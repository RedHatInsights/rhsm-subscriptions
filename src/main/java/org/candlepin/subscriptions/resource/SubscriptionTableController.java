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

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import javax.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ReportCriteria;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
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

  private final SubscriptionCapacityViewRepository subscriptionCapacityViewRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final OfferingRepository offeringRepository;
  private final ApplicationClock clock;
  private final TagProfile tagProfile;

  @Autowired
  SubscriptionTableController(
      SubscriptionCapacityViewRepository subscriptionCapacityViewRepository,
      SubscriptionRepository subscriptionRepository,
      OfferingRepository offeringRepository,
      TagProfile tagProfile,
      ApplicationClock clock) {
    this.subscriptionCapacityViewRepository = subscriptionCapacityViewRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.offeringRepository = offeringRepository;
    this.tagProfile = tagProfile;
    this.clock = clock;
  }

  public SkuCapacityReport capacityReportBySku( // NOSONAR
      ProductId productId,
      @Min(0) Integer offset,
      @Min(1) Integer limit,
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

    log.info(
        "Finding all subscription capacities for "
            + "orgId={}, "
            + "productId={}, "
            + "Service Level={}, "
            + "Usage={} "
            + "between={} and {}"
            + "and uom={}",
        getOrgId(),
        productId,
        sanitizedServiceLevel,
        sanitizedUsage,
        reportStart,
        reportEnd,
        uom);
    List<SubscriptionCapacityView> capacities =
        subscriptionCapacityViewRepository.findAllBy(
            getOrgId(),
            productId.toString(),
            sanitizedServiceLevel,
            sanitizedUsage,
            reportStart,
            reportEnd,
            uom);

    Map<String, SkuCapacity> inventories = new HashMap<>();
    for (SubscriptionCapacityView subscriptionCapacityView : capacities) {
      String sku = subscriptionCapacityView.getSku();
      final SkuCapacity inventory =
          inventories.computeIfAbsent(
              sku, key -> initializeDefaultSkuCapacity(subscriptionCapacityView, uom));
      calculateNextEvent(subscriptionCapacityView, inventory, reportEnd);
      addSubscriptionInformation(subscriptionCapacityView, inventory);
      addTotalCapacity(subscriptionCapacityView, inventory);
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
                .product(productId));
  }

  private List<SkuCapacity> paginate(List<SkuCapacity> capacities, Pageable pageable) {
    if (pageable == null) {
      return capacities;
    }
    int offset = pageable.getPageNumber() * pageable.getPageSize();
    int lastIndex = Math.min(capacities.size(), offset + pageable.getPageSize());
    return capacities.subList(offset, lastIndex);
  }

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
    Map<String, Offering> skuOfferings =
        productNames.stream()
            .map(offeringRepository::findByProductName)
            .flatMap(List::stream)
            .collect(Collectors.toMap(Offering::getSku, offering -> offering));

    var subscriptions =
        subscriptionRepository.findByCriteria(
            ReportCriteria.builder()
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
          final SkuCapacity inventory =
              inventories.computeIfAbsent(
                  String.format("%s:%s", sub.getSku(), sub.getBillingProvider()),
                  key -> initializeOnDemandSkuCapacity(sub, skuOfferings.get(sub.getSku())));
          addOnDemandSubscriptionInformation(sub, inventory);
        });
    return inventories.values();
  }

  public SkuCapacity initializeDefaultSkuCapacity(
      SubscriptionCapacityView subscriptionCapacityView, Uom uom) {
    // If no inventory is associated with the SKU key, then initialize a new inventory
    // with offering-specific data and default values. No information specific to an
    // engineering product within an offering is added.
    var inv = new SkuCapacity();
    inv.setSku(subscriptionCapacityView.getSku());
    inv.setProductName(subscriptionCapacityView.getProductName());
    inv.setServiceLevel(
        Optional.ofNullable(subscriptionCapacityView.getServiceLevel())
            .orElse(ServiceLevel.EMPTY)
            .asOpenApiEnum());
    inv.setUsage(
        Optional.ofNullable(subscriptionCapacityView.getUsage())
            .orElse(Usage.EMPTY)
            .asOpenApiEnum());

    // When uom param is set, force all inventories to report capacities for that UoM
    // (Some products have both sockets and cores)
    if (uom != null) {
      inv.setUom(uom);
    }
    inv.setQuantity(0);
    inv.setPhysicalCapacity(0);
    inv.setVirtualCapacity(0);
    inv.setTotalCapacity(0);
    inv.setSubscriptions(new ArrayList<>());
    return inv;
  }

  public SkuCapacity initializeOnDemandSkuCapacity(Subscription subscription, Offering offering) {
    var inv = new SkuCapacity();
    inv.setSku(subscription.getSku());
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
      SubscriptionCapacityView subscriptionCapacityView,
      SkuCapacity skuCapacity,
      OffsetDateTime now) {

    OffsetDateTime nearestEventDate = skuCapacity.getNextEventDate();
    OffsetDateTime subEnd = subscriptionCapacityView.getEndDate();
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
    skuCapacity.getSubscriptions().add(invSub);
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
      SubscriptionCapacityView subscriptionCapacityView, SkuCapacity skuCapacity) {
    var invSub = new SkuCapacitySubscription();
    invSub.setId(subscriptionCapacityView.getKey().getSubscriptionId());
    Optional.ofNullable(subscriptionCapacityView.getSubscriptionNumber())
        .ifPresent(invSub::setNumber);
    skuCapacity.getSubscriptions().add(invSub);
    skuCapacity.setQuantity(
        skuCapacity.getQuantity() + (int) subscriptionCapacityView.getQuantity());
  }

  public void addTotalCapacity(
      SubscriptionCapacityView subscriptionCapacityView, SkuCapacity skuCapacity) {
    log.debug(
        "Calculating total capacity using sku capacity {} and subscription capacity view {}",
        skuCapacity,
        subscriptionCapacityView);

    var physicalSockets = subscriptionCapacityView.getPhysicalSockets();
    var physicalCores = subscriptionCapacityView.getPhysicalCores();
    var virtualSockets = subscriptionCapacityView.getVirtualSockets();
    var virtualCores = subscriptionCapacityView.getVirtualCores();
    if (skuCapacity.getUom() == Uom.SOCKETS) {
      skuCapacity.setPhysicalCapacity(skuCapacity.getPhysicalCapacity() + physicalSockets);
      skuCapacity.setVirtualCapacity(skuCapacity.getVirtualCapacity() + virtualSockets);
    } else if (skuCapacity.getUom() == Uom.CORES) {
      skuCapacity.setPhysicalCapacity(skuCapacity.getPhysicalCapacity() + physicalCores);
      skuCapacity.setVirtualCapacity(skuCapacity.getVirtualCapacity() + virtualCores);
    } else if (physicalSockets != 0 || virtualSockets != 0) {
      skuCapacity.setPhysicalCapacity(skuCapacity.getPhysicalCapacity() + physicalSockets);
      skuCapacity.setVirtualCapacity(skuCapacity.getVirtualCapacity() + virtualSockets);
      if (skuCapacity.getUom() == null) {
        skuCapacity.setUom(Uom.SOCKETS);
      }
    } else if (physicalCores != 0 || virtualCores != 0) {
      skuCapacity.setPhysicalCapacity(skuCapacity.getPhysicalCapacity() + physicalCores);
      skuCapacity.setVirtualCapacity(skuCapacity.getVirtualCapacity() + virtualCores);
      if (skuCapacity.getUom() == null) {
        skuCapacity.setUom(Uom.CORES);
      }
    }

    boolean hasInfiniteQuantity =
        Optional.ofNullable(subscriptionCapacityView.getHasUnlimitedUsage()).orElse(false);

    skuCapacity.setTotalCapacity(
        skuCapacity.getPhysicalCapacity() + skuCapacity.getVirtualCapacity());
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
          int diff = 0;
          switch (sortField) {
            case SKU:
              diff = left.getSku().compareTo(right.getSku());
              break;
            case SERVICE_LEVEL:
              diff = left.getServiceLevel().compareTo(right.getServiceLevel());
              break;
            case USAGE:
              diff = left.getUsage().compareTo(right.getUsage());
              break;
            case QUANTITY:
              diff = left.getQuantity().compareTo(right.getQuantity());
              break;
            case NEXT_EVENT_DATE:
              diff = left.getNextEventDate().compareTo(right.getNextEventDate());
              break;
            case NEXT_EVENT_TYPE:
              diff = left.getNextEventType().compareTo(right.getNextEventType());
              break;
            case TOTAL_CAPACITY:
              diff = compareTotalCapacity(left, right);
              break;
            case PRODUCT_NAME:
              diff = left.getProductName().compareTo(right.getProductName());
              break;
          }
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
