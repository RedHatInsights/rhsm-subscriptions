/*
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Encapsulates paging logic for inventory database operations.
 */
@Component
public class InventoryDatabaseOperations {

    private final ApplicationProperties properties;
    private final InventoryRepository repo;

    public InventoryDatabaseOperations(ApplicationProperties properties,
        InventoryRepository inventoryRepository) {
        this.properties = properties;
        this.repo = inventoryRepository;
    }

    public void processHostFacts(Collection<String> accounts, int culledOffsetDays,
        Consumer<InventoryHostFacts> consumer) {
        UUID offset = null;
        List<InventoryHostFacts> hostBatch;
        do {
            hostBatch = repo.getFacts(accounts, culledOffsetDays, offset, properties.getHostBatchSize());
            for (InventoryHostFacts host : hostBatch) {
                offset = host.getInventoryId();
                consumer.accept(host);
            }
        } while (!hostBatch.isEmpty());
    }

    public void reportedHypervisors(Collection<String> accounts, Consumer<Object[]> consumer) {
        String offset = null;
        List<Object[]> mappingBatch;
        do {
            mappingBatch = repo.getRhsmReportedHypervisors(accounts, offset,
                properties.getHypervisorGuestMappingBatchSize());
            for (Object[] mapping : mappingBatch) {
                offset = mapping[0].toString();
                consumer.accept(mapping);
            }
        } while (!mappingBatch.isEmpty());
        do {
            mappingBatch = repo.getSatelliteReportedHypervisors(accounts, offset,
                properties.getHypervisorGuestMappingBatchSize());
            for (Object[] mapping : mappingBatch) {
                offset = mapping[0].toString();
                consumer.accept(mapping);
            }
        } while (!mappingBatch.isEmpty());
    }
}
