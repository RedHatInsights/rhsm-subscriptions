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
package com.redhat.swatch.contract.resource.api.v1;

import static com.redhat.swatch.contract.resource.ResourceUtils.sanitizeBillingAccountId;
import static com.redhat.swatch.contract.resource.ResourceUtils.sanitizeBillingProvider;
import static com.redhat.swatch.contract.resource.ResourceUtils.sanitizeServiceLevel;
import static com.redhat.swatch.contract.resource.ResourceUtils.sanitizeUsage;

import com.redhat.swatch.common.model.HardwareMeasurementType;
import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.contract.openapi.model.BillingProviderType;
import com.redhat.swatch.contract.openapi.model.ReportCategory;
import com.redhat.swatch.contract.openapi.model.ServiceLevelType;
import com.redhat.swatch.contract.openapi.model.SkuCapacityReportSortV1;
import com.redhat.swatch.contract.openapi.model.SkuCapacityReportV1;
import com.redhat.swatch.contract.openapi.model.SkuCapacityReportV1Meta;
import com.redhat.swatch.contract.openapi.model.SkuCapacitySubscription;
import com.redhat.swatch.contract.openapi.model.SkuCapacityV1;
import com.redhat.swatch.contract.openapi.model.SortDirection;
import com.redhat.swatch.contract.openapi.model.SubscriptionEventType;
import com.redhat.swatch.contract.openapi.model.SubscriptionType;
import com.redhat.swatch.contract.openapi.model.UsageType;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.DbReportCriteria;
import com.redhat.swatch.contract.repository.HypervisorReportCategory;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionMeasurementKey;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.resource.InMemoryPager;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
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

@ApplicationScoped
@Slf4j
public class SubscriptionTableControllerV1 {

  private static final String NO_METRIC_ID = null;
  private final ApiModelMapperV1 mapper;
  private final SubscriptionRepository subscriptionRepository;
  private final ApplicationClock clock;

  public static final String PHYSICAL = HardwareMeasurementType.PHYSICAL.toString().toUpperCase();
  public static final String HYPERVISOR =
      HardwareMeasurementType.HYPERVISOR.toString().toUpperCase();

  @Context SecurityContext securityContext;

  @Inject
  SubscriptionTableControllerV1(
      ApiModelMapperV1 mapper,
      SubscriptionRepository subscriptionRepository,
      ApplicationClock clock) {
    this.mapper = mapper;
    this.subscriptionRepository = subscriptionRepository;
    this.clock = clock;
  }

  // Transactional annotation necessary to access lazy loaded ElementCollections for Subscription
  @SuppressWarnings("java:S107")
  @Transactional
  public SkuCapacityReportV1 capacityReportBySkuV1(
      ProductId productId,
      @Min(0) Integer offset,
      @Min(1) Integer limit,
      ReportCategory category,
      ServiceLevelType serviceLevel,
      UsageType usage,
      BillingProviderType billingProviderType,
      String billingAccountId,
      OffsetDateTime begining,
      OffsetDateTime ending,
      String metricId,
      SkuCapacityReportSortV1 sort,
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
    var subscriptions = subscriptionRepository.findByCriteria(reportCriteria);
    List<SubscriptionEntity> unlimitedSubs = subscriptionRepository.findUnlimited(reportCriteria);
    Map<String, SkuCapacityV1> reportItemsBySku = new HashMap<>();

    for (SubscriptionEntity subscription : subscriptions) {
      SkuCapacityV1 inventory =
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

    for (SubscriptionEntity subscription : unlimitedSubs) {
      if (subscription.getOffering() != null) {
        SkuCapacityV1 inventory =
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

    List<SkuCapacityV1> reportItems = new ArrayList<>(reportItemsBySku.values());
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
    var page = InMemoryPager.paginate(reportItems, offset, limit);

    return new SkuCapacityReportV1()
        .data(page)
        .meta(
            new SkuCapacityReportV1Meta()
                .subscriptionType(subscriptionType)
                .count(reportItemCount)
                .serviceLevel(serviceLevel)
                .usage(usage)
                .reportCategory(category)
                .product(productId.toString()));
  }

  @SuppressWarnings("java:S107")
  private Collection<SkuCapacityV1> getOnDemandSkuCapacities(
      ProductId productId,
      ServiceLevel serviceLevel,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      OffsetDateTime reportStart,
      OffsetDateTime reportEnd) {

    Map<String, SkuCapacityV1> inventories = new HashMap<>();

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
            Sort.empty());

    subscriptions.forEach(
        sub -> {
          var sku = sub.getOffering().getSku();
          final SkuCapacityV1 inventory =
              inventories.computeIfAbsent(
                  String.format("%s:%s", sku, sub.getBillingProvider()),
                  key -> initializeSkuCapacity(sub, NO_METRIC_ID));
          addOnDemandSubscriptionInformation(sub, inventory);
        });
    return inventories.values();
  }

  public SkuCapacityV1 initializeSkuCapacity(
      @Nonnull SubscriptionEntity sub, @Nullable String metricId) {
    var offering = sub.getOffering();
    var inventory = new SkuCapacityV1();
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
      SubscriptionEntity subscription, SkuCapacityV1 skuCapacity, OffsetDateTime now) {

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
      SubscriptionEntity subscription, SkuCapacityV1 skuCapacity) {
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

  public void addSubscriptionInformation(
      SubscriptionEntity subscription, SkuCapacityV1 skuCapacity) {
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
      SubscriptionEntity subscription,
      SubscriptionMeasurementKey key,
      Double value,
      SkuCapacityV1 skuCapacity) {
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
      List<SkuCapacityV1> items, SkuCapacityReportSortV1 sort, SortDirection dir) {
    items.sort(
        (left, right) -> {
          var sortField = Optional.ofNullable(sort).orElse(SkuCapacityReportSortV1.SKU);
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
          if (diff == 0 && sortField != SkuCapacityReportSortV1.SKU) {
            diff = left.getSku().compareTo(right.getSku());
          }

          return diff * sortDir;
        });
  }

  private static int compareTotalCapacity(SkuCapacityV1 left, SkuCapacityV1 right) {
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

  private String getOrgId() {
    return securityContext.getUserPrincipal().getName();
  }

  // for unit testing since we have no REST request
  protected void setTestSecurityContext(SecurityContext securityContext) {
    this.securityContext = securityContext;
  }
}
