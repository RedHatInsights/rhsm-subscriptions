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
package org.candlepin.subscriptions.resource;

import static org.candlepin.subscriptions.resource.ResourceUtils.*;

import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.HypervisorReportCategory;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionMeasurementRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurement;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SubscriptionTableController {
  private final SubscriptionRepository subscriptionRepository;
  private final OfferingRepository offeringRepository;
  private final ApplicationClock clock;
  private final TagProfile tagProfile;
  private final SubscriptionMeasurementRepository measurementRepository;

  @Autowired
  SubscriptionTableController(
      SubscriptionMeasurementRepository measurementRepository,
      SubscriptionRepository subscriptionRepository,
      OfferingRepository offeringRepository,
      TagProfile tagProfile,
      ApplicationClock clock) {
    this.measurementRepository = measurementRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.offeringRepository = offeringRepository;
    this.tagProfile = tagProfile;
    this.clock = clock;
  }

  public SkuCapacityReport capacityReportBySku( // NOSONAR
      ProductId productId,
      @Min(0) Integer offset,
      @Min(1) Integer limit,
      ReportCategory category,
      ServiceLevelType serviceLevel,
      UsageType usage,
      BillingProviderType billingProviderType,
      String billingAccountId,
      Uom uom,
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
    ServiceLevel sanitizedServiceLevel = sanitizeServiceLevel(serviceLevel);
    Usage sanitizedUsage = sanitizeUsage(usage);
    BillingProvider sanitizedBillingProvider = sanitizeBillingProvider(billingProviderType);
    String sanitiziedBillingAccountId = sanitizeBillingAccountId(billingAccountId);
    HypervisorReportCategory hypervisorReportCategory =
        HypervisorReportCategory.mapCategory(category);

    var orgId = getOrgId();
    log.info(
        "Finding all subscription capacities for "
            + "orgId={}, "
            + "productId={}, "
            + "categories={}, "
            + "Service Level={}, "
            + "Usage={} "
            + "between={} and {}"
            + "and uom={}",
        orgId,
        productId,
        hypervisorReportCategory,
        sanitizedServiceLevel,
        sanitizedUsage,
        reportStart,
        reportEnd,
        uom);
    var metricId =
        (uom != null)
            ? switch (uom) {
              case CORES -> MetricId.CORES;
              case SOCKETS -> MetricId.SOCKETS;
            }
            : null;

    List<SubscriptionMeasurement> measurements =
        measurementRepository.findAllBy(
            orgId,
            productId.toString(),
            metricId,
            hypervisorReportCategory,
            sanitizedServiceLevel,
            sanitizedUsage,
            reportStart,
            reportEnd);

    var reportCriteria =
        DbReportCriteria.builder()
            .orgId(orgId)
            .productId(productId.toString())
            .serviceLevel(sanitizedServiceLevel)
            .usage(sanitizedUsage)
            .beginning(reportStart)
            .ending(reportEnd)
            .build();
    List<Subscription> unlimitedSubs = subscriptionRepository.findUnlimited(reportCriteria);

    var skus =
        measurements.stream()
            .map(x -> x.getSubscription().getOffering().getSku())
            .collect(Collectors.toSet());
    skus.addAll(
        unlimitedSubs.stream().map(x -> x.getOffering().getSku()).collect(Collectors.toSet()));

    Map<String, SkuCapacity> inventories = initializeDefaultSkuCapacities(skus, uom);

    for (SubscriptionMeasurement measurement : measurements) {
      Subscription subscription = measurement.getSubscription();
      SkuCapacity inventory = inventories.get(subscription.getOffering().getSku());
      calculateNextEvent(subscription, inventory, reportEnd);
      addSubscriptionInformation(subscription, inventory);
      addTotalCapacity(measurement, inventory);
    }

    for (Subscription subscription : unlimitedSubs) {
      SkuCapacity inventory = inventories.get(subscription.getOffering().getSku());
      inventory.setHasInfiniteQuantity(true);
      calculateNextEvent(subscription, inventory, reportCriteria.getEnding());
      addSubscriptionInformation(subscription, inventory);
    }

    List<SkuCapacity> reportItems = new ArrayList<>(inventories.values());

    boolean isOnDemand = tagProfile.tagIsPrometheusEnabled(productId.toString());
    SubscriptionType subscriptionType =
        isOnDemand ? SubscriptionType.ON_DEMAND : SubscriptionType.ANNUAL;

    if (isOnDemand && reportItems.isEmpty()) {
      reportItems.addAll(
          getOnDemandSkuCapacities(
              productId,
              sanitizedServiceLevel,
              sanitizedUsage,
              sanitizedBillingProvider,
              sanitiziedBillingAccountId,
              reportStart,
              reportEnd));
    }

    int reportItemCount = reportItems.size();
    // The pagination and sorting of capacities is done in memory and can cause performance
    // issues
    // As an improvement this should be pushed lower into the Repository layer
    Pageable pageable = ResourceUtils.getPageable(offset, limit);
    reportItems = paginate(reportItems, pageable);
    sortCapacities(reportItems, sort, dir);

    return new SkuCapacityReport()
        .data(reportItems)
        .meta(
            new SkuCapacityReportMeta()
                .subscriptionType(subscriptionType)
                .count(reportItemCount)
                .serviceLevel(serviceLevel)
                .usage(usage)
                .uom(uom)
                .reportCategory(category)
                .product(productId));
  }

  private Map<String, SkuCapacity> initializeDefaultSkuCapacities(Set<String> skus, Uom uom) {
    Map<String, SkuCapacity> capacityTemplates = new HashMap<>();
    for (Offering offering : offeringRepository.findBySkuIn(skus)) {
      // No information specific to an engineering product within an offering is added.
      var inventory = new SkuCapacity();
      inventory.setSku(offering.getSku());
      inventory.setProductName(offering.getDescription());
      inventory.setServiceLevel(
          Optional.ofNullable(offering.getServiceLevel())
              .orElse(ServiceLevel.EMPTY)
              .asOpenApiEnum());
      inventory.setUsage(
          Optional.ofNullable(offering.getUsage()).orElse(Usage.EMPTY).asOpenApiEnum());

      // When uom param is set, force all inventories to report capacities for that UoM
      // (Some products have both sockets and cores)
      if (uom != null) {
        inventory.setUom(uom);
      }
      inventory.setQuantity(0);
      inventory.setCapacity(0);
      inventory.setHypervisorCapacity(0);
      inventory.setTotalCapacity(0);
      inventory.setSubscriptions(new ArrayList<>());
      capacityTemplates.put(offering.getSku(), inventory);
    }
    return capacityTemplates;
  }

  private List<SkuCapacity> paginate(List<SkuCapacity> capacities, Pageable pageable) {
    if (pageable == null) {
      return capacities;
    }
    int offset = pageable.getPageNumber() * pageable.getPageSize();
    int lastIndex = Math.min(capacities.size(), offset + pageable.getPageSize());
    return capacities.subList(offset, lastIndex);
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
    var productNames = tagProfile.getOfferingProductNamesForTag(productId.toString());
    Map<String, SkuCapacity> inventories = new HashMap<>();

    var subscriptions =
        subscriptionRepository.findByCriteria(
            DbReportCriteria.builder()
                .orgId(getOrgId())
                .productNames(productNames)
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
                  key -> initializeOnDemandSkuCapacity(sub));
          addOnDemandSubscriptionInformation(sub, inventory);
        });
    return inventories.values();
  }

  public SkuCapacity initializeOnDemandSkuCapacity(Subscription subscription) {
    var offering = subscription.getOffering();
    var inv = new SkuCapacity();
    inv.setSubscriptions(new ArrayList<>());
    inv.setSku(offering.getSku());
    inv.setProductName(offering.getProductName());
    inv.setServiceLevel(
        Optional.ofNullable(offering.getServiceLevel()).orElse(ServiceLevel.EMPTY).asOpenApiEnum());
    inv.setUsage(Optional.ofNullable(offering.getUsage()).orElse(Usage.EMPTY).asOpenApiEnum());
    inv.setHasInfiniteQuantity(offering.getHasUnlimitedUsage());
    inv.setBillingProvider(
        Optional.ofNullable(subscription.getBillingProvider())
            .orElse(BillingProvider.EMPTY)
            .asOpenApiEnum());
    inv.setQuantity(0);
    return inv;
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
  public void addTotalCapacity(SubscriptionMeasurement measurement, SkuCapacity skuCapacity) {
    log.debug(
        "Calculating total capacity using sku capacity {} and subscription capacity view {}",
        skuCapacity,
        measurement);

    var metric = measurement.getMetricId();
    var type = measurement.getMeasurementType();
    var value = measurement.getValue();

    var sockets =
        (MetricId.SOCKETS.toString().equalsIgnoreCase(metric) && "PHYSICAL".equals(type))
            ? value.intValue()
            : 0;
    var cores =
        (MetricId.CORES.toString().equalsIgnoreCase(metric) && "PHYSICAL".equals(type))
            ? value.intValue()
            : 0;

    var hypervisorSockets =
        (MetricId.SOCKETS.toString().equalsIgnoreCase(metric) && "HYPERVISOR".equals(type))
            ? value.intValue()
            : 0;
    var hypervisorCores =
        (MetricId.CORES.toString().equalsIgnoreCase(metric) && "HYPERVISOR".equals(type))
            ? value.intValue()
            : 0;

    if (skuCapacity.getUom() == Uom.SOCKETS) {
      skuCapacity.setCapacity(skuCapacity.getCapacity() + sockets);
      skuCapacity.setHypervisorCapacity(skuCapacity.getHypervisorCapacity() + hypervisorSockets);
    } else if (skuCapacity.getUom() == Uom.CORES) {
      skuCapacity.setCapacity(skuCapacity.getCapacity() + cores);
      skuCapacity.setHypervisorCapacity(skuCapacity.getHypervisorCapacity() + hypervisorCores);
    } else if (sockets != 0 || hypervisorSockets != 0) {
      skuCapacity.setCapacity(skuCapacity.getCapacity() + sockets);
      skuCapacity.setHypervisorCapacity(skuCapacity.getHypervisorCapacity() + hypervisorSockets);
      if (skuCapacity.getUom() == null) {
        skuCapacity.setUom(Uom.SOCKETS);
      }
    } else if (cores != 0 || hypervisorCores != 0) {
      skuCapacity.setCapacity(skuCapacity.getCapacity() + cores);
      skuCapacity.setHypervisorCapacity(skuCapacity.getHypervisorCapacity() + hypervisorCores);
      if (skuCapacity.getUom() == null) {
        skuCapacity.setUom(Uom.CORES);
      }
    }

    boolean hasInfiniteQuantity =
        Optional.ofNullable(measurement.getSubscription().getOffering().getHasUnlimitedUsage())
            .orElse(false);
    if (hasInfiniteQuantity) {
      log.warn(
          "Subscription for SKU {} has both capacity and unlimited quantity", skuCapacity.getSku());
    }

    skuCapacity.setTotalCapacity(skuCapacity.getCapacity() + skuCapacity.getHypervisorCapacity());
    skuCapacity.setHasInfiniteQuantity(hasInfiniteQuantity);
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
