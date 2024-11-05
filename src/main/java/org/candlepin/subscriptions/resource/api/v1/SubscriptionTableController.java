/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.subscriptions.resource.api.v1;

import static org.candlepin.subscriptions.resource.ResourceUtils.*;
import static org.candlepin.subscriptions.resource.api.v1.CapacityResource.HYPERVISOR;
import static org.candlepin.subscriptions.resource.api.v1.CapacityResource.PHYSICAL;

import com.redhat.swatch.configuration.registry.ProductId;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.HypervisorReportCategory;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurementKey;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.util.ApiModelMapperV1;
import org.candlepin.subscriptions.util.InMemoryPager;
import org.candlepin.subscriptions.utilization.api.v1.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SubscriptionTableController {

  private static final String NO_METRIC_ID = null;
  private final ApiModelMapperV1 mapper;
  private final SubscriptionRepository subscriptionRepository;
  private final ApplicationClock clock;

  @Autowired
  SubscriptionTableController(
      ApiModelMapperV1 mapper,
      SubscriptionRepository subscriptionRepository,
      ApplicationClock clock) {
    this.mapper = mapper;
    this.subscriptionRepository = subscriptionRepository;
    this.clock = clock;
  }

  // Transactional annotation necessary to access lazy loaded ElementCollections for Subscription
  @Transactional
  public SkuCapacityReport capacityReportBySku( // NOSONAR
      ProductId productId,
      @Min(0) Integer offset,
      @Min(1) Integer limit,
      ReportCategory category,
      ServiceLevelType serviceLevel,
      UsageType usage,
      BillingProviderType billingProviderType,
      String billingAccountId,
      String metricId,
      SkuCapacityReportSort sort,
      SortDirection dir) {
    /*
    Notes:
    - Ignoring beginning and ending query params, as the table should (currently, anyway) only
      report on the latest subscription status information.
    - The implementation will report "subscription end" events for Next Event ONLY. That is,
      future-dated subs are not taken into account when reporting the Next Event; only currently
      active subs are used for calculating Next Event.
    */

    OffsetDateTime reportEnd = clock.now();
    OffsetDateTime reportStart = clock.now();
    ServiceLevel sanitizedServiceLevel = sanitizeServiceLevel(mapper.map(serviceLevel));
    Usage sanitizedUsage = sanitizeUsage(mapper.map(usage));
    BillingProvider sanitizedBillingProvider =
        sanitizeBillingProvider(mapper.map(billingProviderType));
    String sanitizedBillingAccountId = sanitizeBillingAccountId(billingAccountId);
    HypervisorReportCategory hypervisorReportCategory =
        HypervisorReportCategory.mapCategory(mapper.map(category));

    var orgId = getOrgId();
    log.info(
        "Finding all subscription capacities for "
            + "orgId={}, "
            + "productId={}, "
            + "categories={}, "
            + "Service Level={}, "
            + "Usage={}, "
            + "between={} and {}, "
            + "MetricId={}, "
            + "Billing Provider={}",
        orgId,
        productId,
        hypervisorReportCategory,
        sanitizedServiceLevel,
        sanitizedUsage,
        reportStart,
        reportEnd,
        metricId,
        sanitizedBillingProvider);

    var reportCriteria =
        DbReportCriteria.builder()
            .orgId(orgId)
            .productTag(productId.toString())
            .serviceLevel(sanitizedServiceLevel)
            .usage(sanitizedUsage)
            .beginning(reportStart)
            .ending(reportEnd)
            .metricId(metricId)
            .hypervisorReportCategory(hypervisorReportCategory)
            .billingProvider(sanitizedBillingProvider)
            .build();
    var subscriptionSpec = SubscriptionRepository.buildSearchSpecification(reportCriteria);
    var subscriptions = subscriptionRepository.findAll(subscriptionSpec);
    List<Subscription> unlimitedSubs = subscriptionRepository.findUnlimited(reportCriteria);
    Map<String, SkuCapacity> reportItemsBySku = new HashMap<>();

    for (Subscription subscription : subscriptions) {
      SkuCapacity inventory =
          reportItemsBySku.computeIfAbsent(
              subscription.getOffering().getSku(),
              s -> initializeSkuCapacity(subscription, metricId));
      calculateNextEvent(subscription, inventory, reportEnd);
      addSubscriptionInformation(subscription, inventory);
      var measurements = subscription.getSubscriptionMeasurements().entrySet().stream();
      if (metricId != null) {
        measurements =
            measurements.filter(entry -> Objects.equals(entry.getKey().getMetricId(), metricId));
      }
      if (hypervisorReportCategory == HypervisorReportCategory.HYPERVISOR) {
        measurements =
            measurements.filter(
                entry ->
                    ReportCategory.HYPERVISOR
                        .toString()
                        .equalsIgnoreCase(entry.getKey().getMeasurementType()));
      } else if (hypervisorReportCategory == HypervisorReportCategory.NON_HYPERVISOR) {
        measurements =
            measurements.filter(
                entry ->
                    !ReportCategory.HYPERVISOR
                        .toString()
                        .equalsIgnoreCase(entry.getKey().getMeasurementType()));
      }
      measurements.forEach(
          entry -> {
            var measurementKey = entry.getKey();
            var value = entry.getValue();
            addTotalCapacity(subscription, measurementKey, value, inventory);
          });
    }

    for (Subscription subscription : unlimitedSubs) {
      if (subscription.getOffering() != null) {
        SkuCapacity inventory =
            reportItemsBySku.computeIfAbsent(
                subscription.getOffering().getSku(),
                s -> initializeSkuCapacity(subscription, metricId));

        inventory.setHasInfiniteQuantity(true);
        calculateNextEvent(subscription, inventory, reportCriteria.getEnding());
        addSubscriptionInformation(subscription, inventory);
      }
    }

    SubscriptionType subscriptionType =
        productId.isOnDemand() ? SubscriptionType.ON_DEMAND : SubscriptionType.ANNUAL;

    List<SkuCapacity> reportItems = new ArrayList<>(reportItemsBySku.values());
    if (productId.isOnDemand() && reportItems.isEmpty()) {
      reportItems.addAll(
          getOnDemandSkuCapacities(
              productId,
              sanitizedServiceLevel,
              sanitizedUsage,
              sanitizedBillingProvider,
              sanitizedBillingAccountId,
              reportStart,
              reportEnd));
    }

    int reportItemCount = reportItems.size();
    // The pagination and sorting of capacities is done in memory and can cause performance
    // issues
    // As an improvement this should be pushed lower into the Repository layer
    sortCapacities(reportItems, sort, dir);
    Pageable pageable = ResourceUtils.getPageable(offset, limit);
    var page = InMemoryPager.paginate(reportItems, pageable);

    return new SkuCapacityReport()
        .data(page.getContent())
        .meta(
            new SkuCapacityReportMeta()
                .subscriptionType(subscriptionType)
                .count(reportItemCount)
                .serviceLevel(serviceLevel)
                .usage(usage)
                .reportCategory(category)
                .product(productId.toString()));
  }

  @SuppressWarnings("java:S107")
  private Collection<SkuCapacity> getOnDemandSkuCapacities(
      ProductId productId,
      ServiceLevel serviceLevel,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      OffsetDateTime reportStart,
      OffsetDateTime reportEnd) {

    Map<String, SkuCapacity> inventories = new HashMap<>();

    var subscriptions =
        subscriptionRepository.findByCriteria(
            DbReportCriteria.builder()
                .orgId(getOrgId())
                .productTag(productId.getValue())
                .serviceLevel(serviceLevel)
                .usage(usage)
                .billingProvider(billingProvider)
                .billingAccountId(billingAccountId)
                .beginning(reportStart)
                .ending(reportEnd)
                .payg(true)
                .build(),
            Sort.unsorted());

    subscriptions.forEach(
        sub -> {
          var sku = sub.getOffering().getSku();
          final SkuCapacity inventory =
              inventories.computeIfAbsent(
                  String.format("%s:%s", sku, sub.getBillingProvider()),
                  key -> initializeSkuCapacity(sub, NO_METRIC_ID));
          addOnDemandSubscriptionInformation(sub, inventory);
        });
    return inventories.values();
  }

  public SkuCapacity initializeSkuCapacity(@Nonnull Subscription sub, @Nullable String metricId) {
    var offering = sub.getOffering();
    var inventory = new SkuCapacity();
    inventory.setSubscriptions(new ArrayList<>());
    inventory.setSku(offering.getSku());
    inventory.setProductName(offering.getDescription());
    inventory.setServiceLevel(
        mapper.map(Optional.ofNullable(offering.getServiceLevel()).orElse(ServiceLevel.EMPTY)));
    inventory.setUsage(mapper.map(Optional.ofNullable(offering.getUsage()).orElse(Usage.EMPTY)));
    inventory.setHasInfiniteQuantity(offering.isHasUnlimitedUsage());
    inventory.setBillingProvider(
        mapper.map(Optional.ofNullable(sub.getBillingProvider()).orElse(BillingProvider.EMPTY)));
    inventory.setQuantity(0);
    inventory.setCapacity(0);
    inventory.setHypervisorCapacity(0);
    inventory.setTotalCapacity(0);
    inventory.setMetricId(metricId);
    return inventory;
  }

  public void calculateNextEvent(
      Subscription subscription, SkuCapacity skuCapacity, OffsetDateTime now) {

    OffsetDateTime nearestEventDate = skuCapacity.getNextEventDate();
    OffsetDateTime subEnd = subscription.getEndDate();
    if (subEnd != null
        && now.isBefore(subEnd)
        && (nearestEventDate == null || subEnd.isBefore(nearestEventDate))) {
      nearestEventDate = subEnd;
      skuCapacity.setNextEventDate(nearestEventDate);
      skuCapacity.setNextEventType(SubscriptionEventType.END);
    }
  }

  public void addOnDemandSubscriptionInformation(
      Subscription subscription, SkuCapacity skuCapacity) {
    var invSub = new SkuCapacitySubscription();
    invSub.setId(subscription.getSubscriptionId());
    Optional.ofNullable(subscription.getSubscriptionNumber()).ifPresent(invSub::setNumber);
    skuCapacity.addSubscriptionsItem(invSub);
    skuCapacity.setQuantity(skuCapacity.getQuantity() + (int) subscription.getQuantity());
    OffsetDateTime subEnd = subscription.getEndDate();
    OffsetDateTime nearestEventDate = skuCapacity.getNextEventDate();
    if (subEnd != null && (nearestEventDate == null || subEnd.isBefore(nearestEventDate))) {
      nearestEventDate = subEnd;
      skuCapacity.setNextEventDate(nearestEventDate);
      skuCapacity.setNextEventType(SubscriptionEventType.END);
    }
  }

  public void addSubscriptionInformation(Subscription subscription, SkuCapacity skuCapacity) {
    var invSub = new SkuCapacitySubscription();
    invSub.setId(subscription.getSubscriptionId());
    Optional.ofNullable(subscription.getSubscriptionNumber()).ifPresent(invSub::setNumber);
    // Different measurements can have the same subscription.  I'm not crazy about this
    // implementation but refining it is for another day.

    if (!skuCapacity.getSubscriptions().contains(invSub)) {
      skuCapacity.addSubscriptionsItem(invSub);
      skuCapacity.setQuantity(skuCapacity.getQuantity() + (int) subscription.getQuantity());
    }
  }

  @SuppressWarnings("java:S3776")
  public static void addTotalCapacity(
      Subscription subscription,
      SubscriptionMeasurementKey key,
      Double value,
      SkuCapacity skuCapacity) {
    log.debug(
        "Calculating total capacity using sku capacity {} and subscription capacity view {}, value: {}",
        skuCapacity,
        key,
        value);

    var metric = key.getMetricId();
    var type = key.getMeasurementType();
    // we only initialize the metric for measurements with a value higher than zero.
    if (value > 0 && skuCapacity.getMetricId() == null) {
      // initialize metric ID using the current metric if it's not set
      skuCapacity.setMetricId(metric);
    }

    // accumulate the value
    if (metric.equals(skuCapacity.getMetricId())) {
      if (PHYSICAL.equals(type)) {
        skuCapacity.setCapacity(skuCapacity.getCapacity() + value.intValue());
      } else if (HYPERVISOR.equals(type)) {
        skuCapacity.setHypervisorCapacity(skuCapacity.getHypervisorCapacity() + value.intValue());
      }
    }

    if (subscription.getOffering().isHasUnlimitedUsage()) {
      log.warn(
          "Subscription for SKU {} has both capacity and unlimited quantity", skuCapacity.getSku());
    }

    skuCapacity.setTotalCapacity(skuCapacity.getCapacity() + skuCapacity.getHypervisorCapacity());
    skuCapacity.setHasInfiniteQuantity(subscription.getOffering().isHasUnlimitedUsage());
  }

  private static void sortCapacities(
      List<SkuCapacity> items, SkuCapacityReportSort sort, SortDirection dir) {
    items.sort(
        (left, right) -> {
          var sortField = Optional.ofNullable(sort).orElse(SkuCapacityReportSort.SKU);
          int sortDir = 1;
          if (dir == SortDirection.DESC) {
            sortDir = -1;
          }
          int diff =
              switch (sortField) {
                case SKU -> left.getSku().compareTo(right.getSku());
                case SERVICE_LEVEL -> left.getServiceLevel().compareTo(right.getServiceLevel());
                case USAGE -> left.getUsage().compareTo(right.getUsage());
                case QUANTITY -> left.getQuantity().compareTo(right.getQuantity());
                case NEXT_EVENT_DATE -> left.getNextEventDate().compareTo(right.getNextEventDate());
                case NEXT_EVENT_TYPE -> left.getNextEventType().compareTo(right.getNextEventType());
                case TOTAL_CAPACITY -> compareTotalCapacity(left, right);
                case PRODUCT_NAME -> left.getProductName().compareTo(right.getProductName());
              };
          // If the two items are sorted by some other field than SKU and are equal, then break the
          // tie by sorting by SKU. No two SKUs in the list are equal.
          if (diff == 0 && sortField != SkuCapacityReportSort.SKU) {
            diff = left.getSku().compareTo(right.getSku());
          }

          return diff * sortDir;
        });
  }

  private static int compareTotalCapacity(SkuCapacity left, SkuCapacity right) {
    // unlimited capacity subscriptions are greater than non-unlimited
    if (!Objects.equals(left.getHasInfiniteQuantity(), right.getHasInfiniteQuantity())) {
      if (Boolean.TRUE.equals(left.getHasInfiniteQuantity())) {
        return 1;
      }
      if (Boolean.TRUE.equals(right.getHasInfiniteQuantity())) {
        return -1;
      }
    }
    return left.getTotalCapacity().compareTo(right.getTotalCapacity());
  }
}
