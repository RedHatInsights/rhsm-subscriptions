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
package org.candlepin.subscriptions.product;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.jmx.JmxException;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

/** Allows syncing of offerings. */
@Profile("capacity-ingress")
@Component
@ManagedResource
@Slf4j
public class OfferingJmxBean {
  private final OfferingSyncController offeringSync;

  private final CapacityReconciliationController capacityReconciliationController;

  public OfferingJmxBean(
      OfferingSyncController offeringSync,
      CapacityReconciliationController capacityReconciliationController) {
    this.offeringSync = offeringSync;
    this.capacityReconciliationController = capacityReconciliationController;
  }

  @ManagedOperation(description = "Sync an offering from the upstream source.")
  @ManagedOperationParameter(name = "sku", description = "A marketing SKU")
  public String syncOffering(String sku) {
    try {
      Object principal = ResourceUtils.getPrincipal();
      log.info("Sync for offering {} triggered over JMX by {}", sku, principal);
      Optional<Offering> upstream = offeringSync.getUpstreamOffering(sku);
      upstream.ifPresent(offeringSync::syncOffering);
      return upstream
          .map(Offering::toString)
          .orElseGet(
              () -> "{\"message\": \"offeringSku=\"" + sku + "\" was not found/allowlisted.\"}");
    } catch (Exception e) {
      log.error("Error syncing offering", e);
      throw new JmxException("Error syncing offering. See log for details.");
    }
  }

  @ManagedOperation(
      description = "Syncs all offerings listed in allow list from the upstream source.")
  public String syncAllOfferings() {
    try {
      Object principal = ResourceUtils.getPrincipal();
      log.info("Sync all offerings triggered over JMX by {}", principal);
      int numProducts = offeringSync.syncAllOfferings();

      return "Enqueued " + numProducts + " offerings to be synced.";
    } catch (RuntimeException e) {
      throw new JmxException("Error enqueueing offerings to be synced. See log for details.");
    }
  }

  @ManagedOperation(description = "Reconcile capacity for an offering from the upstream source.")
  @ManagedOperationParameter(name = "sku", description = "A marketing SKU")
  public void forceReconcileOffering(String sku) {
    try {
      Object principal = ResourceUtils.getPrincipal();
      log.info("Capacity Reconciliation for sku {} triggered over JMX by {}", sku, principal);
      capacityReconciliationController.reconcileCapacityForOffering(sku, 0, 100);
    } catch (Exception e) {
      log.error("Error reconciling offering", e);
      throw new JmxException("Error reconciling offering. See log for details.");
    }
  }
}
