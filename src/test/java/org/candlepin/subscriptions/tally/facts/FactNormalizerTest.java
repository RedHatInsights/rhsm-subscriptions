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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.times;

import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.HostHardwareType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.OrgHostsData;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StringUtils;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
@Import(TestClockConfiguration.class)
class FactNormalizerTest {

  @Autowired FactNormalizer normalizer;
  @Autowired ApplicationClock clock;
  @Autowired ApplicationProperties applicationProperties;
  @MockBean BuildProperties buildProperties;
  static MockedStatic<SubscriptionDefinition> subscriptionDefinitionMockedStatic;

  @BeforeEach
  void setup() {
    // restore default value
    applicationProperties.setUseCpuSystemFactsToAllProducts(true);
  }

  @BeforeAll
  static void beforeAll() {
    // functions as a spy
    subscriptionDefinitionMockedStatic =
        Mockito.mockStatic(
            SubscriptionDefinition.class,
            Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
  }

  @AfterAll
  static void afterAll() {
    subscriptionDefinitionMockedStatic.close();
  }

  @Test
  void testEmptyFactNormalization() {
    // Primarily checks the normalization process for situations that could
    // yield NPEs.
    assertDoesNotThrow(
        () -> normalizer.normalize(new InventoryHostFacts(), hypervisorData(), false));
  }

  @Test
  void testRhsmNormalization() {
    InventoryHostFacts rhsmHost = createRhsmHost(List.of(69), null, clock.now());
    rhsmHost.setSystemProfileCoresPerSocket(6);
    rhsmHost.setSystemProfileSockets(2);
    NormalizedFacts normalized = normalizer.normalize(rhsmHost, hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
    assertEquals(Integer.valueOf(12), normalized.getCores());
    assertEquals(Integer.valueOf(2), normalized.getSockets());
  }

  /** Runs through normalizeRhsmFacts */
  @Test
  void testNormalizationProductTagsMetered() {
    InventoryHostFacts rhsmHost = createRhsmHost(List.of(69), null, clock.now());
    rhsmHost.setSystemProfileCoresPerSocket(6);
    rhsmHost.setSystemProfileSockets(2);

    subscriptionDefinitionMockedStatic.clearInvocations();
    normalizer.normalize(rhsmHost, hypervisorData(), true);
    subscriptionDefinitionMockedStatic.verify(
        () ->
            SubscriptionDefinition.getAllProductTagsWithPaygEligibleByRoleOrEngIds(
                any(), any(), any(), anyBoolean()),
        times(1));
  }

  /** Runs through normalizeRhsmFacts */
  @Test
  void testNormalizationProductTagsNotMetered() {
    InventoryHostFacts rhsmHost = createRhsmHost(List.of(69), null, clock.now());
    rhsmHost.setSystemProfileCoresPerSocket(6);
    rhsmHost.setSystemProfileSockets(2);

    subscriptionDefinitionMockedStatic.clearInvocations();
    normalizer.normalize(rhsmHost, hypervisorData(), false);
    subscriptionDefinitionMockedStatic.verify(
        () ->
            SubscriptionDefinition.getAllProductTagsWithNonPaygEligibleByRoleOrEngIds(
                any(), any(), any(), anyBoolean()),
        times(1));
  }

  /** Runs through normalizeSystemProfileFacts */
  @Test
  void testNormalizationSystemProfileMetered() {
    InventoryHostFacts rhsmHost = createRhsmHost(List.of(69), null, clock.now());
    rhsmHost.setSystemProfileCoresPerSocket(6);
    rhsmHost.setSystemProfileSockets(2);
    // this will bypass the method from getting called in normalizeRhsmFacts
    rhsmHost.setSyncTimestamp(clock.startOfToday().minusMonths(1).toString());
    rhsmHost.setSystemProfileProductIds("prod1,prod2");

    subscriptionDefinitionMockedStatic.clearInvocations();
    normalizer.normalize(rhsmHost, hypervisorData(), true);
    subscriptionDefinitionMockedStatic.verify(
        () ->
            SubscriptionDefinition.getAllProductTagsWithPaygEligibleByRoleOrEngIds(
                any(), any(), any(), anyBoolean()),
        times(1));
  }

  /** Runs through normalizeSystemProfileFacts */
  @Test
  void testNormalizationSystemProfileNotMetered() {
    InventoryHostFacts rhsmHost = createRhsmHost(List.of(69), null, clock.now());
    rhsmHost.setSystemProfileCoresPerSocket(6);
    rhsmHost.setSystemProfileSockets(2);
    // this will bypass the method from getting called in normalizeRhsmFacts
    rhsmHost.setSyncTimestamp(clock.startOfToday().minusMonths(1).toString());
    rhsmHost.setSystemProfileProductIds("prod1,prod2");

    subscriptionDefinitionMockedStatic.clearInvocations();
    normalizer.normalize(rhsmHost, hypervisorData(), false);
    subscriptionDefinitionMockedStatic.verify(
        () ->
            SubscriptionDefinition.getAllProductTagsWithNonPaygEligibleByRoleOrEngIds(
                any(), any(), any(), anyBoolean()),
        times(1));
  }

  @Test
  void testQpcNormalization() {
    NormalizedFacts normalized =
        normalizer.normalize(createQpcHost("RHEL", "x86_64", clock.now()), hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
    assertNull(normalized.getCores());
    assertNull(normalized.getSockets());
  }

  @Test
  void testSystemProfileNormalization() {
    InventoryHostFacts host =
        createSystemProfileHost(Collections.singletonList(69), 4, 2, clock.now());
    NormalizedFacts normalized = normalizer.normalize(host, hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
    assertEquals(Integer.valueOf(8), normalized.getCores());
    assertEquals(Integer.valueOf(2), normalized.getSockets());
  }

  @Test
  void testNormalizeNonRhelProduct() {
    InventoryHostFacts rhsmHost = createRhsmHost(List.of(42), null, clock.now());
    rhsmHost.setSystemProfileCoresPerSocket(4);
    rhsmHost.setSystemProfileSockets(8);
    NormalizedFacts normalized = normalizer.normalize(rhsmHost, hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.empty());
    assertEquals(Integer.valueOf(32), normalized.getCores());
    assertEquals(Integer.valueOf(8), normalized.getSockets());
  }

  @Test
  void testSystemProfileNonRhelProduct() {
    NormalizedFacts normalized =
        normalizer.normalize(
            createSystemProfileHost(Collections.singletonList(42), 2, 4, clock.now()),
            hypervisorData(),
            false);
    assertThat(normalized.getProducts(), Matchers.empty());
    assertEquals(Integer.valueOf(8), normalized.getCores());
    assertEquals(Integer.valueOf(4), normalized.getSockets());
  }

  @Test
  void testSystemProfileInfrastructureType() {
    InventoryHostFacts baseFacts = createBaseHost("test-org");
    baseFacts.setSystemProfileInfrastructureType("virtual");
    baseFacts.setSyncTimestamp(clock.now().toString());

    NormalizedFacts normalized = normalizer.normalize(baseFacts, hypervisorData(), false);
    assertThat(normalized.isVirtual(), Matchers.is(true));
  }

  @Test
  void testNormalizeWhenProductsMissingFromFactsAndOnlyCoresAreSet() {
    InventoryHostFacts host = createRhsmHost((List<Integer>) null, null, clock.now());
    host.setSystemProfileCoresPerSocket(4);
    NormalizedFacts normalized = normalizer.normalize(host, hypervisorData(), false);
    assertNotNull(normalized.getProducts());
    assertThat(normalized.getProducts(), Matchers.empty());
    assertEquals(Integer.valueOf(4), normalized.getCores());
    assertNull(normalized.getSockets());
  }

  @Test
  void testNormalizeWhenProductsMissingFromFactsAndOnlySocketsAreSet() {
    InventoryHostFacts host = createRhsmHost((List<Integer>) null, null, clock.now());
    host.setSystemProfileSockets(8);
    NormalizedFacts normalized = normalizer.normalize(host, hypervisorData(), false);
    assertNotNull(normalized.getProducts());
    assertThat(normalized.getProducts(), Matchers.empty());
    assertNull(normalized.getCores());
    assertEquals(Integer.valueOf(8), normalized.getSockets());
  }

  @Test
  void testNormalizeWhenCoresAndSocketsMissingFromFacts() {
    NormalizedFacts normalized =
        normalizer.normalize(
            createRhsmHost(List.of(69), null, clock.now()), hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
    assertNull(normalized.getCores());
    assertNull(normalized.getSockets());
  }

  @Test
  void testIgnoresHostWhenLastSyncIsOutOfConfiguredThreshold() {
    OffsetDateTime lastSynced = clock.now().minusDays(2);
    InventoryHostFacts facts = createRhsmHost("O1", "69", null, lastSynced);

    NormalizedFacts normalized = normalizer.normalize(facts, hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.empty());
    assertNull(normalized.getCores());
  }

  @Test
  void testIncludesHostWhenLastSyncIsWithinTheConfiguredThreshold() {
    OffsetDateTime lastSynced = clock.now().minusDays(1);
    InventoryHostFacts facts = createRhsmHost("O1", "69", null, lastSynced);
    facts.setSystemProfileCoresPerSocket(2);
    facts.setSystemProfileSockets(2);
    NormalizedFacts normalized = normalizer.normalize(facts, hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
    assertEquals(Integer.valueOf(4), normalized.getCores());
  }

  @Test
  void testRhelFromQpcFacts() {
    NormalizedFacts normalized =
        normalizer.normalize(createQpcHost("RHEL", "x86_64", clock.now()), hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
  }

  @Test
  void testEmptyProductListWhenRhelNotPresent() {
    NormalizedFacts normalized =
        normalizer.normalize(createQpcHost("EAP", null, clock.now()), hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.empty());
  }

  @Test
  void testEmptyProductListWhenQpcProductsNotSet() {
    NormalizedFacts normalized =
        normalizer.normalize(createQpcHost(null, null, clock.now()), hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.empty());
  }

  @Test
  void testNullSocketsNormalizeToZero() {
    InventoryHostFacts host = createRhsmHost(Collections.emptyList(), null, clock.now());

    host.setSyspurposeUnits("Sockets");
    NormalizedFacts normalizedHost = normalizer.normalize(host, hypervisorData(), false);

    assertEquals(0, normalizedHost.getSockets().intValue());
  }

  @Test
  void testDetectsMultipleProductsBasedOnProductId() {
    NormalizedFacts normalized =
        normalizer.normalize(
            createRhsmHost(List.of(69, 419, 72), null, clock.now()), hypervisorData(), false);
    assertThat(
        normalized.getProducts(),
        Matchers.containsInAnyOrder("RHEL for x86", "RHEL for ARM", "RHEL for IBM z"));
  }

  @Test
  void testDetectsProductFromSyspurposeRole() {
    NormalizedFacts normalized =
        normalizer.normalize(
            createRhsmHost(Collections.emptyList(), "Red Hat Enterprise Linux Server", clock.now()),
            hypervisorData(),
            false);
    assertThat(normalized.getProducts(), Matchers.contains("RHEL for x86"));
  }

  @Test
  void testRhelUngroupedIfNoVariants() {
    NormalizedFacts normalized =
        normalizer.normalize(createQpcHost("RHEL", "x86_64", clock.now()), hypervisorData(), false);
    assertThat(
        normalized.getProducts(),
        Matchers.containsInAnyOrder("RHEL for x86", "RHEL", "RHEL Ungrouped"));
  }

  @Test
  void variantFromSyspurposeWinsIfMultipleVariants() {
    NormalizedFacts normalized =
        normalizer.normalize(
            createRhsmHost(List.of(9, 10), "Red Hat Enterprise Linux Server", clock.now()),
            hypervisorData(),
            false);
    assertThat(normalized.getProducts(), Matchers.contains("RHEL for x86"));
  }

  @Test
  void nonNumericProductIdIgnored() {
    NormalizedFacts normalized =
        normalizer.normalize(
            createRhsmHost("O1", "69,419,Foobar", "Red Hat Enterprise Linux Server", clock.now()),
            hypervisorData(),
            false);
    assertThat(
        normalized.getProducts(), Matchers.containsInAnyOrder("RHEL for x86", "RHEL " + "for ARM"));
  }

  @Test
  void testNormalizationDiscardsRHELWhenSatelliteExists() {
    InventoryHostFacts host = createRhsmHost(Collections.singletonList(250), null, clock.now());
    host.setSystemProfileCoresPerSocket(6);
    host.setSystemProfileSockets(2);
    NormalizedFacts normalized = normalizer.normalize(host, hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.contains("Satellite Server"));
    assertEquals(Integer.valueOf(12), normalized.getCores());
    assertEquals(Integer.valueOf(2), normalized.getSockets());
  }

  @ParameterizedTest
  @CsvSource(
      value = {"290,OpenShift Container Platform", "250,Satellite Server", "269,Satellite Capsule"})
  void testNormalizationDiscardsRHELWhenProductExists(int productId, String productName) {
    InventoryHostFacts host = createRhsmHost(List.of(productId), null, clock.now());
    host.setSystemProfileCoresPerSocket(6);
    host.setSystemProfileSockets(2);
    NormalizedFacts normalized = normalizer.normalize(host, hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.contains(productName));
    assertEquals(Integer.valueOf(12), normalized.getCores());
    assertEquals(Integer.valueOf(2), normalized.getSockets());
  }

  @Test
  void testModulo2SocketNormalizationForHypervisors() {
    InventoryHostFacts hypervisor = createHypervisor("O1", 69);
    hypervisor.setSystemProfileCoresPerSocket(4);
    hypervisor.setSystemProfileSockets(3);

    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(hypervisor.getSubscriptionManagerId(), null);

    NormalizedFacts normalized = normalizer.normalize(hypervisor, guestData, false);
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
    assertEquals(Integer.valueOf(12), normalized.getCores());
    assertEquals(Integer.valueOf(4), normalized.getSockets());
  }

  @Test
  void testModulo2SocketNormalizationForPhysicalHosts() {
    InventoryHostFacts host = createRhsmHost(List.of(69), null, clock.now());
    host.setSystemProfileCoresPerSocket(4);
    host.setSystemProfileSockets(3);
    NormalizedFacts normalized = normalizer.normalize(host, hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
    assertEquals(Integer.valueOf(12), normalized.getCores());
    assertEquals(Integer.valueOf(4), normalized.getSockets());
  }

  @Test
  void testNoModulo2SocketNormalizationForGuests() {
    InventoryHostFacts guestFacts = createGuest("hyp-id", "O1", 69);
    guestFacts.setSystemProfileCoresPerSocket(4);
    guestFacts.setSystemProfileSockets(3);
    assertTrue(guestFacts.isVirtual());

    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(guestFacts.getHypervisorUuid(), guestFacts.getHypervisorUuid());

    NormalizedFacts normalized = normalizer.normalize(guestFacts, guestData, false);
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
    assertEquals(Integer.valueOf(12), normalized.getCores());
    assertEquals(Integer.valueOf(3), normalized.getSockets());
  }

  @Test
  void testSocketSetToOneForVirtualRhelSystem() {
    InventoryHostFacts guestFacts = createGuest("hyp-id", "O1", 69);
    guestFacts.setHypervisorUuid(null);
    guestFacts.setSystemProfileCoresPerSocket(4);
    guestFacts.setSystemProfileSockets(3);
    OrgHostsData guestData = hypervisorData();

    NormalizedFacts normalized = normalizer.normalize(guestFacts, guestData, false);

    assertEquals(Integer.valueOf(1), normalized.getSockets());
  }

  @Test
  void testNoSocketNormalizationForVirtualNonRhelSystem() {
    InventoryHostFacts guestFacts = createGuest("hyp-id", "O1", 290);
    guestFacts.setHypervisorUuid(null);
    guestFacts.setSystemProfileCoresPerSocket(4);
    guestFacts.setSystemProfileSockets(3);
    OrgHostsData guestData = hypervisorData();

    NormalizedFacts normalized = normalizer.normalize(guestFacts, guestData, false);

    assertEquals(Integer.valueOf(3), normalized.getSockets());
  }

  @Test
  void testMarketplaceSystemHas0Sockets() {
    InventoryHostFacts baseFacts = createBaseHost("O1");
    baseFacts.setCloudProvider("aws");
    baseFacts.setMarketplace(true);
    baseFacts.setSystemProfileSockets(3);

    NormalizedFacts normalized = normalizer.normalize(baseFacts, hypervisorData(), false);
    assertEquals(0, normalized.getSockets());
  }

  @Test
  void testPhysicalNormalization() {
    InventoryHostFacts hostFacts = createBaseHost("O1");
    assertFalse(hostFacts.isVirtual());
    assertTrue(StringUtils.isEmpty(hostFacts.getHypervisorUuid()));
    assertTrue(StringUtils.isEmpty(hostFacts.getSatelliteHypervisorUuid()));

    NormalizedFacts normalized = normalizer.normalize(hostFacts, hypervisorData(), false);
    assertFalse(normalized.isHypervisor());
    assertFalse(normalized.isVirtual());
    assertEquals(HostHardwareType.PHYSICAL, normalized.getHardwareType());
  }

  @Test
  void testIsHypervisorNormalization() {
    InventoryHostFacts facts = createHypervisor("O1", 1);
    facts.setSystemProfileCoresPerSocket(4);
    facts.setSystemProfileSockets(3);
    assertFalse(facts.isVirtual());

    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(facts.getSubscriptionManagerId(), facts.getSubscriptionManagerId());

    NormalizedFacts normalized = normalizer.normalize(facts, guestData, false);
    assertTrue(normalized.isHypervisor());
    assertEquals(HostHardwareType.PHYSICAL, normalized.getHardwareType());
    assertEquals(12, normalized.getCores());
    assertEquals(4, normalized.getSockets());
    assertFalse(normalized.isVirtual());
  }

  @Test
  void testIsGuestNormalization() {
    InventoryHostFacts facts = createGuest("hyp-id", "O1", 1);
    facts.setSystemProfileCoresPerSocket(4);
    facts.setSystemProfileSockets(3);
    assertTrue(facts.isVirtual());

    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(facts.getHypervisorUuid(), facts.getHypervisorUuid());

    NormalizedFacts normalized = normalizer.normalize(facts, guestData, false);
    assertEquals(12, normalized.getCores());
    assertEquals(3, normalized.getSockets());
    assertTrue(normalized.isVirtual());
    assertFalse(normalized.isHypervisorUnknown());
    assertFalse(normalized.isHypervisor());

    facts = createGuest(null, "O1", 1);
    facts.setSystemProfileCoresPerSocket(4);
    facts.setSystemProfileSockets(3);

    assertTrue(facts.isVirtual());

    normalized = normalizer.normalize(facts, hypervisorData(), false);
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
    InventoryHostFacts baseFacts = createBaseHost("O1");
    baseFacts.setCloudProvider(expectedCloudProvider);

    NormalizedFacts normalized = normalizer.normalize(baseFacts, hypervisorData(), false);
    assertNotNull(normalized.getCloudProviderType());
    assertEquals(HardwareMeasurementType.AWS, normalized.getCloudProviderType());
  }

  @ParameterizedTest
  @ValueSource(strings = {"google", "gcp"})
  void testThatGoogleCloudProviderIsSet(String expectedCloudProvider) {
    InventoryHostFacts baseFacts = createBaseHost("O1");
    baseFacts.setCloudProvider(expectedCloudProvider);

    NormalizedFacts normalized = normalizer.normalize(baseFacts, hypervisorData(), false);
    assertNotNull(normalized.getCloudProviderType());
    assertEquals(HardwareMeasurementType.GOOGLE, normalized.getCloudProviderType());
  }

  @Test
  void testThatCloudProviderIsNotSetIfNull() {
    NormalizedFacts normalized =
        normalizer.normalize(createBaseHost("O1"), hypervisorData(), false);
    assertNull(normalized.getCloudProviderType());
  }

  @Test
  void testThatCloudProviderIsNotSetIfEmpty() {
    InventoryHostFacts baseFacts = createBaseHost("O1");
    baseFacts.setCloudProvider("");

    NormalizedFacts normalized = normalizer.normalize(baseFacts, hypervisorData(), false);
    assertNull(normalized.getCloudProviderType());
  }

  @Test
  void testThatUnsupportedCloudProviderIsNotSet() {
    String expectedCloudProvider = "unknown";
    InventoryHostFacts baseFacts = createBaseHost("O1");
    baseFacts.setCloudProvider(expectedCloudProvider);

    NormalizedFacts normalized = normalizer.normalize(baseFacts, hypervisorData(), false);
    assertNull(normalized.getCloudProviderType());
  }

  @Test
  void testPhysicalClassification() {
    InventoryHostFacts physical = createRhsmHost(List.of(12), null, clock.now());
    NormalizedFacts facts = normalizer.normalize(physical, hypervisorData(), false);
    assertClassification(facts, false, true, false);
  }

  @Test
  void testGuestWithMappedHypervisorClassification() {
    InventoryHostFacts guestWithMappedHypervisor = createGuest("mapped-hyp-id", "O1", 1);

    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(
        guestWithMappedHypervisor.getHypervisorUuid(),
        guestWithMappedHypervisor.getHypervisorUuid());

    NormalizedFacts facts = normalizer.normalize(guestWithMappedHypervisor, guestData, false);
    assertClassification(facts, false, false, true);
  }

  @Test
  void testGuestWithUnmappedHypervisorClassification() {
    InventoryHostFacts guestWithMappedHypervisor = createGuest("mapped-hyp-id", "O1", 1);

    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(guestWithMappedHypervisor.getHypervisorUuid(), null);

    NormalizedFacts facts = normalizer.normalize(guestWithMappedHypervisor, guestData, false);
    assertClassification(facts, false, true, true);
  }

  @Test
  void testGuestWithUnmappedHypervisorClassificationUsingSatelliteMapping() {
    InventoryHostFacts guestWithMappedHypervisor = createGuest("mapped-hyp-id", "O1", 1);
    guestWithMappedHypervisor.setHypervisorUuid(null);
    guestWithMappedHypervisor.setSatelliteHypervisorUuid("mapped-hyp-id");

    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(guestWithMappedHypervisor.getSatelliteHypervisorUuid(), null);

    NormalizedFacts facts = normalizer.normalize(guestWithMappedHypervisor, guestData, false);
    assertClassification(facts, false, true, true);
  }

  @Test
  void testGuestWithNullHypIdIsUnmappedHypervisorClassification() {
    InventoryHostFacts guestWithMappedHypervisor = createGuest(null, "O1", 1);

    NormalizedFacts facts =
        normalizer.normalize(guestWithMappedHypervisor, hypervisorData(), false);
    assertClassification(facts, false, true, true);
  }

  @Test
  void testHypervisorClassificationWhenMapped() {
    InventoryHostFacts hypervisor = createHypervisor("O1", 1);
    hypervisor.setSystemProfileCoresPerSocket(4);
    hypervisor.setSystemProfileSockets(3);
    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    NormalizedFacts facts = normalizer.normalize(hypervisor, guestData, false);
    assertClassification(facts, true, true, false);
  }

  @Test
  void testHypervisorClassificationWhenUnmapped() {
    InventoryHostFacts hypervisor = createHypervisor("O1", 1);
    OrgHostsData guestData = hypervisorData();
    guestData.addHostMapping(hypervisor.getSubscriptionManagerId(), null);

    NormalizedFacts facts = normalizer.normalize(hypervisor, guestData, false);
    assertClassification(facts, true, true, false);
  }

  @Test
  void testShouldPruneProductsThatAreExcludedInConfiguration() {
    // Where 290 is "OpenShift Container Platform", then we should remove:
    // - 72 because is "RHEL for IBM z" and is in the includedSubscriptions configuration of
    // OpenShift Container Platform.
    // - 90 because is "rhel-for-x86-rs" and same as above
    // 250 is "Satellite Server" that is not excluded, so it should not be pruned.
    InventoryHostFacts host = createRhsmHost(List.of(290, 72, 90, 250), null, clock.now());
    NormalizedFacts normalized = normalizer.normalize(host, hypervisorData(), false);
    assertThat(
        "Products after normalization: " + normalized.getProducts(),
        normalized.getProducts(),
        Matchers.contains("OpenShift Container Platform", "Satellite Server"));
  }

  static Stream<Arguments> syspurposeUnitsArgs() {
    return Stream.of(
        arguments("Sockets", 2, null), arguments("Cores/vCPU", null, 4), arguments("Foobar", 2, 4));
  }

  @ParameterizedTest
  @MethodSource("syspurposeUnitsArgs")
  void testSyspurposeUnits(String unit, Integer sockets, Integer cores) {
    InventoryHostFacts facts = createBaseHost("O1");
    facts.setSystemProfileCoresPerSocket(2);
    facts.setSystemProfileSockets(2);

    facts.setSyspurposeUnits(unit);

    NormalizedFacts normalized = normalizer.normalize(facts, hypervisorData(), false);
    assertEquals(sockets, normalized.getSockets());
    assertEquals(cores, normalized.getCores());
  }

  @Test
  void testSyspurposeUnitsUnspecified() {
    InventoryHostFacts facts = createBaseHost("O1");
    facts.setSystemProfileCoresPerSocket(2);
    facts.setSystemProfileSockets(2);

    NormalizedFacts normalized = normalizer.normalize(facts, hypervisorData(), false);
    assertEquals(2, normalized.getSockets().longValue());
    assertEquals(4, normalized.getCores().longValue());
  }

  @Test
  void classifyAsVirtualIfSatelliteReportedHypervisorUuidButNotInfrastructureType() {
    InventoryHostFacts facts = createBaseHost("O1");
    facts.setSatelliteHypervisorUuid("SAT_HYPERVISOR");
    assertFalse(facts.isVirtual());
    assertNull(facts.getSystemProfileInfrastructureType());

    assertTrue(normalizer.normalize(facts, hypervisorData(), false).isVirtual());
  }

  @Test
  void testSatelliteSyspurposeHandled() {
    InventoryHostFacts facts = createBaseHost("O1");
    facts.setSatelliteRole("Red Hat Enterprise Linux Server");
    facts.setSatelliteSla("Premium");
    facts.setSatelliteUsage("Production");
    NormalizedFacts normalized = normalizer.normalize(facts, hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.contains("RHEL for x86"));
    assertEquals(ServiceLevel.PREMIUM, normalized.getSla());
    assertEquals(Usage.PRODUCTION, normalized.getUsage());
  }

  @Test
  void testRhsmFactsOverrideSatellite() {
    InventoryHostFacts facts = createBaseHost("O1");
    facts.setSatelliteRole("Red Hat Enterprise Linux Server");
    facts.setSatelliteSla("Premium");
    facts.setSatelliteUsage("Production");
    facts.setSyspurposeRole("Red Hat Enterprise Linux Workstation");
    facts.setSyspurposeSla("Standard");
    facts.setSyspurposeUsage("Development/Test");
    NormalizedFacts normalized = normalizer.normalize(facts, hypervisorData(), false);
    assertThat(
        normalized.getProducts(),
        Matchers.containsInAnyOrder("RHEL Workstation", "RHEL " + "for x86"));
    assertEquals(ServiceLevel.STANDARD, normalized.getSla());
    assertEquals(Usage.DEVELOPMENT_TEST, normalized.getUsage());
  }

  @Test
  void testCalculationOfVirtualCPU() {
    InventoryHostFacts facts = createBaseHost("01");
    facts.setSystemProfileArch("x86_64");
    facts.setSystemProfileCoresPerSocket(16);
    facts.setSystemProfileSockets(1);
    facts.setSystemProfileInfrastructureType("virtual");
    NormalizedFacts normalizedFacts = normalizer.normalize(facts, hypervisorData(), false);
    assertEquals(8, normalizedFacts.getCores());
    assertEquals(HostHardwareType.VIRTUALIZED, normalizedFacts.getHardwareType());
  }

  @Test
  void testCalculationOfVirtualCPUUsingSystemProfileThreadsPerCore() {
    InventoryHostFacts facts = givenInventoryHostFactsForX86AndVirtual();
    facts.setSystemProfileThreadsPerCore(3); // this will overwrite the default value
    NormalizedFacts normalized = normalizer.normalize(facts, hypervisorData(), false);
    // cores changed from 8 to 6 because we're using 3 threads per core.
    assertEquals(Integer.valueOf(6), normalized.getCores());
  }

  @Test
  void testCalculationOfVirtualCPUUsingSystemProfileCPUs() {
    InventoryHostFacts facts = givenInventoryHostFactsForX86AndVirtual();
    facts.setSystemProfileCpus(
        24); // this will trigger the usage of the formula to calculate the threads per core
    NormalizedFacts normalized = normalizer.normalize(facts, hypervisorData(), false);
    // cores changed from 8 to 11
    assertEquals(Integer.valueOf(11), normalized.getCores());
  }

  @Test
  void testCalculationOfVirtualCPUShouldUseDefaultWhenDisableForAllProducts() {
    // disable the new formula for all products, but OpenShift
    applicationProperties.setUseCpuSystemFactsToAllProducts(false);
    InventoryHostFacts facts = givenInventoryHostFactsForX86AndVirtual();
    facts.setSystemProfileThreadsPerCore(3);
    NormalizedFacts normalized = normalizer.normalize(facts, hypervisorData(), false);
    // cores kept with 8 because the system profile cpus was not used
    assertEquals(Integer.valueOf(8), normalized.getCores());
  }

  @Test
  void testCalculationOfVirtualCPUShouldUseSystemFactsCpuWhenOpenShift() {
    // disable the new formula for all products, but OpenShift
    applicationProperties.setUseCpuSystemFactsToAllProducts(false);

    InventoryHostFacts facts = givenInventoryHostFactsForX86AndVirtual();
    facts.setSystemProfileProductIds("290"); // product ID for OpenShift
    facts.setSystemProfileThreadsPerCore(3);
    NormalizedFacts normalized = normalizer.normalize(facts, hypervisorData(), false);
    // cores changed from 8 to 6 because we're using 3 threads per core and product is OpenShift.
    assertEquals(Integer.valueOf(6), normalized.getCores());
    assertTrue(normalized.getProducts().contains("OpenShift Container Platform"));
  }

  @Test
  void testCalculationOfVirtualCPURoundsUP() {
    InventoryHostFacts facts = createBaseHost("01");
    facts.setSystemProfileArch("x86_64");
    facts.setSystemProfileCoresPerSocket(9);
    facts.setSystemProfileSockets(1);
    facts.setSystemProfileInfrastructureType("virtual");
    NormalizedFacts normalizedFacts = normalizer.normalize(facts, hypervisorData(), false);
    assertEquals(5, normalizedFacts.getCores());
    assertEquals(HostHardwareType.VIRTUALIZED, normalizedFacts.getHardwareType());
  }

  @Test
  void testCalculationOfRhsmVirtualCPUFacts() {
    InventoryHostFacts facts = createRhsmHost(List.of(1), null, clock.now());

    facts.setSystemProfileArch("x86_64");
    facts.setSystemProfileCoresPerSocket(7);
    facts.setSystemProfileSockets(1);
    facts.setSystemProfileInfrastructureType("virtual");
    NormalizedFacts normalizedFacts = normalizer.normalize(facts, hypervisorData(), false);
    assertEquals(4, normalizedFacts.getCores());
    assertEquals(HostHardwareType.VIRTUALIZED, normalizedFacts.getHardwareType());
  }

  @Test
  void testCalculationOfMarketplaceMeasurements() {
    InventoryHostFacts facts = createRhsmHost(List.of(1), null, clock.now());
    facts.setMarketplace(true);
    facts.setSystemProfileCoresPerSocket(7);
    facts.setSystemProfileSockets(1);
    NormalizedFacts normalizedFacts = normalizer.normalize(facts, hypervisorData(), false);
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
    NormalizedFacts normalizedFacts = normalizer.normalize(facts, hypervisorData(), false);
    assertTrue(normalizedFacts.isMarketplace());
    assertEquals(0, normalizedFacts.getCores());
    assertEquals(0, normalizedFacts.getSockets());
  }

  @ParameterizedTest
  @ValueSource(strings = {"x86_64", "i386", "i686"})
  void testQpcSystemArchSetRhelForX86Product(String arch) {
    NormalizedFacts normalized =
        normalizer.normalize(createQpcHost("RHEL", arch, clock.now()), hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
  }

  @Test
  void testQpcSystemArchSetRhelForArm() {
    NormalizedFacts normalized =
        normalizer.normalize(
            createQpcHost("RHEL", "aarch64", clock.now()), hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for ARM"));
  }

  @Test
  void testQpcSystemArchSetRhelForIbmPower() {
    NormalizedFacts normalized =
        normalizer.normalize(
            createQpcHost("RHEL", "ppc64le", clock.now()), hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for IBM Power"));
  }

  @Test
  void testQpcProductIdFromEngId() {
    var host = createQpcHost("RHEL", "Test", clock.now());
    host.setSystemProfileProductIds("69");
    NormalizedFacts normalized = normalizer.normalize(host, hypervisorData(), false);
    assertThat(normalized.getProducts(), Matchers.hasItem("RHEL for x86"));
  }

  private InventoryHostFacts givenInventoryHostFactsForX86AndVirtual() {
    InventoryHostFacts facts = createBaseHost("01");
    facts.setSystemProfileArch("x86_64");
    facts.setSystemProfileCoresPerSocket(16);
    facts.setSystemProfileSockets(1);
    facts.setSystemProfileInfrastructureType("virtual");
    return facts;
  }

  private void assertClassification(
      NormalizedFacts check, boolean isHypervisor, boolean isHypervisorUnknown, boolean isVirtual) {
    assertEquals(isHypervisor, check.isHypervisor());
    assertEquals(isHypervisorUnknown, check.isHypervisorUnknown());
    assertEquals(isVirtual, check.isVirtual());
  }

  private OrgHostsData hypervisorData() {
    return new OrgHostsData("Dummy Org");
  }
}
