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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import javax.validation.constraints.Min;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.files.ProductProfileRegistry;
import org.candlepin.subscriptions.security.auth.ReportingAccessRequired;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.HostReportMeta;
import org.candlepin.subscriptions.utilization.api.model.ProductId;
import org.candlepin.subscriptions.utilization.api.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.model.SkuCapacity;
import org.candlepin.subscriptions.utilization.api.model.SkuCapacityReport;
import org.candlepin.subscriptions.utilization.api.model.SkuCapacityReportSort;
import org.candlepin.subscriptions.utilization.api.model.SkuCapacitySubscription;
import org.candlepin.subscriptions.utilization.api.model.SortDirection;
import org.candlepin.subscriptions.utilization.api.model.Uom;
import org.candlepin.subscriptions.utilization.api.model.UsageType;
import org.candlepin.subscriptions.utilization.api.resources.SubscriptionsApi;
import org.springframework.stereotype.Component;

/** Subscriptions Table API implementation. */
@Component
public class SubscriptionsResource implements SubscriptionsApi {
  private final SubscriptionCapacityRepository subCapRepo;
  //  private final OfferingRepository offeringRepo;
  //  private final SubscriptionRepository subRepo;
  private final ApplicationClock clock;
  private final ProductProfileRegistry productProfileRegistry;

  public SubscriptionsResource(
      SubscriptionCapacityRepository subCapRepo,
      //      OfferingRepository offeringRepo,
      ApplicationClock clock,
      ProductProfileRegistry productProfileRegistry) {
    this.subCapRepo = subCapRepo;
    //    this.offeringRepo = offeringRepo;
    this.clock = clock;
    this.productProfileRegistry = productProfileRegistry;
  }

