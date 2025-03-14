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
package com.redhat.swatch.hbi.events.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.redhat.swatch.common.model.HardwareMeasurementType;
import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostFacts;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SatelliteFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SystemProfileFacts;
import com.redhat.swatch.hbi.events.services.HypervisorRelationshipService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event.CloudProvider;
import org.candlepin.subscriptions.json.Event.HardwareType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactNormalizerTest {

  private ApplicationClock clock;
  private FactNormalizer normalizer;
  @Mock private HypervisorRelationshipService hypervisorRelationshipService;

  @BeforeEach
  void setUp() {
    ApplicationConfiguration config = new ApplicationConfiguration();
    config.setHostLastSyncThreshold(Duration.ofHours(24));
    clock = config.applicationClock();
    normalizer = new FactNormalizer(clock, config, hypervisorRelationshipService);
  }

  // Basic facts are facts that require no logic when deciding the value.
  // They are either set or are null.
  @Test
  void testNormalizationOfBasicFacts() {
    Host host = new Host(hbiHost());
    NormalizedFacts normalizedFacts = normalizer.normalize(host);
    assertEquals(host.getHbiHost().getOrgId(), normalizedFacts.getOrgId());
    assertEquals(host.getHbiHost().getId().toString(), normalizedFacts.getInventoryId());
    assertEquals(host.getHbiHost().getInsightsId(), normalizedFacts.getInsightsId());
    assertEquals(
        host.getHbiHost().getSubscriptionManagerId(), normalizedFacts.getSubscriptionManagerId());
    assertEquals(host.getHbiHost().getDisplayName(), normalizedFacts.getDisplayName());
    assertEquals(clock.startOfToday().minusHours(1).toString(), normalizedFacts.getSyncTimestamp());
  }

  static Stream<Arguments> instanceIdParams() {
    UUID inventoryId = UUID.randomUUID();
    String providerId = UUID.randomUUID().toString();
    return Stream.of(
        Arguments.of(null, null, null),
        Arguments.of(inventoryId, null, inventoryId.toString()),
        Arguments.of(null, providerId, providerId),
        // Prefers provider ID over the inventory ID.
        Arguments.of(inventoryId, providerId, providerId));
  }

  @ParameterizedTest
  @MethodSource("instanceIdParams")
  void testInstanceIdNormalization(UUID inventoryId, String providerId, String expectedInstanceId) {
    HbiHost hbiHost = hbiHost();
    hbiHost.setId(inventoryId);
    hbiHost.setProviderId(providerId);
    assertEquals(expectedInstanceId, normalizer.normalize(new Host(hbiHost)).getInstanceId());
  }

  static Stream<Arguments> cloudProviderParams() {
    return Stream.of(
        Arguments.of("aws", HardwareMeasurementType.AWS, CloudProvider.AWS),
        Arguments.of("AwS", HardwareMeasurementType.AWS, CloudProvider.AWS),
        Arguments.of("Not Known", null, null),
        Arguments.of(null, null, null));
  }

  @ParameterizedTest
  @MethodSource("cloudProviderParams")
  void testValidCloudProviderNormalization(
      String cloudProviderFact,
      HardwareMeasurementType expectedType,
      CloudProvider expectedProvider) {
    HbiHost hbiHost = hbiHost();
    hbiHost.getSystemProfile().put(SystemProfileFacts.CLOUD_PROVIDER_FACT, cloudProviderFact);

    Host host = new Host(hbiHost);
    NormalizedFacts normalizedFacts = normalizer.normalize(host);
    assertEquals(expectedType, normalizedFacts.getCloudProviderType());
    assertEquals(expectedProvider, normalizedFacts.getCloudProvider());
  }

  static Stream<Arguments> hypervisorUuidParams() {
    String satelliteUuid = UUID.randomUUID().toString();
    String systemProfileUuid = UUID.randomUUID().toString();
    return Stream.of(
        Arguments.of(null, null, null),
        Arguments.of(satelliteUuid, null, satelliteUuid),
        Arguments.of(null, systemProfileUuid, systemProfileUuid),
        // Prefer satellite hypervisorUuid if both exist.
        Arguments.of(satelliteUuid, systemProfileUuid, satelliteUuid));
  }

  @ParameterizedTest
  @MethodSource("hypervisorUuidParams")
  void testHypervisorUuidNormalization(
      String satelliteHypervisorUuid,
      String systemProfileHypervisorUuid,
      String expectedHypervisorUuid) {
    HbiHost hbiHost = hbiHost();
    if (Objects.nonNull(systemProfileHypervisorUuid)) {
      hbiHost
          .getSystemProfile()
          .put(SystemProfileFacts.HYPERVISOR_UUID_FACT, systemProfileHypervisorUuid);
    }

    if (Objects.nonNull(satelliteHypervisorUuid)) {
      HbiHostFacts satelliteFacts = satelliteFacts();
      satelliteFacts.getFacts().put(SatelliteFacts.VIRTUAL_HOST_UUID_FACT, satelliteHypervisorUuid);
      hbiHost.setFacts(List.of(satelliteFacts));
    }
    assertEquals(
        expectedHypervisorUuid, normalizer.normalize(new Host(hbiHost)).getHypervisorUuid());
  }

  static Stream<Arguments> isHypervisorNormalizationParams() {
    return Stream.of(Arguments.of(true), Arguments.of(false));
  }

  @ParameterizedTest
  @MethodSource("isHypervisorNormalizationParams")
  void testIsHypervisorNormalization(boolean expectedHypervisor) {
    HbiHost hbiHost = hbiHost();
    when(hypervisorRelationshipService.isHypervisor(
            hbiHost.getOrgId(), hbiHost.getSubscriptionManagerId()))
        .thenReturn(expectedHypervisor);
    NormalizedFacts facts = normalizer.normalize(new Host(hbiHost));
    assertEquals(expectedHypervisor, facts.isHypervisor());
    assertEquals(hbiHost.getSubscriptionManagerId(), facts.getSubscriptionManagerId());
  }

  static Stream<Arguments> isUnmappedGuestNormalizationParams() {
    return Stream.of(Arguments.of(true), Arguments.of(false));
  }

  @ParameterizedTest
  @MethodSource("isUnmappedGuestNormalizationParams")
  void testIsUnmappedGuestNormalization(boolean isHypervisorKnown) {
    final String expectedHypervisorUuid = UUID.randomUUID().toString();
    HbiHost hbiHost = hbiHost();
    hbiHost.getSystemProfile().put(SystemProfileFacts.HYPERVISOR_UUID_FACT, expectedHypervisorUuid);
    hbiHost.getSystemProfile().put(SystemProfileFacts.INFRASTRUCTURE_TYPE_FACT, "virtual");
    when(hypervisorRelationshipService.isKnownHost(hbiHost.getOrgId(), expectedHypervisorUuid))
        .thenReturn(isHypervisorKnown);
    NormalizedFacts facts = normalizer.normalize(new Host(hbiHost));
    assertEquals(!isHypervisorKnown, facts.isUnmappedGuest());
    assertEquals(expectedHypervisorUuid, facts.getHypervisorUuid());
  }

  static Stream<Arguments> isVirtualParams() {
    return Stream.of(
        Arguments.of(null, null, null, false),
        Arguments.of(true, null, null, true),
        Arguments.of(null, "test_hypervisor_uuid", null, true),
        Arguments.of(null, null, "virtual", true),
        Arguments.of(null, null, "ViRtUaL", true),
        Arguments.of(null, null, "physical", false));
  }

  @ParameterizedTest
  @MethodSource("isVirtualParams")
  void testIsVirtualNormalization(
      Boolean rhsmIsVirtual,
      String satelliteHypervisorUuid,
      String infrastructureType,
      boolean expectedToBeVirtual) {

    List<HbiHostFacts> hbiHostFacts = new ArrayList<>();
    HbiHostFacts rhsmFacts = rhsmHbiFacts();
    rhsmFacts.getFacts().put(RhsmFacts.IS_VIRTUAL_FACT, rhsmIsVirtual);
    hbiHostFacts.add(rhsmFacts);

    HbiHostFacts satelliteFacts = satelliteFacts();
    satelliteFacts.getFacts().put(SatelliteFacts.VIRTUAL_HOST_UUID_FACT, satelliteHypervisorUuid);
    hbiHostFacts.add(satelliteFacts);

    HbiHost hbiHost = hbiHost();
    hbiHost.setFacts(hbiHostFacts);
    hbiHost.getSystemProfile().put(SystemProfileFacts.INFRASTRUCTURE_TYPE_FACT, infrastructureType);

    assertEquals(expectedToBeVirtual, normalizer.normalize(new Host(hbiHost)).isVirtual());
  }

  static Stream<Arguments> hardwareTypeParams() {
    return Stream.of(
        Arguments.of(null, null, HardwareType.PHYSICAL),
        Arguments.of(null, "physical_infra", HardwareType.PHYSICAL),
        // Infrastructure type 'virtual' will force the system to be virtual
        Arguments.of(null, "virtual", HardwareType.VIRTUAL),
        Arguments.of("aws", null, HardwareType.CLOUD),
        // Unsupported cloud provider
        Arguments.of("unsupported", null, HardwareType.PHYSICAL),
        Arguments.of("unsupported", "virtual", HardwareType.VIRTUAL),
        // Always prefer cloud before virtual.
        Arguments.of("aws", "virtual", HardwareType.CLOUD));
  }

  @ParameterizedTest
  @MethodSource("hardwareTypeParams")
  void testHardwareTypeNormalization(
      String sysProfileCloudProvider,
      String sysProfileInfraType,
      HardwareType expectedHardwareType) {
    HbiHost hbiHost = hbiHost();
    hbiHost.getSystemProfile().put(SystemProfileFacts.CLOUD_PROVIDER_FACT, sysProfileCloudProvider);
    hbiHost
        .getSystemProfile()
        .put(SystemProfileFacts.INFRASTRUCTURE_TYPE_FACT, sysProfileInfraType);
    assertEquals(expectedHardwareType, normalizer.normalize(new Host(hbiHost)).getHardwareType());
  }

  static Stream<Arguments> skipRhsmFactsParams() {
    return Stream.of(
        // Null lastSyncTimestamp will not skip RHSM facts.
        Arguments.of(null, Usage.PRODUCTION, ServiceLevel.PREMIUM, Set.of("RHEL for x86")),
        // Empty lastSyncTimestamp will not skip RHSM facts.
        Arguments.of("", Usage.PRODUCTION, ServiceLevel.PREMIUM, Set.of("RHEL for x86")),
        // Will not skip RHSM facts for a registered host.
        Arguments.of(
            OffsetDateTime.now().toString(),
            Usage.PRODUCTION,
            ServiceLevel.PREMIUM,
            Set.of("RHEL for x86")),
        // Will skip RHSM facts when for an unregistered host (lastSync is before today - configured
        // threshold). In this case, we expect the satellite configured values.
        Arguments.of(
            OffsetDateTime.now().minusMonths(1).toString(),
            Usage.DEVELOPMENT_TEST,
            ServiceLevel.STANDARD,
            Set.of()));
  }

  @ParameterizedTest
  @MethodSource("skipRhsmFactsParams")
  void testSkipRhsmFacts(
      String lastSyncTimestamp,
      Usage expectedUsage,
      ServiceLevel expectedSla,
      Set<String> expectedProductTags) {
    // RHSM usage/sla facts are preferred over satellite usage when they are not skipped.
    List<HbiHostFacts> hbiHostFacts = new ArrayList<>();
    HbiHostFacts rhsmFacts = rhsmHbiFacts();
    rhsmFacts.getFacts().put(RhsmFacts.SYNC_TIMESTAMP_FACT, lastSyncTimestamp);
    rhsmFacts.getFacts().put(RhsmFacts.USAGE_FACT, Usage.PRODUCTION.getValue());
    rhsmFacts.getFacts().put(RhsmFacts.SLA_FACT, ServiceLevel.PREMIUM.getValue());
    rhsmFacts.getFacts().put(RhsmFacts.PRODUCT_IDS_FACT, List.of("69"));

    hbiHostFacts.add(rhsmFacts);

    HbiHostFacts satelliteFacts = satelliteFacts();
    satelliteFacts.getFacts().put(SatelliteFacts.USAGE_FACT, Usage.DEVELOPMENT_TEST.getValue());
    satelliteFacts.getFacts().put(SatelliteFacts.SLA_FACT, ServiceLevel.STANDARD.getValue());
    hbiHostFacts.add(satelliteFacts);

    HbiHost hbiHost = hbiHost();
    hbiHost.setFacts(hbiHostFacts);

    NormalizedFacts normalizedFacts = normalizer.normalize(new Host(hbiHost));
    assertEquals(expectedUsage.getValue(), normalizedFacts.getUsage());
    assertEquals(expectedSla.getValue(), normalizedFacts.getSla());
    assertEquals(expectedProductTags, normalizedFacts.getProductTags());
  }

  static Stream<Arguments> usageParams() {
    return Stream.of(
        Arguments.of(OffsetDateTime.now().toString(), null, null, null),
        Arguments.of(OffsetDateTime.now().toString(), Usage.PRODUCTION, null, Usage.PRODUCTION),
        // Registered host -- use satellite usage fact if RHSM fact does not exist.
        Arguments.of(OffsetDateTime.now().toString(), null, Usage.PRODUCTION, Usage.PRODUCTION),
        // Registered host -- use RHSM usage over satellite, if RHSM facts exist.
        Arguments.of(
            OffsetDateTime.now().toString(),
            Usage.PRODUCTION,
            Usage.DEVELOPMENT_TEST,
            Usage.PRODUCTION),
        // Unregistered host -- use satellite usage
        Arguments.of(
            OffsetDateTime.now().minusMonths(1).toString(),
            Usage.PRODUCTION,
            Usage.DEVELOPMENT_TEST,
            Usage.DEVELOPMENT_TEST));
  }

  @ParameterizedTest
  @MethodSource("usageParams")
  void testUsageNormalization(
      String lastSyncTimestamp, Usage rhsmUsage, Usage satelliteUsage, Usage expectedUsage) {
    List<HbiHostFacts> hbiHostFacts = new ArrayList<>();
    HbiHostFacts rhsmFacts = rhsmHbiFacts();
    rhsmFacts.getFacts().put(RhsmFacts.SYNC_TIMESTAMP_FACT, lastSyncTimestamp);
    if (Objects.nonNull(rhsmUsage)) {
      rhsmFacts.getFacts().put(RhsmFacts.USAGE_FACT, rhsmUsage.getValue());
    }
    hbiHostFacts.add(rhsmFacts);

    HbiHostFacts satelliteFacts = satelliteFacts();
    if (Objects.nonNull(satelliteUsage)) {
      satelliteFacts.getFacts().put(SatelliteFacts.USAGE_FACT, satelliteUsage.getValue());
    }
    hbiHostFacts.add(satelliteFacts);

    HbiHost hbiHost = hbiHost();
    hbiHost.setFacts(hbiHostFacts);
    assertEquals(
        Objects.nonNull(expectedUsage) ? expectedUsage.getValue() : null,
        normalizer.normalize(new Host(hbiHost)).getUsage());
  }

  static Stream<Arguments> slaParams() {
    return Stream.of(
        Arguments.of(OffsetDateTime.now().toString(), null, null, null),
        Arguments.of(
            OffsetDateTime.now().toString(), ServiceLevel.PREMIUM, null, ServiceLevel.PREMIUM),
        // Registered host -- use satellite sla fact if RHSM fact does not exist.
        Arguments.of(
            OffsetDateTime.now().toString(), null, ServiceLevel.PREMIUM, ServiceLevel.PREMIUM),
        // Registered host -- use RHSM sla over satellite, if RHSM fact exists.
        Arguments.of(
            OffsetDateTime.now().toString(),
            ServiceLevel.PREMIUM,
            ServiceLevel.STANDARD,
            ServiceLevel.PREMIUM),
        // Unregistered host -- use satellite usage
        Arguments.of(
            OffsetDateTime.now().minusMonths(1).toString(),
            ServiceLevel.PREMIUM,
            ServiceLevel.STANDARD,
            ServiceLevel.STANDARD));
  }

  @ParameterizedTest
  @MethodSource("slaParams")
  void testServiceLevelNormalization(
      String lastSyncTimestamp,
      ServiceLevel rhsmSla,
      ServiceLevel satelliteSla,
      ServiceLevel expectedSla) {
    List<HbiHostFacts> hbiHostFacts = new ArrayList<>();
    HbiHostFacts rhsmFacts = rhsmHbiFacts();
    rhsmFacts.getFacts().put(RhsmFacts.SYNC_TIMESTAMP_FACT, lastSyncTimestamp);
    if (Objects.nonNull(rhsmSla)) {
      rhsmFacts.getFacts().put(RhsmFacts.SLA_FACT, rhsmSla.getValue());
    }
    hbiHostFacts.add(rhsmFacts);

    HbiHostFacts satelliteFacts = satelliteFacts();
    if (Objects.nonNull(satelliteSla)) {
      satelliteFacts.getFacts().put(SatelliteFacts.SLA_FACT, satelliteSla.getValue());
    }
    hbiHostFacts.add(satelliteFacts);

    HbiHost hbiHost = hbiHost();
    hbiHost.setFacts(hbiHostFacts);
    assertEquals(
        Objects.nonNull(expectedSla) ? expectedSla.getValue() : null,
        normalizer.normalize(new Host(hbiHost)).getSla());
  }

  @Test
  void testProductIdsAndProductTagsAreNormalized() {
    // NOTE: The logic around product normalization is tested in ProductNormalizerTest.
    //       This test only ensures that products are being normalized and included in
    //       the normalized facts.
    List<HbiHostFacts> hbiHostFacts = new ArrayList<>();
    HbiHostFacts rhsmFacts = rhsmHbiFacts();
    rhsmFacts.getFacts().put(RhsmFacts.SYNC_TIMESTAMP_FACT, clock.now().toString());
    rhsmFacts.getFacts().put(RhsmFacts.PRODUCT_IDS_FACT, List.of("69"));
    hbiHostFacts.add(rhsmFacts);

    HbiHost hbiHost = hbiHost();
    hbiHost.setFacts(hbiHostFacts);

    NormalizedFacts normalizedFacts = normalizer.normalize(new Host(hbiHost));
    assertEquals(Set.of("RHEL for x86"), normalizedFacts.getProductTags());
    assertEquals(Set.of("69"), normalizedFacts.getProductIds());
  }

  @Test
  void testIs3rdPartyMigrated() {
    // Empty system profile facts.
    HbiHost hbiHost = hbiHost();
    assertFalse(normalizer.normalize(new Host(hbiHost)).is3rdPartyMigrated());

    hbiHost
        .getSystemProfile()
        .put(
            SystemProfileFacts.CONVERSIONS_FACT,
            Map.of(SystemProfileFacts.CONVERSIONS_ACTIVITY, false));
    assertFalse(normalizer.normalize(new Host(hbiHost)).is3rdPartyMigrated());

    hbiHost
        .getSystemProfile()
        .put(
            SystemProfileFacts.CONVERSIONS_FACT,
            Map.of(SystemProfileFacts.CONVERSIONS_ACTIVITY, true));
    assertTrue(normalizer.normalize(new Host(hbiHost)).is3rdPartyMigrated());
  }

  static Stream<Arguments> lastSeenParams() {
    OffsetDateTime lastUpdatedDate = OffsetDateTime.now();
    return Stream.of(
        Arguments.of(null, null),
        Arguments.of("", null),
        Arguments.of(lastUpdatedDate.toString(), lastUpdatedDate));
  }

  @ParameterizedTest
  @MethodSource("lastSeenParams")
  void testLastSeenNormalization(String hostUpdatedDate, OffsetDateTime expectedLastSeen) {
    HbiHost hbiHost = hbiHost();
    hbiHost.updated = Objects.nonNull(hostUpdatedDate) ? hostUpdatedDate : null;
    assertEquals(expectedLastSeen, normalizer.normalize(new Host(hbiHost)).getLastSeen());
  }

  private HbiHost hbiHost() {
    HbiHost hbiHost = new HbiHost();
    hbiHost.orgId = "12345678";
    hbiHost.id = UUID.randomUUID();
    hbiHost.insightsId = "insights_1234";
    hbiHost.subscriptionManagerId = "subscription_1234";
    hbiHost.displayName = "test_host";
    hbiHost.setSystemProfile(new HashMap<>());

    List<HbiHostFacts> facts = new ArrayList<>();
    facts.add(rhsmHbiFacts());
    hbiHost.setFacts(facts);

    return hbiHost;
  }

  private HbiHostFacts rhsmHbiFacts() {
    HbiHostFacts facts = new HbiHostFacts();
    facts.setNamespace(RhsmFacts.RHSM_FACTS_NAMESPACE);
    facts
        .getFacts()
        .put(RhsmFacts.SYNC_TIMESTAMP_FACT, clock.startOfToday().minusHours(1).toString());
    return facts;
  }

  private HbiHostFacts satelliteFacts() {
    HbiHostFacts facts = new HbiHostFacts();
    facts.setNamespace(SatelliteFacts.SATELLITE_FACTS_NAMESPACE);
    return facts;
  }
}
