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
package org.candlepin.subscriptions.tally.facts;

import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createBaseHost;
import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createGuest;
import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createHypervisor;
import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createQpcHost;
import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createRhsmHost;
import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createSystemProfileHost;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.HostHardwareType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.OrgHostsData;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StringUtils;

@TestInstance(Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
class FactNormalizerTest {

  private FactNormalizer normalizer;

  @Autowired private ApplicationClock clock;

  @MockBean BuildProperties buildProperties;

  @BeforeAll
  void setup() {
    normalizer = new FactNormalizer(new ApplicationProperties(), clock);
  }

  private OrgHostsData hypervisorData() {
    return new OrgHostsData("Dummy Org");
  }

  @Test
  void testEmptyFactNormalization() {
    // Primarily checks the normalization process for situations that could
    // yield NPEs.
    assertDoesNotThrow(() -> normalizer.normalize(new InventoryHostFacts(), hypervisorData()));
  }

  @Test
  void testRhsmNormalization() {
    InventoryHostFacts rhsmHost = createRhsmHost(Arrays.asList(69), null, clock.now());
    rhsmHost.setSystemProfileCoresPerSocket(6);
    rhsmHost.setSystemProfileSockets(2);
    NormalizedFacts normalized = normalizer.normalize(rhsmHost, hypervisorData());
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
    assertEquals(Integer.valueOf(12), normalized.getCores());
    assertEquals(Integer.valueOf(2), normalized.getSockets());
  }

  @Test
  void testQpcNormalization() {
    NormalizedFacts normalized =
        normalizer.normalize(createQpcHost("RHEL", clock.now()), hypervisorData());
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
    assertEquals(Integer.valueOf(0), normalized.getCores());
    assertEquals(Integer.valueOf(0), normalized.getSockets());
  }

  @Test
  void testSystemProfileNormalization() {
    InventoryHostFacts host =
        createSystemProfileHost(Collections.singletonList(69), 4, 2, clock.now());
    NormalizedFacts normalized = normalizer.normalize(host, hypervisorData());
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
    assertEquals(Integer.valueOf(8), normalized.getCores());
    assertEquals(Integer.valueOf(2), normalized.getSockets());
  }

  @Test
  void testNormalizeNonRhelProduct() {
    InventoryHostFacts rhsmHost = createRhsmHost(Arrays.asList(42), null, clock.now());
    rhsmHost.setSystemProfileCoresPerSocket(4);
    rhsmHost.setSystemProfileSockets(8);
    NormalizedFacts normalized = normalizer.normalize(rhsmHost, hypervisorData());
    assertThat(normalized.getProducts(), Matchers.empty());
    assertEquals(Integer.valueOf(32), normalized.getCores());
    assertEquals(Integer.valueOf(8), normalized.getSockets());
  }

  @Test
  void testSystemProfileNonRhelProduct() {
    NormalizedFacts normalized =
        normalizer.normalize(
            createSystemProfileHost(Collections.singletonList(42), 2, 4, clock.now()),
            hypervisorData());
    assertThat(normalized.getProducts(), Matchers.empty());
    assertEquals(Integer.valueOf(8), normalized.getCores());
    assertEquals(Integer.valueOf(4), normalized.getSockets());
  }

  @Test
  void testSystemProfileInfrastructureType() {
    InventoryHostFacts baseFacts = createBaseHost("Account", "test-org");
    baseFacts.setSystemProfileInfrastructureType("virtual");
    baseFacts.setSyncTimestamp(clock.now().toString());

    NormalizedFacts normalized = normalizer.normalize(baseFacts, hypervisorData());
    assertThat(normalized.isVirtual(), Matchers.is(true));
  }

  @Test
  void testNormalizeWhenProductsMissingFromFactsAndOnlyCoresAreSet() {
    InventoryHostFacts host = createRhsmHost((List<Integer>) null, null, clock.now());
    host.setSystemProfileCoresPerSocket(4);
    NormalizedFacts normalized = normalizer.normalize(host, hypervisorData());
    assertNotNull(normalized.getProducts());
    assertThat(normalized.getProducts(), Matchers.empty());
    assertEquals(Integer.valueOf(4), normalized.getCores());
    assertEquals(Integer.valueOf(0), normalized.getSockets());
  }

  @Test
  void testNormalizeWhenProductsMissingFromFactsAndOnlySocketsAreSet() {
    InventoryHostFacts host = createRhsmHost((List<Integer>) null, null, clock.now());
    host.setSystemProfileSockets(8);
    NormalizedFacts normalized = normalizer.normalize(host, hypervisorData());
    assertNotNull(normalized.getProducts());
    assertThat(normalized.getProducts(), Matchers.empty());
    assertEquals(Integer.valueOf(0), normalized.getCores());
    assertEquals(Integer.valueOf(8), normalized.getSockets());
  }

  @Test
  void testNormalizeWhenCoresAndSocketsMissingFromFacts() {
    NormalizedFacts normalized =
        normalizer.normalize(
            createRhsmHost(Arrays.asList(69), null, clock.now()), hypervisorData());
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
    assertEquals(Integer.valueOf(0), normalized.getCores());
    assertEquals(Integer.valueOf(0), normalized.getSockets());
  }

  @Test
  void testIgnoresHostWhenLastSyncIsOutOfConfiguredThreshold() {
    OffsetDateTime lastSynced = clock.now().minusDays(2);
    InventoryHostFacts facts = createRhsmHost("A1", "O1", "69", null, lastSynced);

    NormalizedFacts normalized = normalizer.normalize(facts, hypervisorData());
    assertThat(normalized.getProducts(), Matchers.empty());
    assertEquals(0, normalized.getCores());
  }

  @Test
  void testIncludesHostWhenLastSyncIsWithinTheConfiguredThreshold() {
    OffsetDateTime lastSynced = clock.now().minusDays(1);
    InventoryHostFacts facts = createRhsmHost("A1", "O1", "69", null, lastSynced);
    facts.setSystemProfileCoresPerSocket(2);
    facts.setSystemProfileSockets(2);
    NormalizedFacts normalized = normalizer.normalize(facts, hypervisorData());
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
    assertEquals(Integer.valueOf(4), normalized.getCores());
  }

  @Test
  void testRhelFromQpcFacts() {
    NormalizedFacts normalized =
        normalizer.normalize(createQpcHost("RHEL", clock.now()), hypervisorData());
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
  }

  @Test
  void testEmptyProductListWhenRhelNotPresent() {
    NormalizedFacts normalized =
        normalizer.normalize(createQpcHost("EAP", clock.now()), hypervisorData());
    assertThat(normalized.getProducts(), Matchers.empty());
  }

  @Test
  void testEmptyProductListWhenQpcProductsNotSet() {
    NormalizedFacts normalized =
        normalizer.normalize(createQpcHost(null, clock.now()), hypervisorData());
    assertThat(normalized.getProducts(), Matchers.empty());
  }

  @Test
  void testNullSocketsNormalizeToZero() {
    InventoryHostFacts host = createRhsmHost(Collections.emptyList(), null, clock.now());
    NormalizedFacts normalizedHost = normalizer.normalize(host, hypervisorData());

    assertEquals(0, normalizedHost.getSockets().intValue());
  }

  @Test
  void testDetectsMultipleProductsBasedOnProductId() {
    NormalizedFacts normalized =
        normalizer.normalize(
            createRhsmHost(Arrays.asList(69, 419, 72), null, clock.now()), hypervisorData());
    assertThat(
        normalized.getProducts(),
        Matchers.containsInAnyOrder("RHEL for x86", "RHEL for ARM", "RHEL for IBM z"));
  }

  @Test
  void testDetectsProductFromSyspurposeRole() {
    NormalizedFacts normalized =
        normalizer.normalize(
            createRhsmHost(Collections.emptyList(), "Red Hat Enterprise Linux Server", clock.now()),
            hypervisorData());
    assertThat(normalized.getProducts(), Matchers.contains("RHEL for x86"));
  }

  @Test
  void testRhelUngroupedIfNoVariants() {
    NormalizedFacts normalized =
        normalizer.normalize(createQpcHost("RHEL", clock.now()), hypervisorData());
    assertThat(normalized.getProducts(), Matchers.containsInAnyOrder("RHEL", "RHEL Ungrouped"));
  }

  @Test
  void variantFromSyspurposeWinsIfMultipleVariants() {
    NormalizedFacts normalized =
        normalizer.normalize(
            createRhsmHost(Arrays.asList(9, 10), "Red Hat Enterprise Linux Server", clock.now()),
            hypervisorData());
    assertThat(normalized.getProducts(), Matchers.contains("RHEL for x86"));
  }

  @Test
  void nonNumericProductIdIgnored() {
    NormalizedFacts normalized =
        normalizer.normalize(
            createRhsmHost(
                "A1", "O1", "69,419,Foobar", "Red Hat Enterprise Linux Server", clock.now()),
            hypervisorData());
    assertThat(
        normalized.getProducts(), Matchers.containsInAnyOrder("RHEL for x86", "RHEL " + "for ARM"));
  }

  @Test
  void testNormalizationDiscardsRHELWhenSatelliteExists() {
    InventoryHostFacts host = createRhsmHost(Collections.singletonList(250), null, clock.now());
    host.setSystemProfileCoresPerSocket(6);
    host.setSystemProfileSockets(2);
    NormalizedFacts normalized = normalizer.normalize(host, hypervisorData());
    assertThat(normalized.getProducts(), Matchers.contains("Satellite Server"));
    assertEquals(Integer.valueOf(12), normalized.getCores());
    assertEquals(Integer.valueOf(2), normalized.getSockets());
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        290, // OpenShift Container Platform
        250, // Satellite Server
        269 // Satellite Capsule
      })
  void testNormalizationDiscardsRHELWhenProductExists() {
    InventoryHostFacts host = createRhsmHost(Arrays.asList(69, 290), null, clock.now());
    host.setSystemProfileCoresPerSocket(6);
    host.setSystemProfileSockets(2);
    NormalizedFacts normalized = normalizer.normalize(host, hypervisorData());
    assertThat(normalized.getProducts(), Matchers.contains("OpenShift Container Platform"));
    assertEquals(Integer.valueOf(12), normalized.getCores());
    assertEquals(Integer.valueOf(2), normalized.getSockets());
  }

  @Test
  void testModulo2SocketNormalizationForHypervisors() {
    InventoryHostFacts hypervisor = createHypervisor("A1", "O1", 69);
    hypervisor.setSystemProfileCoresPerSocket(4);
    hypervisor.setSystemProfileSockets(3);

    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(hypervisor.getSubscriptionManagerId(), null);

    NormalizedFacts normalized = normalizer.normalize(hypervisor, guestData);
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
    assertEquals(Integer.valueOf(12), normalized.getCores());
    assertEquals(Integer.valueOf(4), normalized.getSockets());
  }

  @Test
  void testModulo2SocketNormalizationForPhysicalHosts() {
    InventoryHostFacts host = createRhsmHost(Arrays.asList(69), null, clock.now());
    host.setSystemProfileCoresPerSocket(4);
    host.setSystemProfileSockets(3);
    NormalizedFacts normalized = normalizer.normalize(host, hypervisorData());
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
    assertEquals(Integer.valueOf(12), normalized.getCores());
    assertEquals(Integer.valueOf(4), normalized.getSockets());
  }

  @Test
  void testNoModulo2SocketNormalizationForGuests() {
    InventoryHostFacts guestFacts = createGuest("hyp-id", "A1", "O1", 69);
    guestFacts.setSystemProfileCoresPerSocket(4);
    guestFacts.setSystemProfileSockets(3);
    assertTrue(guestFacts.isVirtual());

    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(guestFacts.getHypervisorUuid(), guestFacts.getHypervisorUuid());

    NormalizedFacts normalized = normalizer.normalize(guestFacts, guestData);
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
    assertEquals(Integer.valueOf(12), normalized.getCores());
    assertEquals(Integer.valueOf(3), normalized.getSockets());
  }

  @Test
  void testPhysicalNormalization() {
    InventoryHostFacts hostFacts = createBaseHost("A1", "O1");
    assertFalse(hostFacts.isVirtual());
    assertTrue(StringUtils.isEmpty(hostFacts.getHypervisorUuid()));
    assertTrue(StringUtils.isEmpty(hostFacts.getSatelliteHypervisorUuid()));

    NormalizedFacts normalized = normalizer.normalize(hostFacts, hypervisorData());
    assertFalse(normalized.isHypervisor());
    assertFalse(normalized.isVirtual());
    assertEquals(HostHardwareType.PHYSICAL, normalized.getHardwareType());
  }

  @Test
  void testIsHypervisorNormalization() {
    InventoryHostFacts facts = createHypervisor("A1", "O1", 1);
    facts.setSystemProfileCoresPerSocket(4);
    facts.setSystemProfileSockets(3);
    assertFalse(facts.isVirtual());

    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(facts.getSubscriptionManagerId(), facts.getSubscriptionManagerId());

    NormalizedFacts normalized = normalizer.normalize(facts, guestData);
    assertTrue(normalized.isHypervisor());
    assertEquals(HostHardwareType.PHYSICAL, normalized.getHardwareType());
    assertEquals(12, normalized.getCores());
    assertEquals(4, normalized.getSockets());
    assertFalse(normalized.isVirtual());
  }

  @Test
  void testIsGuestNormalization() {
    InventoryHostFacts facts = createGuest("hyp-id", "A1", "O1", 1);
    facts.setSystemProfileCoresPerSocket(4);
    facts.setSystemProfileSockets(3);
    assertTrue(facts.isVirtual());

    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(facts.getHypervisorUuid(), facts.getHypervisorUuid());

    NormalizedFacts normalized = normalizer.normalize(facts, guestData);
    assertEquals(12, normalized.getCores());
    assertEquals(3, normalized.getSockets());
    assertTrue(normalized.isVirtual());
    assertFalse(normalized.isHypervisorUnknown());
    assertFalse(normalized.isHypervisor());

    facts = createGuest(null, "A1", "O1", 1);
    facts.setSystemProfileCoresPerSocket(4);
    facts.setSystemProfileSockets(3);

    assertTrue(facts.isVirtual());

    normalized = normalizer.normalize(facts, hypervisorData());
    assertTrue(normalized.isVirtual());
    assertEquals(12, normalized.getCores());
    assertEquals(3, normalized.getSockets());
    assertEquals(HostHardwareType.VIRTUALIZED, normalized.getHardwareType());
    assertTrue(normalized.isHypervisorUnknown());
    assertFalse(normalized.isHypervisor());
  }

  @Test
  void testThatCloudProviderIsSet() {
    String expectedCloudProvider = "aws";
    InventoryHostFacts baseFacts = createBaseHost("A1", "O1");
    baseFacts.setCloudProvider(expectedCloudProvider);

    NormalizedFacts normalized = normalizer.normalize(baseFacts, hypervisorData());
    assertNotNull(normalized.getCloudProviderType());
    assertEquals(HardwareMeasurementType.AWS, normalized.getCloudProviderType());
  }

  @Test
  void testThatCloudProviderIsNotSetIfNull() {
    NormalizedFacts normalized = normalizer.normalize(createBaseHost("A1", "O1"), hypervisorData());
    assertNull(normalized.getCloudProviderType());
  }

  @Test
  void testThatCloudProviderIsNotSetIfEmpty() {
    InventoryHostFacts baseFacts = createBaseHost("A1", "O1");
    baseFacts.setCloudProvider("");

    NormalizedFacts normalized = normalizer.normalize(baseFacts, hypervisorData());
    assertNull(normalized.getCloudProviderType());
  }

  @Test
  void testThatUnsupportedCloudProviderIsNotSet() {
    String expectedCloudProvider = "unknown";
    InventoryHostFacts baseFacts = createBaseHost("A1", "O1");
    baseFacts.setCloudProvider(expectedCloudProvider);

    NormalizedFacts normalized = normalizer.normalize(baseFacts, hypervisorData());
    assertNull(normalized.getCloudProviderType());
  }

  @Test
  void testPhysicalClassification() {
    InventoryHostFacts physical = createRhsmHost(Arrays.asList(12), null, clock.now());
    NormalizedFacts facts = normalizer.normalize(physical, hypervisorData());
    assertClassification(facts, false, true, false);
  }

  @Test
  void testGuestWithMappedHypervisorClassification() {
    InventoryHostFacts guestWithMappedHypervisor = createGuest("mapped-hyp-id", "A1", "O1", 1);

    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(
        guestWithMappedHypervisor.getHypervisorUuid(),
        guestWithMappedHypervisor.getHypervisorUuid());

    NormalizedFacts facts = normalizer.normalize(guestWithMappedHypervisor, guestData);
    assertClassification(facts, false, false, true);
  }

  @Test
  void testGuestWithUnmappedHypervisorClassification() {
    InventoryHostFacts guestWithMappedHypervisor = createGuest("mapped-hyp-id", "A1", "O1", 1);

    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(guestWithMappedHypervisor.getHypervisorUuid(), null);

    NormalizedFacts facts = normalizer.normalize(guestWithMappedHypervisor, guestData);
    assertClassification(facts, false, true, true);
  }

  @Test
  void testGuestWithUnmappedHypervisorClassificationUsingSatelliteMapping() {
    InventoryHostFacts guestWithMappedHypervisor = createGuest("mapped-hyp-id", "A1", "O1", 1);
    guestWithMappedHypervisor.setHypervisorUuid(null);
    guestWithMappedHypervisor.setSatelliteHypervisorUuid("mapped-hyp-id");

    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(guestWithMappedHypervisor.getSatelliteHypervisorUuid(), null);

    NormalizedFacts facts = normalizer.normalize(guestWithMappedHypervisor, guestData);
    assertClassification(facts, false, true, true);
  }

  @Test
  void testGuestWithNullHypIdIsUnmappedHypervisorClassification() {
    InventoryHostFacts guestWithMappedHypervisor = createGuest(null, "A1", "O1", 1);

    NormalizedFacts facts = normalizer.normalize(guestWithMappedHypervisor, hypervisorData());
    assertClassification(facts, false, true, true);
  }

  @Test
  void testHypervisorClassificationWhenMapped() {
    InventoryHostFacts hypervisor = createHypervisor("A1", "O1", 1);
    hypervisor.setSystemProfileCoresPerSocket(4);
    hypervisor.setSystemProfileSockets(3);
    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    NormalizedFacts facts = normalizer.normalize(hypervisor, guestData);
    assertClassification(facts, true, true, false);
  }

  @Test
  void testHypervisorClassificationWhenUnmapped() {
    InventoryHostFacts hypervisor = createHypervisor("A1", "O1", 1);
    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(hypervisor.getSubscriptionManagerId(), null);

    NormalizedFacts facts = normalizer.normalize(hypervisor, guestData);
    assertClassification(facts, true, true, false);
  }

  static Stream<Arguments> syspurposeUnitsArgs() {
    return Stream.of(
        arguments("Sockets", 2, 0), arguments("Cores/vCPU", 0, 4), arguments("Foobar", 2, 4));
  }

  @ParameterizedTest
  @MethodSource("syspurposeUnitsArgs")
  void testSyspurposeUnits(String unit, int sockets, int cores) {
    InventoryHostFacts facts = createBaseHost("A1", "O1");
    facts.setSystemProfileCoresPerSocket(2);
    facts.setSystemProfileSockets(2);

    facts.setSyspurposeUnits(unit);

    NormalizedFacts normalized = normalizer.normalize(facts, hypervisorData());
    assertEquals(sockets, normalized.getSockets().longValue());
    assertEquals(cores, normalized.getCores().longValue());
  }

  @Test
  void testSyspurposeUnitsUnspecified() {
    InventoryHostFacts facts = createBaseHost("A1", "O1");
    facts.setSystemProfileCoresPerSocket(2);
    facts.setSystemProfileSockets(2);

    NormalizedFacts normalized = normalizer.normalize(facts, hypervisorData());
    assertEquals(2, normalized.getSockets().longValue());
    assertEquals(4, normalized.getCores().longValue());
  }

  @Test
  void classifyAsVirtualIfSatelliteReportedHypervisorUuidButNotInfrastructureType() {
    InventoryHostFacts facts = createBaseHost("A1", "O1");
    facts.setSatelliteHypervisorUuid("SAT_HYPERVISOR");
    assertFalse(facts.isVirtual());
    assertNull(facts.getSystemProfileInfrastructureType());

    assertTrue(normalizer.normalize(facts, hypervisorData()).isVirtual());
  }

  @Test
  void testSatelliteSyspurposeHandled() {
    InventoryHostFacts facts = createBaseHost("A1", "O1");
    facts.setSatelliteRole("Red Hat Enterprise Linux Server");
    facts.setSatelliteSla("Premium");
    facts.setSatelliteUsage("Production");
    NormalizedFacts normalized = normalizer.normalize(facts, hypervisorData());
    assertThat(normalized.getProducts(), Matchers.contains("RHEL for x86"));
    assertEquals(ServiceLevel.PREMIUM, normalized.getSla());
    assertEquals(Usage.PRODUCTION, normalized.getUsage());
  }

  @Test
  void testRhsmFactsOverrideSatellite() {
    InventoryHostFacts facts = createBaseHost("A1", "O1");
    facts.setSatelliteRole("Red Hat Enterprise Linux Server");
    facts.setSatelliteSla("Premium");
    facts.setSatelliteUsage("Production");
    facts.setSyspurposeRole("Red Hat Enterprise Linux Workstation");
    facts.setSyspurposeSla("Standard");
    facts.setSyspurposeUsage("Development/Test");
    NormalizedFacts normalized = normalizer.normalize(facts, hypervisorData());
    assertThat(
        normalized.getProducts(),
        Matchers.containsInAnyOrder("RHEL Workstation", "RHEL " + "for x86"));
    assertEquals(ServiceLevel.STANDARD, normalized.getSla());
    assertEquals(Usage.DEVELOPMENT_TEST, normalized.getUsage());
  }

  @Test
  void testCalculationOfVirtualCPU() {
    InventoryHostFacts facts = createBaseHost("V1", "01");
    facts.setSystemProfileArch("x86_64");
    facts.setSystemProfileCoresPerSocket(16);
    facts.setSystemProfileSockets(1);
    facts.setSystemProfileInfrastructureType("virtual");
    NormalizedFacts normalizedFacts = normalizer.normalize(facts, hypervisorData());
    assertEquals(8, normalizedFacts.getCores());
    assertEquals(HostHardwareType.VIRTUALIZED, normalizedFacts.getHardwareType());
  }

  @Test
  void testCalculationOfVirtualCPURoundsUP() {
    InventoryHostFacts facts = createBaseHost("V1", "01");
    facts.setSystemProfileArch("x86_64");
    facts.setSystemProfileCoresPerSocket(9);
    facts.setSystemProfileSockets(1);
    facts.setSystemProfileInfrastructureType("virtual");
    NormalizedFacts normalizedFacts = normalizer.normalize(facts, hypervisorData());
    assertEquals(5, normalizedFacts.getCores());
    assertEquals(HostHardwareType.VIRTUALIZED, normalizedFacts.getHardwareType());
  }

  @Test
  void testCalculationOfRhsmVirtualCPUFacts() {
    InventoryHostFacts facts = createRhsmHost(Arrays.asList(1), null, clock.now());

    facts.setSystemProfileArch("x86_64");
    facts.setSystemProfileCoresPerSocket(7);
    facts.setSystemProfileSockets(1);
    facts.setSystemProfileInfrastructureType("virtual");
    NormalizedFacts normalizedFacts = normalizer.normalize(facts, hypervisorData());
    assertEquals(4, normalizedFacts.getCores());
    assertEquals(HostHardwareType.VIRTUALIZED, normalizedFacts.getHardwareType());
  }

  @Test
  void testCalculationOfMarketplaceMeasurements() {
    InventoryHostFacts facts = createRhsmHost(List.of(1), null, clock.now());
    facts.setMarketplace(true);
    facts.setSystemProfileCoresPerSocket(7);
    facts.setSystemProfileSockets(1);
    NormalizedFacts normalizedFacts = normalizer.normalize(facts, hypervisorData());
    assertTrue(normalizedFacts.isMarketplace());
    assertEquals(0, normalizedFacts.getCores());
    assertEquals(0, normalizedFacts.getSockets());
  }

  @Test
  void testNullSocketsMarketplaceDefaulting() {
    InventoryHostFacts facts = createRhsmHost(List.of(1), null, clock.now());
    facts.setMarketplace(true);
    facts.setSystemProfileSockets(null);
    facts.setSystemProfileCoresPerSocket(null);
    NormalizedFacts normalizedFacts = normalizer.normalize(facts, hypervisorData());
    assertTrue(normalizedFacts.isMarketplace());
    assertEquals(0, normalizedFacts.getCores());
    assertEquals(0, normalizedFacts.getSockets());
  }

  private void assertClassification(
      NormalizedFacts check, boolean isHypervisor, boolean isHypervisorUnknown, boolean isVirtual) {
    assertEquals(isHypervisor, check.isHypervisor());
    assertEquals(isHypervisorUnknown, check.isHypervisorUnknown());
    assertEquals(isVirtual, check.isVirtual());
  }
}
