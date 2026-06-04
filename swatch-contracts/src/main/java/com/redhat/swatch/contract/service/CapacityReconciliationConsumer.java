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
package com.redhat.swatch.contract.service;

import static com.redhat.swatch.contract.config.Channels.CAPACITY_RECONCILE_TASK;

import com.redhat.swatch.contract.model.ReconcileCapacityByOfferingTask;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class CapacityReconciliationConsumer {

  private final CapacityReconciliationService service;

  @Blocking
  @Incoming(CAPACITY_RECONCILE_TASK)
  public void consume(ReconcileCapacityByOfferingTask reconcileCapacityByOfferingTask) {
    log.info(
        "Capacity Reconciliation Worker is reconciling capacity for offering with values: {} ",
        reconcileCapacityByOfferingTask.toString());
    service.reconcileCapacityForOffering(
        reconcileCapacityByOfferingTask.getSku(),
        reconcileCapacityByOfferingTask.getOffset(),
        reconcileCapacityByOfferingTask.getLimit());
  }
}
