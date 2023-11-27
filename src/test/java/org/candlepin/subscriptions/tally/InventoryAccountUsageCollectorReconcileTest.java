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
package org.candlepin.subscriptions.tally;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.in;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Set;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.HostTallyBucketRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostHardwareType;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.inventory.db.InventoryDatabaseOperations;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryAccountUsageCollectorReconcileTest {
  @Mock FactNormalizer factNormalizer;
  @Mock InventoryDatabaseOperations inventory;
  @Mock AccountServiceInventoryRepository accountServiceInventoryRepository;
  @Mock InventoryRepository inventoryRepository;
  @Mock HostRepository hostRepository;
  @Mock EntityManager entityManager;
  @Mock HostTallyBucketRepository tallyBucketRepository;
  @Mock ApplicationProperties props;
  @Mock MeterRegistry meterRegistry;
  @Mock InventorySwatchDataCollator collator;

  InventoryAccountUsageCollector setupCollector() {
    return new InventoryAccountUsageCollector(
        factNormalizer,
        inventory,
        accountServiceInventoryRepository,
        hostRepository,
        entityManager,
        tallyBucketRepository,
        props,
        meterRegistry,
        collator);
  }

  @Test
  void testFlushingHappensAtConfiguredInterval() {
    ArgumentCaptor<InventorySwatchDataCollator.Processor> captor =
        ArgumentCaptor.forClass(InventorySwatchDataCollator.Processor.class);

    when(props.getHbiReconciliationFlushInterval()).thenReturn(2L);
    when(collator.collateData(any(), anyInt(), captor.capture())).thenReturn(5);
    when(factNormalizer.normalize(any(), any())).thenReturn(new NormalizedFacts());
    when(hostRepository.save(any())).thenReturn(new Host());

    var collector = setupCollector();
    collector.reconcileSystemDataWithHbi("org123", Set.of("RHEL"));
    // at this point, the iterations haven't actually run, since we're mocking the collator.
    var processor = captor.getValue();
    var mockHbiSystem = InventoryHostFactTestHelper.createHypervisor("org", 1);
    processor.accept(mockHbiSystem, null, new OrgHostsData("placeholder"), 1);
    processor.accept(mockHbiSystem, null, new OrgHostsData("placeholder"), 2);
    processor.accept(mockHbiSystem, null, new OrgHostsData("placeholder"), 3);
    processor.accept(mockHbiSystem, null, new OrgHostsData("placeholder"), 4);
    processor.accept(mockHbiSystem, null, new OrgHostsData("placeholder"), 5);
    verify(hostRepository, times(2)).flush();
  }

  @Test
  void testCreate() {
    when(factNormalizer.normalize(any(), any())).thenReturn(new NormalizedFacts());
    when(hostRepository.save(any())).thenReturn(new Host());
    var collector = setupCollector();
    InventoryHostFacts hbiSystem = InventoryHostFactTestHelper.createHypervisor("org123", 1);
    collector.reconcileHbiSystemWithSwatchSystem(
        hbiSystem, null, new OrgHostsData("org123"), Set.of("RHEL"), new ArrayList<>());
    verify(hostRepository).save(any());
  }

  @Test
  void testUpdate() {
    when(factNormalizer.normalize(any(), any())).thenReturn(new NormalizedFacts());

    var collector = setupCollector();
    InventoryHostFacts hbiSystem = InventoryHostFactTestHelper.createHypervisor("org123", 1);
    Host swatchSystem = new Host();
    collector.reconcileHbiSystemWithSwatchSystem(
        hbiSystem, swatchSystem, new OrgHostsData("org123"), Set.of("RHEL"), new ArrayList<>());
    verify(hostRepository).save(swatchSystem);
  }

  @Test
  void testDelete() {
    var collector = setupCollector();
    Host swatchSystem = new Host();
    collector.reconcileHbiSystemWithSwatchSystem(
        null, swatchSystem, new OrgHostsData("org123"), Set.of("RHEL"), new ArrayList<>());
    verify(hostRepository).delete(swatchSystem);
  }

  @Test
  void testGuestBucketsTracked() {
    NormalizedFacts guestFacts = new NormalizedFacts();
    guestFacts.setProducts(Set.of("RHEL"));
    guestFacts.setHardwareType(HostHardwareType.VIRTUALIZED);
    guestFacts.setHypervisorUnknown(false);
    guestFacts.setHypervisorUuid("123e4567-e89b-12d3-a456-426614174000");
    when(factNormalizer.normalize(any(), any())).thenReturn(guestFacts);

    var collector = setupCollector();
    var hbiSystem = new InventoryHostFacts();
    Host swatchSystem = new Host();
    OrgHostsData orgHostsData = new OrgHostsData("org123");
    Host hypervisorRecord = new Host();
    orgHostsData.addHostToHypervisor("123e4567-e89b-12d3-a456-426614174000", hypervisorRecord);
    orgHostsData.addHostMapping(
        "123e4567-e89b-12d3-a456-426614174000", "123e4567-e89b-12d3-a456-426614174000");
    orgHostsData.addHypervisorFacts("123e4567-e89b-12d3-a456-426614174000", new NormalizedFacts());
    collector.reconcileHbiSystemWithSwatchSystem(
        hbiSystem, swatchSystem, orgHostsData, Set.of("RHEL"), new ArrayList<>());
    HostTallyBucket expectedEmptyBucket =
        new HostTallyBucket(
            swatchSystem,
            "RHEL",
            ServiceLevel.EMPTY,
            Usage.EMPTY,
            BillingProvider._ANY,
            "_ANY",
            true,
            0,
            0,
            HardwareMeasurementType.HYPERVISOR);
    HostTallyBucket expectedAnyBucket =
        new HostTallyBucket(
            swatchSystem,
            "RHEL",
            ServiceLevel._ANY,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            true,
            0,
            0,
            HardwareMeasurementType.HYPERVISOR);
    assertThat(expectedEmptyBucket, in(hypervisorRecord.getBuckets()));
    assertThat(expectedAnyBucket, in(hypervisorRecord.getBuckets()));
    assertTrue(swatchSystem.getBuckets().isEmpty());
  }

  @Test
  void testHypervisorBucketsApplied() {
    NormalizedFacts hypervisorFacts = new NormalizedFacts();
    hypervisorFacts.setProducts(Set.of("RHEL"));
    hypervisorFacts.setHardwareType(HostHardwareType.PHYSICAL);
    hypervisorFacts.setSockets(4);
    hypervisorFacts.setCores(8);
    when(factNormalizer.normalize(any(), any())).thenReturn(hypervisorFacts);

    var collector = setupCollector();
    var hbiSystem = new InventoryHostFacts();
    hbiSystem.setSubscriptionManagerId("123e4567-e89b-12d3-a456-426614174000");
    Host swatchSystem = new Host();
    OrgHostsData orgHostsData = new OrgHostsData("org123");
    Host placeholder = new Host();
    HostTallyBucket expectedBucket =
        new HostTallyBucket(
            swatchSystem,
            "hitchhiker",
            ServiceLevel._ANY,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            true,
            0,
            0,
            HardwareMeasurementType.HYPERVISOR);
    placeholder.addBucket(expectedBucket);
    orgHostsData.addHostToHypervisor("123e4567-e89b-12d3-a456-426614174000", placeholder);
    orgHostsData.addHostMapping(
        "123e4567-e89b-12d3-a456-426614174000", "123e4567-e89b-12d3-a456-426614174000");
    orgHostsData.addHypervisorFacts("123e4567-e89b-12d3-a456-426614174000", new NormalizedFacts());
    collector.reconcileHbiSystemWithSwatchSystem(
        hbiSystem, swatchSystem, orgHostsData, Set.of("RHEL"), new ArrayList<>());
    assertThat(expectedBucket, in(swatchSystem.getBuckets()));
    assertEquals(4, expectedBucket.getSockets());
    assertEquals(8, expectedBucket.getCores());
  }

  @Test
  void testStaleHypervisorBucketsRemoved() {
    NormalizedFacts hypervisorFacts = new NormalizedFacts();
    hypervisorFacts.setHardwareType(HostHardwareType.PHYSICAL);
    hypervisorFacts.setSockets(4);
    hypervisorFacts.setCores(8);
    when(factNormalizer.normalize(any(), any())).thenReturn(hypervisorFacts);

    var collector = setupCollector();
    var hbiSystem = new InventoryHostFacts();
    hbiSystem.setSubscriptionManagerId("123e4567-e89b-12d3-a456-426614174000");
    Host swatchSystem = new Host();
    OrgHostsData orgHostsData = new OrgHostsData("org123");
    Host placeholder = new Host();
    HostTallyBucket staleBucket =
        new HostTallyBucket(
            swatchSystem,
            "stale",
            ServiceLevel._ANY,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            true,
            0,
            0,
            HardwareMeasurementType.HYPERVISOR);
    swatchSystem.addBucket(staleBucket);
    orgHostsData.addHostToHypervisor("123e4567-e89b-12d3-a456-426614174000", placeholder);
    orgHostsData.addHostMapping(
        "123e4567-e89b-12d3-a456-426614174000", "123e4567-e89b-12d3-a456-426614174000");
    orgHostsData.addHypervisorFacts("123e4567-e89b-12d3-a456-426614174000", new NormalizedFacts());
    collector.reconcileHbiSystemWithSwatchSystem(
        hbiSystem, swatchSystem, orgHostsData, Set.of("RHEL"), new ArrayList<>());
    assertTrue(swatchSystem.getBuckets().isEmpty());
  }

  @Test
  void testNonHypervisorBucketsApplied() {
    NormalizedFacts normalizedFacts = new NormalizedFacts();
    normalizedFacts.setProducts(Set.of("RHEL"));
    normalizedFacts.setHardwareType(HostHardwareType.PHYSICAL);
    when(factNormalizer.normalize(any(), any())).thenReturn(normalizedFacts);

    var collector = setupCollector();
    var hbiSystem = new InventoryHostFacts();
    Host swatchSystem = new Host();
    OrgHostsData orgHostsData = new OrgHostsData("org123");
    collector.reconcileHbiSystemWithSwatchSystem(
        hbiSystem, swatchSystem, orgHostsData, Set.of("RHEL"), new ArrayList<>());
    HostTallyBucket expectedEmptyBucket =
        new HostTallyBucket(
            swatchSystem,
            "RHEL",
            ServiceLevel.EMPTY,
            Usage.EMPTY,
            BillingProvider._ANY,
            "_ANY",
            false,
            null,
            null,
            HardwareMeasurementType.PHYSICAL);
    HostTallyBucket expectedAnyBucket =
        new HostTallyBucket(
            swatchSystem,
            "RHEL",
            ServiceLevel._ANY,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            false,
            null,
            null,
            HardwareMeasurementType.PHYSICAL);
    assertThat(expectedEmptyBucket, in(swatchSystem.getBuckets()));
    assertThat(expectedAnyBucket, in(swatchSystem.getBuckets()));
  }

  @Test
  void testNonHypervisorStaleBucketsRemoved() {
    NormalizedFacts normalizedFacts = new NormalizedFacts();
    normalizedFacts.setHardwareType(HostHardwareType.PHYSICAL);
    when(factNormalizer.normalize(any(), any())).thenReturn(normalizedFacts);

    var collector = setupCollector();
    var hbiSystem = new InventoryHostFacts();
    Host swatchSystem = new Host();
    OrgHostsData orgHostsData = new OrgHostsData("org123");
    collector.reconcileHbiSystemWithSwatchSystem(
        hbiSystem, swatchSystem, orgHostsData, Set.of("RHEL"), new ArrayList<>());
    HostTallyBucket staleBucket =
        new HostTallyBucket(
            swatchSystem,
            "stale",
            ServiceLevel._ANY,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            false,
            0,
            0,
            HardwareMeasurementType.PHYSICAL);
    swatchSystem.addBucket(staleBucket);
    collector.reconcileHbiSystemWithSwatchSystem(
        hbiSystem, swatchSystem, orgHostsData, Set.of("RHEL"), new ArrayList<>());
    assertTrue(swatchSystem.getBuckets().isEmpty());
  }

  @Test
  void testSystemNoLongerMarkedAsHypervisorHasHypervisorBucketsRemoved() {
    NormalizedFacts normalizedFacts = new NormalizedFacts();
    normalizedFacts.setHardwareType(HostHardwareType.PHYSICAL);
    when(factNormalizer.normalize(any(), any())).thenReturn(normalizedFacts);

    var collector = setupCollector();
    var hbiSystem = new InventoryHostFacts();
    Host swatchSystem = new Host();
    OrgHostsData orgHostsData = new OrgHostsData("org123");
    collector.reconcileHbiSystemWithSwatchSystem(
        hbiSystem, swatchSystem, orgHostsData, Set.of("RHEL"), new ArrayList<>());
    HostTallyBucket staleBucket =
        new HostTallyBucket(
            swatchSystem,
            "stale",
            ServiceLevel._ANY,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            true,
            0,
            0,
            HardwareMeasurementType.HYPERVISOR);
    swatchSystem.addBucket(staleBucket);
    collector.reconcileHbiSystemWithSwatchSystem(
        hbiSystem, swatchSystem, orgHostsData, Set.of("RHEL"), new ArrayList<>());
    assertTrue(swatchSystem.getBuckets().isEmpty());
  }

  @Test
  void collectorPutsPlaceholderBucketsOnSingleCopyOfHypervisor() {
    NormalizedFacts hypervisorFacts = new NormalizedFacts();
    hypervisorFacts.setProducts(Set.of());
    hypervisorFacts.setHardwareType(HostHardwareType.PHYSICAL);
    hypervisorFacts.setSockets(4);
    hypervisorFacts.setCores(8);
    when(factNormalizer.normalize(any(), any())).thenReturn(hypervisorFacts);

    var collector = setupCollector();
    var hbiSystem = new InventoryHostFacts();
    hbiSystem.setSubscriptionManagerId("123e4567-e89b-12d3-a456-426614174000");
    Host hypervisorCopy0 = new Host();
    Host hypervisorCopy1 = new Host();
    OrgHostsData orgHostsData = new OrgHostsData("org123");
    Host placeholder = new Host();
    HostTallyBucket expectedBucket =
        new HostTallyBucket(
            hypervisorCopy0,
            "hitchhiker",
            ServiceLevel._ANY,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            true,
            0,
            0,
            HardwareMeasurementType.HYPERVISOR);
    placeholder.addBucket(expectedBucket);
    orgHostsData.addHostToHypervisor("123e4567-e89b-12d3-a456-426614174000", placeholder);
    orgHostsData.addHostMapping(
        "123e4567-e89b-12d3-a456-426614174000", "123e4567-e89b-12d3-a456-426614174000");
    orgHostsData.addHypervisorFacts("123e4567-e89b-12d3-a456-426614174000", new NormalizedFacts());
    collector.reconcileHbiSystemWithSwatchSystem(
        hbiSystem, hypervisorCopy0, orgHostsData, Set.of("RHEL"), new ArrayList<>());
    assertTrue(orgHostsData.hypervisorHostMap().isEmpty(), "placeholder was not consumed");
    assertFalse(hypervisorCopy0.getBuckets().isEmpty(), "no buckets added to hypervisor");
    collector.reconcileHbiSystemWithSwatchSystem(
        hbiSystem, hypervisorCopy1, orgHostsData, Set.of("RHEL"), new ArrayList<>());
    assertTrue(hypervisorCopy1.getBuckets().isEmpty(), "buckets added to hypervisor copy");
  }
}
