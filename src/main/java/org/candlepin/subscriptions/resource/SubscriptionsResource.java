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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
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
import org.candlepin.subscriptions.utilization.api.model.HypervisorGuestReportMeta;
import org.candlepin.subscriptions.utilization.api.model.SubscriptionInventory;
import org.candlepin.subscriptions.utilization.api.model.SubscriptionTableInventory;
import org.candlepin.subscriptions.utilization.api.model.Uom;
import org.candlepin.subscriptions.utilization.api.resources.SubscriptionsApi;
import org.springframework.stereotype.Component;

/** Subscriptions Table API implementation. */
@Component
public class SubscriptionsResource implements SubscriptionsApi {
  private final SubscriptionCapacityRepository subCapRepo;
  private final OfferingRepository offeringRepo;
  private final ApplicationClock clock;
  private final ProductProfileRegistry productProfileRegistry;

  public SubscriptionsResource(
      SubscriptionCapacityRepository subCapRepo,
      OfferingRepository offeringRepo,
      ApplicationClock clock,
      ProductProfileRegistry productProfileRegistry) {
    this.subCapRepo = subCapRepo;
    this.offeringRepo = offeringRepo;
    this.clock = clock;
    this.productProfileRegistry = productProfileRegistry;
  }

  @ReportingAccessRequired
  @Override
  public SubscriptionTableInventory getSubscriptionsProducts(String productId) {
    OffsetDateTime now = clock.now();
    String ownerId = ResourceUtils.getOwnerId();

    // Map of SKUs to inventories.
    Map<String, SubscriptionInventory> inventories = new TreeMap<>();

    // Grab all active and future subs.
    // Future subs are needed to calculate the nearest events like "Subscription Begin".
    List<SubscriptionCapacity> capacities =
        subCapRepo.findByOwnerAndProductId(ownerId, productId, null, null, null, now);

    for (SubscriptionCapacity cap : capacities) {
      String sku = cap.getSku();
      SubscriptionInventory inventory = inventories.get(sku);
      if (inventory == null) {
        inventory = new SubscriptionInventory();
        inventories.put(sku, inventory);

        // Product Name is not provided by subscription capacity repo, get it from offering repo.
        var productName = offeringRepo.findById(sku).map(Offering::getProductName).orElse("");
        inventory.setProductName(productName);

        var serviceLevel =
            Optional.ofNullable(cap.getServiceLevel()).orElse(ServiceLevel.EMPTY).asOpenApiEnum();
        inventory.setServiceLevel(serviceLevel);

        var usage = Optional.ofNullable(cap.getUsage()).orElse(Usage.EMPTY).asOpenApiEnum();
        inventory.setUsage(usage);

        inventory.setSubscriptionIds(new ArrayList<>());
      }

      // If the sub starts in the future, see if it will be the nearest event.
      OffsetDateTime nearestEventDate = inventory.getUpcomingEventDate();
      OffsetDateTime subBegin = cap.getBeginDate();
      if (subBegin != null
          && now.isBefore(subBegin)
          && (nearestEventDate == null || subBegin.isBefore(nearestEventDate))) {
        nearestEventDate = subBegin;
        inventory.setUpcomingEventDate(nearestEventDate);
        inventory.setUpcomingEventType("Subscription Begin");
      }

      // If the sub ends in the future (it should), see if it will be the nearest event.
      OffsetDateTime subEnd = cap.getEndDate();
      if (subEnd != null
          && now.isBefore(subEnd)
          && (nearestEventDate == null || subEnd.isBefore(nearestEventDate))) {
        nearestEventDate = subEnd;
        inventory.setUpcomingEventDate(nearestEventDate);
        inventory.setUpcomingEventType("Subscription End");
      }

      // If the sub is active (doesn't start in the future nor is the end date in the past)
      // then calculate the capacities and quantities, and add the sub to the list.
      if (isActive(cap, now)) {
        // NULLS! NULLS EVERYWHERE! AND THEY'RE UNCHECKED! CHECK THEM!
        // The original API spec called for a list of subscription ids, but the mock up interfaces
        // show subscription ids. I think we actually want a list of ids.
        inventory.getSubscriptionIds().add(cap.getSubscriptionId());

        Integer physicalSockets = cap.getPhysicalSockets();
        Integer physicalCores = cap.getPhysicalCores();
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

    SubscriptionTableInventory table = new SubscriptionTableInventory();
    table.data(new ArrayList<>(inventories.values()));

    HypervisorGuestReportMeta meta = new HypervisorGuestReportMeta();
    meta.setCount(table.getData().size());
    table.setMeta(meta);

    return table;
  }

  private static boolean isActive(SubscriptionCapacity cap, OffsetDateTime now) {
    OffsetDateTime subBegin = cap.getBeginDate();
    OffsetDateTime subEnd = cap.getEndDate();

    return (subBegin == null || subBegin.isBefore(now) || subBegin.isEqual(now))
        && (subEnd == null || subEnd.isAfter(now) || subEnd.isEqual(now));
  }
}
