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
package org.candlepin.subscriptions.subscription;

import org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.*;
import org.springframework.stereotype.Component;

import javax.validation.constraints.Min;
import java.time.OffsetDateTime;
import java.util.*;

@Component
public class SubscriptionTableController {

  private final SubscriptionCapacityViewRepository subscriptionCapacityViewRepository;
  private final ApplicationClock clock;

  private SubscriptionTableController(
      SubscriptionCapacityViewRepository subscriptionCapacityViewRepository,
      ApplicationClock clock) {
    this.subscriptionCapacityViewRepository = subscriptionCapacityViewRepository;
    this.clock = clock;
  }

  public Map<String, SkuCapacity> getSkuCapacityReport(
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
    /*
    Notes:
    - Ignoring beginning and ending query params, as the table should (currently, anyway) only
      report on the latest subscription status information.
    - The implementation will report "subscription end" events for Next Event ONLY. That is,
      future-dated subs are not taken into account when reporting the Next Event; only currently
      active subs are used for calculating Next Event.
    */

    OffsetDateTime now = clock.now();
    OffsetDateTime reportStart = now.minusYears(1);

    String ownerId = ResourceUtils.getOwnerId();

    // Map of SKUs to inventories.
    Map<String, SkuCapacity> inventories = new HashMap<>();

    // Grab all active and future subs.
    // Future subs are needed to calculate the nearest events like "Subscription Begin".
    ServiceLevel sanitizedSla = ResourceUtils.sanitizeServiceLevel(sla);
    Usage sanitizedUsage = ResourceUtils.sanitizeUsage(usage);

    List<SubscriptionCapacityView> capacities =
        subscriptionCapacityViewRepository.findByKeyOwnerIdAndKeyProductIdAndServiceLevelAndUsageAndBeginDateGreaterThanEqualAndEndDateLessThanEqual(
            ownerId, productId.toString(), sanitizedSla, sanitizedUsage, reportStart, now);

    for (SubscriptionCapacityView subscriptionCapacityView : capacities) {
      String sku = subscriptionCapacityView.getSku();
      final SkuCapacity inventory =
          inventories.computeIfAbsent(
              sku, key -> initializeDefaultSkuCapacity(subscriptionCapacityView, uom));
      calculateNextEvent(subscriptionCapacityView, inventory, now);
      addSubscriptionInformation(subscriptionCapacityView, inventory);
      addTotalCapacity(subscriptionCapacityView, inventory);
    }

    return inventories;
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

  public void calculateNextEvent(
      SubscriptionCapacityView subscriptionCapacityView,
      SkuCapacity skuCapacity,
      OffsetDateTime now) {

    OffsetDateTime nearestEventDate = skuCapacity.getUpcomingEventDate();
    OffsetDateTime subEnd = subscriptionCapacityView.getEndDate();
    if (subEnd != null
        && now.isBefore(subEnd)
        && (nearestEventDate == null || subEnd.isBefore(nearestEventDate))) {
      nearestEventDate = subEnd;
      skuCapacity.setUpcomingEventDate(nearestEventDate);
      skuCapacity.setUpcomingEventType(SubscriptionEventType.END);
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
    } else if (physicalSockets != 0) {
      skuCapacity.setPhysicalCapacity(skuCapacity.getPhysicalCapacity() + physicalSockets);
      skuCapacity.setVirtualCapacity(skuCapacity.getVirtualCapacity() + virtualSockets);
      if (skuCapacity.getUom() == null) {
        skuCapacity.setUom(Uom.SOCKETS);
      }
    } else if (physicalCores != 0) {
      skuCapacity.setPhysicalCapacity(skuCapacity.getPhysicalCapacity() + physicalCores);
      skuCapacity.setVirtualCapacity(skuCapacity.getVirtualCapacity() + virtualCores);
      if (skuCapacity.getUom() == null) {
        skuCapacity.setUom(Uom.CORES);
      }
    }

    skuCapacity.setTotalCapacity(
        skuCapacity.getPhysicalCapacity() + skuCapacity.getVirtualCapacity());
  }
}
