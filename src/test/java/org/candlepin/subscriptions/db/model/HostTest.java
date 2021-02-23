/*
 * Copyright (c) 2019 - 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.db.model;

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;


public class HostTest {

    InventoryHostFacts inventoryHostFacts = new InventoryHostFacts();
    NormalizedFacts normalizedFacts = new NormalizedFacts();

    @Test
    void populateFieldsFromHbiNull() {
        final Host host = new Host();

        host.populateFieldsFromHbi(inventoryHostFacts, normalizedFacts);

        assertNull(host.getInventoryId());
        assertNull(host.getInsightsId());
        assertNull(host.getAccountNumber());
        assertNull(host.getOrgId());
        assertNull(host.getDisplayName());
        assertNull(host.getSubscriptionManagerId());
        assertFalse(host.getGuest());
        assertNull(host.getHypervisorUuid());
        assertEquals(host.getMeasurements().size(), 0);
        assertFalse(host.isHypervisor());
        assertNull(host.getCloudProvider());
        assertNull(host.getLastSeen());
        assertNull(host.getHardwareType());
    }

    @Test
    void populateFieldsFromHbiFull() {
        final Host host = new Host();

        inventoryHostFacts = getInventoryHostFactsFull();
        normalizedFacts = getNormalizedFactsFull();

        host.populateFieldsFromHbi(inventoryHostFacts, normalizedFacts);

        assertEquals(host.getInventoryId(), inventoryHostFacts.getInventoryId().toString());
        assertEquals(host.getInsightsId(), inventoryHostFacts.getInsightsId());
        assertEquals(host.getAccountNumber(), inventoryHostFacts.getAccount());
        assertEquals(host.getOrgId(), inventoryHostFacts.getOrgId());
        assertEquals(host.getDisplayName(), inventoryHostFacts.getDisplayName());
        assertEquals(host.getSubscriptionManagerId(), inventoryHostFacts.getSubscriptionManagerId());
        assertTrue(host.getGuest());
        assertEquals(host.getHypervisorUuid(), normalizedFacts.getHypervisorUuid());
        assertEquals(host.getMeasurements().size(), 2);
        assertTrue(host.isHypervisor());
        assertEquals(host.getCloudProvider(), normalizedFacts.getCloudProviderType().name());
        assertEquals(host.getLastSeen(), inventoryHostFacts.getModifiedOn());
        assertEquals(host.getHardwareType(), normalizedFacts.getHardwareType());
    }

    private InventoryHostFacts getInventoryHostFactsFull() {
        inventoryHostFacts.setInventoryId(UUID.randomUUID());
        inventoryHostFacts.setInsightsId("123");
        inventoryHostFacts.setAccount("234");
        inventoryHostFacts.setOrgId("345");
        inventoryHostFacts.setDisplayName("test");
        inventoryHostFacts.setSubscriptionManagerId("456");
        inventoryHostFacts.setModifiedOn(OffsetDateTime.MIN);

        return inventoryHostFacts;
    }

    private NormalizedFacts getNormalizedFactsFull() {
        normalizedFacts.setVirtual(true);
        normalizedFacts.setHypervisorUuid(UUID.randomUUID().toString());
        normalizedFacts.setCores(1);
        normalizedFacts.setSockets(1);
        normalizedFacts.setHypervisor(true);
        normalizedFacts.setHypervisor(true);
        normalizedFacts.setHypervisorUnknown(true);
        normalizedFacts.setCloudProviderType(HardwareMeasurementType.GOOGLE);
        normalizedFacts.setHardwareType(HostHardwareType.CLOUD);

        return normalizedFacts;
    }
}
