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
package org.candlepin.subscriptions.db.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;
import org.junit.jupiter.api.Test;

class HostTest {

  @Test
  void populateFieldsFromHbiNull() {
    Host host = new Host();
    InventoryHostFacts inventoryHostFacts = new InventoryHostFacts();
    NormalizedFacts normalizedFacts = new NormalizedFacts();

    host.populateFieldsFromHbi(inventoryHostFacts, normalizedFacts);

    assertNull(host.getInventoryId());
    assertNull(host.getInsightsId());
    assertNull(host.getAccountNumber());
    assertNull(host.getOrgId());
    assertNull(host.getDisplayName());
    assertNull(host.getSubscriptionManagerId());
    assertFalse(host.isGuest());
    assertNull(host.getHypervisorUuid());
    assertEquals(0, host.getMeasurements().size());
    assertFalse(host.isHypervisor());
    assertNull(host.getCloudProvider());
    assertNull(host.getLastSeen());
    assertNull(host.getHardwareType());
  }

  @Test
  void populateFieldsFromHbiFull() {
    Host host = new Host();
    InventoryHostFacts inventoryHostFacts = getInventoryHostFactsFull();
    NormalizedFacts normalizedFacts = getNormalizedFactsFull();

    host.populateFieldsFromHbi(inventoryHostFacts, normalizedFacts);

    assertEquals(host.getInventoryId(), inventoryHostFacts.getInventoryId().toString());
    assertEquals(host.getInsightsId(), inventoryHostFacts.getInsightsId());
    assertEquals(host.getAccountNumber(), inventoryHostFacts.getAccount());
    assertEquals(host.getOrgId(), inventoryHostFacts.getOrgId());
    assertEquals(host.getDisplayName(), inventoryHostFacts.getDisplayName());
    assertEquals(host.getSubscriptionManagerId(), inventoryHostFacts.getSubscriptionManagerId());
    assertTrue(host.isGuest());
    assertEquals(host.getHypervisorUuid(), normalizedFacts.getHypervisorUuid());
    assertEquals(2, host.getMeasurements().size());
    assertTrue(host.isHypervisor());
    assertEquals(host.getCloudProvider(), normalizedFacts.getCloudProviderType().name());
    assertEquals(host.getLastSeen(), inventoryHostFacts.getModifiedOn());
    assertEquals(host.getHardwareType(), normalizedFacts.getHardwareType());
  }

  @Test
  void testRemoveRangeRemovesMultipleMonths() {
    Host host = new Host();
    host.addToMonthlyTotal("2021-01", Measurement.Uom.CORES, 1.0);
    host.addToMonthlyTotal("2021-02", Measurement.Uom.CORES, 2.0);
    host.clearMonthlyTotals(
        OffsetDateTime.parse("2021-01-01T00:00:00Z"), OffsetDateTime.parse("2021-02-01T00:00:00Z"));
    assertTrue(host.getMonthlyTotals().isEmpty());
  }

  @Test
  void testAsTallyHostViewApiHostSetsMonthlyCoreHours() {
    HostTallyBucket b1 = new HostTallyBucket();
    b1.setMeasurementType(HardwareMeasurementType.PHYSICAL);

    Host host = new Host();
    host.setBuckets(Set.of(b1));
    host.addToMonthlyTotal("2021-01", Measurement.Uom.CORES, 1.0);
    host.addToMonthlyTotal("2021-01", Measurement.Uom.CORES, 1.0);
    host.addToMonthlyTotal("2021-02", Measurement.Uom.CORES, 2.0);
    host.addToMonthlyTotal("2021-02", Measurement.Uom.CORES, 3.0);

    assertEquals(2.0, host.asTallyHostViewApiHost("2021-01").getCoreHours());
    assertEquals(5.0, host.asTallyHostViewApiHost("2021-02").getCoreHours());
  }

  private InventoryHostFacts getInventoryHostFactsFull() {
    InventoryHostFacts inventoryHostFacts = new InventoryHostFacts();

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
    NormalizedFacts normalizedFacts = new NormalizedFacts();

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