  @ReportingAccessRequired
  @Override
  public SkuCapacityReport getSkuCapacityReport(
      ProductId productId,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      @Min(0) Integer offset,
      @Min(1) Integer limit,
      ServiceLevelType sla,
      UsageType usage,
      Uom uom,
      SkuCapacityReportSort sort,
      SortDirection dir) {
    OffsetDateTime now = clock.now();
    OffsetDateTime reportStart = Optional.ofNullable(beginning).orElse(now);
    OffsetDateTime reportEnd = Optional.ofNullable(ending).orElse(now);

    String ownerId = ResourceUtils.getOwnerId();

    // Map of SKUs to inventories.
    Map<String, SkuCapacity> inventories = new TreeMap<>();

    // Grab all active and future subs.
    // Future subs are needed to calculate the nearest events like "Subscription Begin".
    ServiceLevel sanitizedSla = ResourceUtils.sanitizeServiceLevel(sla);
    Usage sanitizedUsage = ResourceUtils.sanitizeUsage(usage);

    List<SubscriptionCapacityView> capacities =
        subCapRepo.findByKeyOwnerIdAndKeyProductId(
            ownerId, productId.toString(), sanitizedSla, sanitizedUsage, reportStart, reportEnd);

    for (SubscriptionCapacityView cap : capacities) {
      String sku = cap.getSku();
      final SkuCapacity inventory =
          inventories.computeIfAbsent(
              sku,
              key -> {
                // If no inventory is associated with the SKU key, then initialize a new inventory
                // with offering-specific data and default values. No information specific to an
                // engineering product within an offering is added.
                var inv = new SkuCapacity();

                String productName =
                    Optional.ofNullable(cap.getOffering())
                        .map(Offering::getProductName)
                        .orElse("[Unnamed]");
                inv.setProductName(productName);

                var invSla =
                    Optional.ofNullable(cap.getServiceLevel())
                        .orElse(ServiceLevel.EMPTY)
                        .asOpenApiEnum();
                inv.setServiceLevel(invSla);

                var invUsage =
                    Optional.ofNullable(cap.getUsage()).orElse(Usage.EMPTY).asOpenApiEnum();
                inv.setUsage(invUsage);

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
              });

      // If the sub starts in the future (after the report end) see if it's the nearest event.
      OffsetDateTime nearestEventDate = inventory.getUpcomingEventDate();
      OffsetDateTime subBegin = cap.getBeginDate();
      if (subBegin != null
          && reportEnd.isBefore(subBegin)
          && (nearestEventDate == null || subBegin.isBefore(nearestEventDate))) {
        nearestEventDate = subBegin;
        inventory.setUpcomingEventDate(nearestEventDate);
        inventory.setUpcomingEventType("Subscription Begin");
      }

      // If the sub ends in the future (after the report end), see if it's the nearest event.
      OffsetDateTime subEnd = cap.getEndDate();
      if (subEnd != null
          && reportEnd.isBefore(subEnd)
          && (nearestEventDate == null || subEnd.isBefore(nearestEventDate))) {
        nearestEventDate = subEnd;
        inventory.setUpcomingEventDate(nearestEventDate);
        inventory.setUpcomingEventType("Subscription End");
      }

      // If the sub is active (start date is before report end and end date is after report start)
      // then add the sub to the list and add the capacities and quantity with the inventory.
      if (isActive(cap, reportStart, reportEnd)) {
        Optional.ofNullable(cap.getSubscription()).stream()
            .forEach(
                capSub -> {
                  var invSub = new SkuCapacitySubscription();
                  invSub.setId(capSub.getSubscriptionId());
                  invSub.setNumber(capSub.getSubscriptionNumber());
                  inventory.getSubscriptions().add(invSub);

                  inventory.setQuantity(inventory.getQuantity() + (int) capSub.getQuantity());
                });

        var physicalSockets = cap.getPhysicalSockets();
        var physicalCores = cap.getPhysicalCores();
        if (physicalSockets != null && physicalSockets != 0) {
          inventory.setPhysicalCapacity(physicalSockets);
          if (inventory.getUom() == null) {
            inventory.setUom(Uom.SOCKETS);
          }
        } else if (physicalCores != null && physicalCores != 0) {
          inventory.setPhysicalCapacity(physicalCores);
          if (inventory.getUom() == null) {
            inventory.setUom(Uom.CORES);
          }
        }

        Integer virtualSockets = cap.getVirtualSockets();
        Integer virtualCores = cap.getVirtualCores();
        if (virtualSockets != null && virtualSockets != 0) {
          inventory.setVirtualCapacity(virtualSockets);
          if (inventory.getUom() == null) {
            inventory.setUom(Uom.SOCKETS);
          }
        } else if (virtualCores != null && virtualCores != 0) {
          inventory.setVirtualCapacity(virtualCores);
          if (inventory.getUom() == null) {
            inventory.setUom(Uom.CORES);
          }
        }

        inventory.setTotalCapacity(
            inventory.getPhysicalCapacity() + inventory.getVirtualCapacity());
      }
    }

    List<SkuCapacity> reportItems = new ArrayList<>(inventories.values());
    sortCapacities(reportItems, sort, dir);
    SkuCapacityReport report = new SkuCapacityReport();
    reportItems = getPage(reportItems, offset, limit);
    report.data(reportItems);

    var meta =
        new HostReportMeta()
            .count(report.getData().size())
            .serviceLevel(sla)
            .usage(usage)
            .uom(uom)
            .product(productId);
    report.setMeta(meta);

    return report;
  }

  private static boolean isActive(
      SubscriptionCapacity cap, OffsetDateTime start, OffsetDateTime end) {
    OffsetDateTime subBegin = cap.getBeginDate();
    OffsetDateTime subEnd = cap.getEndDate();

    // Is this right?!?!
    return (subBegin == null || subBegin.isBefore(end) || subBegin.isEqual(end))
        && (subEnd == null || subEnd.isAfter(start) || subEnd.isEqual(start));
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
            case SLA:
              diff = left.getServiceLevel().compareTo(right.getServiceLevel());
              break;
            case USAGE:
              diff = left.getUsage().compareTo(right.getUsage());
              break;
            case QUANTITY:
              diff = left.getQuantity().compareTo(right.getQuantity());
              break;
            case NEXT_EVENT:
              diff = left.getUpcomingEventDate().compareTo(right.getUpcomingEventDate());
              break;
            case NEXT_EVENT_TYPE:
              diff = left.getUpcomingEventType().compareTo(right.getUpcomingEventType());
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

  private static List<SkuCapacity> getPage(List<SkuCapacity> items, Integer offset, Integer limit) {
    // Default to starting from the first item on the list
    int fromIndex = 0;
    if (offset != null) {
      fromIndex = offset;
    }

    // Don't return all objects if the limit results in less items returned, default to all
    int toIndex = items.size();
    if (limit != null && offset + limit < toIndex) {
      toIndex = offset + limit;
    }

    // If the page is beyond the size of the list then return an empty list, otherwise
    // return the sublist.
    if (toIndex <= fromIndex) {
      return Collections.emptyList();
    }
    return items.subList(fromIndex, toIndex);
  }
}
