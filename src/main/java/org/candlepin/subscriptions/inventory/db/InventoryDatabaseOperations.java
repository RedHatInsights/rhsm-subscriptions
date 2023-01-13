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
package org.candlepin.subscriptions.inventory.db;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.HypervisorData;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Isolates readonly transaction for inventory database operations. */
@Component
@Slf4j
public class InventoryDatabaseOperations {

  private final InventoryRepository repo;

  public InventoryDatabaseOperations(InventoryRepository inventoryRepository) {
    this.repo = inventoryRepository;
  }

  @Transactional(value = "inventoryTransactionManager", readOnly = true)
  public void processHost(
      String orgId, int culledOffsetDays, Consumer<InventoryHostFacts> consumer) {
    try (Stream<InventoryHostFacts> hostFactStream =
        repo.getFacts(List.of(orgId), culledOffsetDays)) {
      hostFactStream.forEach(consumer::accept);
    }
  }

  /* This method is transactional since we are using a Stream and keeping the cursor open requires
  a transaction. */
  @Transactional(value = "inventoryTransactionManager", readOnly = true)
  public void fetchReportedHypervisors(String orgId, HypervisorData hypervisorData) {
    try (Stream<Object[]> stream = repo.getReportedHypervisors(List.of(orgId))) {
      stream.forEach(
          reported -> hypervisorData.addHostMapping((String) reported[0], (String) reported[1]));
    }
    log.info("Found {} reported hypervisors.", hypervisorData.getHypervisorMapping().size());
  }

  public int activeSystemCountForOrgId(String orgId, int culledOffsetDays) {
    return repo.activeSystemCountForOrgId(orgId, culledOffsetDays);
  }
}
