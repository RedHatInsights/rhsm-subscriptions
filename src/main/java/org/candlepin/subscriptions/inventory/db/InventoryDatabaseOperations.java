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

import java.util.Collection;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Isolates readonly transaction for inventory database operations. */
@Component
public class InventoryDatabaseOperations {

  private final InventoryRepository repo;

  public InventoryDatabaseOperations(InventoryRepository inventoryRepository) {
    this.repo = inventoryRepository;
  }

  @Transactional(value = "inventoryTransactionManager", readOnly = true)
  public void processHostFacts(
      Collection<String> orgIds, int culledOffsetDays, Consumer<InventoryHostFacts> consumer) {
    try (Stream<InventoryHostFacts> hostFactStream = repo.getFacts(orgIds, culledOffsetDays)) {
      hostFactStream.forEach(consumer::accept);
    }
  }

  @Transactional(value = "inventoryTransactionManager", readOnly = true)
  public void reportedHypervisors(Collection<String> orgIds, Consumer<Object[]> consumer) {
    try (Stream<Object[]> stream = repo.getReportedHypervisors(orgIds)) {
      stream.forEach(consumer::accept);
    }
  }
}
