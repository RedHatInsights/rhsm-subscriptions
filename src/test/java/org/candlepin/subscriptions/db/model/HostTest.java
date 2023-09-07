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

import static org.candlepin.subscriptions.tally.InventoryAccountUsageCollector.populateHostFieldsFromHbi;
import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;
import org.candlepin.subscriptions.util.MetricIdUtils;
import org.junit.jupiter.api.Test;

class HostTest {

  @Test
  void populateFieldsFromHbiNull() {
    Host host = new Host();
    InventoryHostFacts inventoryHostFacts = new InventoryHostFacts();
    NormalizedFacts normalizedFacts = new NormalizedFacts();

    populateHostFieldsFromHbi(host, inventoryHostFacts, normalizedFacts);

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
  void populateFieldsFromHbiPhysical() {
    Host host = new Host();
    InventoryHostFacts inventoryHostFacts = getInventoryHostFactsFull();
    NormalizedFacts normalizedFacts = getNormalizedFactsPhysical();

    populateHostFieldsFromHbi(host, inventoryHostFacts, normalizedFacts);

    assertEquals(host.getInventoryId(), inventoryHostFacts.getInventoryId().toString());
    assertEquals(host.getInsightsId(), inventoryHostFacts.getInsightsId());
    assertEquals(host.getAccountNumber(), inventoryHostFacts.getAccount());
    assertEquals(host.getOrgId(), inventoryHostFacts.getOrgId());
    assertEquals(host.getDisplayName(), inventoryHostFacts.getDisplayName());
    assertEquals(host.getSubscriptionManagerId(), inventoryHostFacts.getSubscriptionManagerId());
    assertFalse(host.isGuest());
    assertFalse(host.isHypervisor());
    assertEquals(host.getHypervisorUuid(), normalizedFacts.getHypervisorUuid());
    assertEquals(2, host.getMeasurements().size());
    assertEquals(host.getLastSeen(), inventoryHostFacts.getModifiedOn());
    assertEquals(host.getHardwareType(), normalizedFacts.getHardwareType());
  }

  @Test
  void populateFieldsFromHbiUnmappedGuest() {
    Host host = new Host();
    InventoryHostFacts inventoryHostFacts = getInventoryHostFactsFull();
    NormalizedFacts normalizedFacts = getNormalizedFactsUnmappedGuest();

    populateHostFieldsFromHbi(host, inventoryHostFacts, normalizedFacts);
    assertEquals(host.getInventoryId(), inventoryHostFacts.getInventoryId().toString());
    assertEquals(host.getInsightsId(), inventoryHostFacts.getInsightsId());
    assertEquals(host.getAccountNumber(), inventoryHostFacts.getAccount());
    assertEquals(host.getOrgId(), inventoryHostFacts.getOrgId());
    assertEquals(host.getDisplayName(), inventoryHostFacts.getDisplayName());
    assertEquals(host.getSubscriptionManagerId(), inventoryHostFacts.getSubscriptionManagerId());
    assertTrue(host.isGuest());
    assertFalse(host.isHypervisor());
    assertEquals(host.getHypervisorUuid(), normalizedFacts.getHypervisorUuid());
    assertEquals(2, host.getMeasurements().size());
    assertEquals(host.getCloudProvider(), normalizedFacts.getCloudProviderType().name());
    assertEquals(host.getLastSeen(), inventoryHostFacts.getModifiedOn());
    assertEquals(host.getHardwareType(), normalizedFacts.getHardwareType());
  }

  @Test
  void populateFieldsFromHbiMappedGuest() {
    Host host = new Host();
    InventoryHostFacts inventoryHostFacts = getInventoryHostFactsFull();
    NormalizedFacts normalizedFacts = getNormalizedFactsUnmappedGuest();

    populateHostFieldsFromHbi(host, inventoryHostFacts, normalizedFacts);
    assertEquals(host.getInventoryId(), inventoryHostFacts.getInventoryId().toString());
    assertEquals(host.getInsightsId(), inventoryHostFacts.getInsightsId());
    assertEquals(host.getAccountNumber(), inventoryHostFacts.getAccount());
    assertEquals(host.getOrgId(), inventoryHostFacts.getOrgId());
    assertEquals(host.getDisplayName(), inventoryHostFacts.getDisplayName());
    assertEquals(host.getSubscriptionManagerId(), inventoryHostFacts.getSubscriptionManagerId());
    assertTrue(host.isGuest());
    assertFalse(host.isHypervisor());
    assertEquals(host.getHypervisorUuid(), normalizedFacts.getHypervisorUuid());
    assertEquals(2, host.getMeasurements().size());
    assertEquals(host.getCloudProvider(), normalizedFacts.getCloudProviderType().name());
    assertEquals(host.getLastSeen(), inventoryHostFacts.getModifiedOn());
    assertEquals(host.getHardwareType(), normalizedFacts.getHardwareType());
  }

  @Test
  void populateFieldsFromHbiHypervisor() {
    Host host = new Host();
    InventoryHostFacts inventoryHostFacts = getInventoryHostFactsFull();
    NormalizedFacts normalizedFacts = getNormalizedFactsHypervisor();

    populateHostFieldsFromHbi(host, inventoryHostFacts, normalizedFacts);
    assertEquals(host.getInventoryId(), inventoryHostFacts.getInventoryId().toString());
    assertEquals(host.getInsightsId(), inventoryHostFacts.getInsightsId());
    assertEquals(host.getAccountNumber(), inventoryHostFacts.getAccount());
    assertEquals(host.getOrgId(), inventoryHostFacts.getOrgId());
    assertEquals(host.getDisplayName(), inventoryHostFacts.getDisplayName());
    assertEquals(host.getSubscriptionManagerId(), inventoryHostFacts.getSubscriptionManagerId());
    assertFalse(host.isGuest());
    assertTrue(host.isHypervisor());
    assertEquals(host.getHypervisorUuid(), normalizedFacts.getHypervisorUuid());
    assertEquals(2, host.getMeasurements().size());
    assertEquals(host.getLastSeen(), inventoryHostFacts.getModifiedOn());
    assertEquals(host.getHardwareType(), normalizedFacts.getHardwareType());
  }

  @Test
  void testRemoveRangeRemovesMultipleMonths() {
    Host host = new Host();
    host.addToMonthlyTotal("2021-01", MetricIdUtils.getCores(), 1.0);
    host.addToMonthlyTotal("2021-02", MetricIdUtils.getCores(), 2.0);
    host.clearMonthlyTotals(
        OffsetDateTime.parse("2021-01-01T00:00:00Z"), OffsetDateTime.parse("2021-02-01T00:00:00Z"));
    assertTrue(host.getMonthlyTotals().values().stream().allMatch(v -> v == 0));
  }

  @Test
  void testAsTallyHostViewApiHostSetsMonthlyCoreHours() {
    HostTallyBucket b1 = new HostTallyBucket();
    b1.setMeasurementType(HardwareMeasurementType.PHYSICAL);

    Host host = new Host();
    host.setBuckets(Set.of(b1));
    host.addToMonthlyTotal("2021-01", MetricIdUtils.getCores(), 1.0);
    host.addToMonthlyTotal("2021-01", MetricIdUtils.getCores(), 1.0);
    host.addToMonthlyTotal("2021-02", MetricIdUtils.getCores(), 2.0);
    host.addToMonthlyTotal("2021-02", MetricIdUtils.getCores(), 3.0);

    assertEquals(2.0, host.asTallyHostViewApiHost("2021-01").getCoreHours());
    assertEquals(5.0, host.asTallyHostViewApiHost("2021-02").getCoreHours());
  }

  @Test
  void testExistingBucketsAreUpdatedAndNotDuplicated() {
    Host host = new Host();
    host.setId(UUID.randomUUID());

    HostTallyBucket b1 =
        new HostTallyBucket(
            null, // NOTE: it is important to pass null here to simulate a new bucket
            "foo",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            "bar",
            true,
            1,
            1,
            HardwareMeasurementType.PHYSICAL);
    HostTallyBucket b2 =
        new HostTallyBucket(
            null, // NOTE: it is important to pass null here to simulate a new bucket
            "foo",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            "bar",
            true,
            2,
            2,
            HardwareMeasurementType.PHYSICAL);

    host.addBucket(b1);
    host.addBucket(b2);

    assertEquals(1, host.getBuckets().size());
    HostTallyBucket actualBucket = host.getBuckets().stream().findFirst().orElseThrow();
    assertEquals(2, actualBucket.getCores());
    assertEquals(2, actualBucket.getSockets());
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

  private NormalizedFacts getNormalizedFactsMappedGuest() {
    NormalizedFacts normalizedFacts = new NormalizedFacts();

    normalizedFacts.setHardwareType(HostHardwareType.VIRTUALIZED);
    normalizedFacts.setHypervisorUuid(UUID.randomUUID().toString());
    normalizedFacts.setCores(1);
    normalizedFacts.setSockets(1);
    normalizedFacts.setCloudProviderType(HardwareMeasurementType.GOOGLE);

    return normalizedFacts;
  }

  private NormalizedFacts getNormalizedFactsUnmappedGuest() {
    NormalizedFacts normalizedFacts = new NormalizedFacts();

    normalizedFacts.setHardwareType(HostHardwareType.VIRTUALIZED);
    normalizedFacts.setHypervisorUuid("");
    normalizedFacts.setCores(1);
    normalizedFacts.setSockets(1);
    normalizedFacts.setCloudProviderType(HardwareMeasurementType.GOOGLE);

    return normalizedFacts;
  }

  private NormalizedFacts getNormalizedFactsPhysical() {
    NormalizedFacts normalizedFacts = new NormalizedFacts();

    normalizedFacts.setHardwareType(HostHardwareType.PHYSICAL);
    normalizedFacts.setCores(1);
    normalizedFacts.setSockets(1);

    return normalizedFacts;
  }

  private NormalizedFacts getNormalizedFactsHypervisor() {
    NormalizedFacts normalizedFacts = new NormalizedFacts();

    normalizedFacts.setHardwareType(HostHardwareType.PHYSICAL);
    normalizedFacts.setHypervisorUuid(UUID.randomUUID().toString());
    normalizedFacts.setHypervisor(true);
    normalizedFacts.setCores(1);
    normalizedFacts.setSockets(1);

    return normalizedFacts;
  }
}
