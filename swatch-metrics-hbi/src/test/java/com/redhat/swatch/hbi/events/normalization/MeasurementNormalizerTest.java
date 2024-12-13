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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.redhat.swatch.common.model.HardwareMeasurementType;
import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SystemProfileFacts;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeasurementNormalizerTest {

  private ApplicationConfiguration appConfig;
  private MeasurementNormalizer measurementNormalizer;

  @BeforeEach
  void setup() {
    appConfig = new ApplicationConfiguration();
    appConfig.setUseCpuSystemFactsForAllProducts(false);
    measurementNormalizer = new MeasurementNormalizer(appConfig);
  }

  @Test
  void testNormalizeCores() {
    int systemProfileCorePerSocket = 6;
    int systemProfileSockets = 2;

    SystemProfileFacts systemProfileFacts =
        physicalSystemProfile(systemProfileSockets, systemProfileCorePerSocket);
    RhsmFacts rhsmFacts = rhsmFacts();
    NormalizedFacts normalizedFacts = hostFacts(UUID.randomUUID().toString(), false);
    NormalizedMeasurements normalized =
        measurementNormalizer.getMeasurements(
            normalizedFacts, systemProfileFacts, Optional.of(rhsmFacts), Set.of());
    // sockets * coresPerSocket
    assertEquals(12, normalized.getCores().orElse(null));
  }

  @Test
  void testNormalizeCoresWhenNotEnoughFacts() {
    int systemProfileSockets = 2;

    SystemProfileFacts systemProfileFacts = physicalSystemProfile(systemProfileSockets, null);
    RhsmFacts rhsmFacts = rhsmFacts();
    NormalizedFacts normalizedFacts = hostFacts(UUID.randomUUID().toString(), false);
    NormalizedMeasurements normalized =
        measurementNormalizer.getMeasurements(
            normalizedFacts, systemProfileFacts, Optional.of(rhsmFacts), Set.of());
    // Null sockets when cores can not be calculated.
    assertTrue(normalized.getCores().isEmpty());
  }

  @Test
  void testApplyx8664VirtualCpusAsCoresDefault() {
    // Must be VIRTUAL and have an arch of x86_64 (not OCP)
    int systemProfileCorePerSocket = 6;
    int systemProfileSockets = 2;

    SystemProfileFacts systemProfileFacts =
        virtualSystemProfile(
            "x86_64", systemProfileSockets, systemProfileCorePerSocket, null, null);
    RhsmFacts rhsmFacts = rhsmFacts();
    NormalizedFacts normalizedFacts = hostFacts(UUID.randomUUID().toString(), true);
    NormalizedMeasurements normalized =
        measurementNormalizer.getMeasurements(
            normalizedFacts, systemProfileFacts, Optional.of(rhsmFacts), Set.of());
    // Default threadsPerCore = 2
    // (sockets * coresPerSocket) / threadsPerCore
    assertEquals(6, normalized.getCores().orElse(null));
  }

  @Test
  void testApplyX86VirtualCpusAsCoresOcpUseThreadsPerCoreIfFactExists() {
    // Must be VIRTUAL and have an arch of x86_64 and have tag OCP.
    int systemProfileCorePerSocket = 6;
    int systemProfileSockets = 2;
    int threadsPerCore = 4;

    SystemProfileFacts systemProfileFacts =
        virtualSystemProfile(
            "x86_64", systemProfileSockets, systemProfileCorePerSocket, threadsPerCore, null);
    RhsmFacts rhsmFacts = rhsmFacts();
    NormalizedFacts normalizedFacts = hostFacts(UUID.randomUUID().toString(), true);
    NormalizedMeasurements normalized =
        measurementNormalizer.getMeasurements(
            normalizedFacts,
            systemProfileFacts,
            Optional.of(rhsmFacts),
            Set.of(MeasurementNormalizer.OPEN_SHIFT_CONTAINER_PLATFORM));
    // (sockets * coresPerSocket) / threadsPerCore
    // ceil(12 / 4) = 3
    assertEquals(3, normalized.getCores().orElse(null));
  }

  @Test
  void testApplyX86VirtualCpusAsCoresOcpCalculateThreadsPerCoreIfFactDoesNotExist() {
    // Must be VIRTUAL and have an arch of x86_64 and have tag OCP.
    // If threadsPerCore does not exist, but cpus are, calculate threadsPerCore as
    // cpus / (sockets * corePerSocket)
    int systemProfileCorePerSocket = 6;
    int systemProfileSockets = 2;
    int cpus = 4;

    SystemProfileFacts systemProfileFacts =
        virtualSystemProfile(
            "x86_64", systemProfileSockets, systemProfileCorePerSocket, null, cpus);
    RhsmFacts rhsmFacts = rhsmFacts();
    NormalizedFacts normalizedFacts = hostFacts(UUID.randomUUID().toString(), true);
    NormalizedMeasurements normalized =
        measurementNormalizer.getMeasurements(
            normalizedFacts,
            systemProfileFacts,
            Optional.of(rhsmFacts),
            Set.of(MeasurementNormalizer.OPEN_SHIFT_CONTAINER_PLATFORM));
    // threadsPerCore = cpus / (sockets * coresPerSocket) = 0.333333333
    // (sockets * coresPerSocket) / threadsPerCore
    // ceil(12 / 0.333333333) = 36
    assertEquals(36, normalized.getCores().orElse(null));
  }

  @Test
  void testZeroCoresWhenIsMarketplaceProfile() {
    SystemProfileFacts systemProfileFacts = marketPlaceProfile(2, 2);
    RhsmFacts rhsmFacts = rhsmFacts();
    NormalizedFacts normalizedFacts = hostFacts(UUID.randomUUID().toString(), true);
    NormalizedMeasurements normalized =
        measurementNormalizer.getMeasurements(
            normalizedFacts, systemProfileFacts, Optional.of(rhsmFacts), Set.of());
    // Expect 0 cores for a marketplace system profile
    assertEquals(0, normalized.getCores().orElse(null));
  }

  @Test
  void testApplyVirtualCpusAsCoresLikeOcpForAllGuestsWhenEnabled() {
    // Must be VIRTUAL and have an arch of x86_64, no OCP, and the app must be
    // configured to use cpu system facts for all products.
    int systemProfileCorePerSocket = 6;
    int systemProfileSockets = 2;
    int threadsPerCore = 4;

    appConfig.setUseCpuSystemFactsForAllProducts(true);

    SystemProfileFacts systemProfileFacts =
        virtualSystemProfile(
            "x86_64", systemProfileSockets, systemProfileCorePerSocket, threadsPerCore, null);
    RhsmFacts rhsmFacts = rhsmFacts();
    NormalizedFacts normalizedFacts = hostFacts(UUID.randomUUID().toString(), true);
    NormalizedMeasurements normalized =
        measurementNormalizer.getMeasurements(
            normalizedFacts, systemProfileFacts, Optional.of(rhsmFacts), Set.of());
    // (sockets * coresPerSocket) / threadsPerCore
    // ceil(12 / 4) = 3
    assertEquals(3, normalized.getCores().orElse(null));
  }

  @Test
  void testNormalizeSocketsWhenFactDoesNotExist() {
    SystemProfileFacts systemProfileFacts = physicalSystemProfile(null, null);
    RhsmFacts rhsmFacts = rhsmFacts();
    NormalizedFacts normalizedFacts = hostFacts(UUID.randomUUID().toString(), false);
    NormalizedMeasurements normalized =
        measurementNormalizer.getMeasurements(
            normalizedFacts, systemProfileFacts, Optional.of(rhsmFacts), Set.of());
    assertTrue(normalized.getSockets().isEmpty());
  }

  static Stream<Arguments> physicalSocketsParams() {
    return Stream.of(
        Arguments.of(2, 2, false),
        // Modulo-2 rounding applied for physical systems.
        Arguments.of(3, 4, false),
        Arguments.of(2, 2, true),
        // Modulo-2 rounding applied for hypervisors.
        Arguments.of(3, 4, true));
  }

  @ParameterizedTest
  @MethodSource("physicalSocketsParams")
  void testNormalizeSocketsForPhysicalAndHypervisorSystemProfile(
      int systemProfileSockets, int expectedSockets, boolean isHypervisor) {
    RhsmFacts rhsmFacts = rhsmFacts();
    String subscriptionManagerId = UUID.randomUUID().toString();

    SystemProfileFacts systemProfileFacts;
    NormalizedFacts normalizedFacts;
    if (isHypervisor) {
      // Although a virtual hypervisor isn't typical, testing this way allows for a stronger
      // and more isolated test since it triggers the non-physical hyperlogic.
      systemProfileFacts = virtualSystemProfile("x86_64", systemProfileSockets, 4, null, null);

      normalizedFacts = hypervisorFacts(subscriptionManagerId);
    } else {
      systemProfileFacts = physicalSystemProfile(systemProfileSockets, null);
      normalizedFacts = hostFacts(subscriptionManagerId, false);
    }

    NormalizedMeasurements normalized =
        measurementNormalizer.getMeasurements(
            normalizedFacts, systemProfileFacts, Optional.of(rhsmFacts), Set.of());
    assertEquals(expectedSockets, normalized.getSockets().orElse(null));
  }

  @Test
  void testCloudProviderGuestsAccountForSingleSocket() {
    int systemProfileSockets = 2;
    int systemProfileCorePerSocket = 4;
    String subscriptionManagerId = UUID.randomUUID().toString();
    String hypervisorUuid = UUID.randomUUID().toString();

    SystemProfileFacts systemProfileFacts =
        virtualSystemProfile(
            "x86_64", systemProfileSockets, systemProfileCorePerSocket, null, null);
    NormalizedFacts normalizedFacts =
        virtualWithCloudProviderHostFacts(subscriptionManagerId, hypervisorUuid);

    NormalizedMeasurements normalized =
        measurementNormalizer.getMeasurements(
            normalizedFacts, systemProfileFacts, Optional.of(rhsmFacts()), Set.of());
    assertEquals(1, normalized.getSockets().orElse(null));
  }

  static Stream<Arguments> socketsForUnmappedGuest() {
    return Stream.of(
        // Unmapped virtual RHEL guests account for a single socket
        Arguments.of(2, Set.of("RHEL for x86"), 1),
        // Any tag starting with RHEL or rhel is considered RHEL
        Arguments.of(2, Set.of("rhel for x86"), 1),
        // All other unmapped virtual guests account for system profile sockets.
        Arguments.of(2, Set.of("OpenShift Container Platform"), 2),
        // Does not START WITH RHEL or rhel
        Arguments.of(2, Set.of("Not considered RHEL"), 2),
        Arguments.of(2, Set.of("Not considered rhel"), 2));
  }

  @ParameterizedTest
  @MethodSource("socketsForUnmappedGuest")
  void testAppliesSocketsForUnmappedGuests(
      int systemProfileSockets, Set<String> productTags, int expectedSockets) {

    String subscriptionManagerId = UUID.randomUUID().toString();
    String hypervisorUuid = UUID.randomUUID().toString();

    SystemProfileFacts systemProfileFacts =
        virtualSystemProfile("x86_64", systemProfileSockets, 4, null, null);

    NormalizedFacts normalizedFacts =
        unmappedVirtualHostFacts(subscriptionManagerId, hypervisorUuid);

    NormalizedMeasurements normalized =
        measurementNormalizer.getMeasurements(
            normalizedFacts, systemProfileFacts, Optional.of(rhsmFacts()), productTags);
    assertEquals(expectedSockets, normalized.getSockets().orElse(null));
  }

  @Test
  void testZeroSocketsWhenIsMarketplaceProfile() {
    int systemProfileSockets = 2;
    int systemProfileCorePerSocket = 4;
    String subscriptionManagerId = UUID.randomUUID().toString();

    SystemProfileFacts systemProfileFacts =
        marketPlaceProfile(systemProfileSockets, systemProfileCorePerSocket);
    NormalizedFacts normalizedFacts = hostFacts(subscriptionManagerId, false);

    NormalizedMeasurements normalized =
        measurementNormalizer.getMeasurements(
            normalizedFacts, systemProfileFacts, Optional.of(rhsmFacts()), Set.of());
    assertEquals(0, normalized.getSockets().orElse(null));
  }

  static Stream<Arguments> systemPurposeUnitsArgs() {
    return Stream.of(
        arguments("Sockets", 2, null),
        arguments("Cores/vCPU", null, 8),
        arguments("Untracked", 2, 8),
        arguments(null, 2, 8));
  }

  @ParameterizedTest
  @MethodSource("systemPurposeUnitsArgs")
  void testNormalizeMetricUnits(String unit, Integer expectedSockets, Integer expectedCores) {
    SystemProfileFacts systemProfileFacts = physicalSystemProfile(2, 4);
    RhsmFacts rhsmFacts = rhsmFacts(unit, null, Set.of());

    NormalizedFacts normalizedFacts = hostFacts(UUID.randomUUID().toString(), false);
    NormalizedMeasurements normalized =
        measurementNormalizer.getMeasurements(
            normalizedFacts, systemProfileFacts, Optional.of(rhsmFacts), Set.of());
    assertEquals(expectedSockets, normalized.getSockets().orElse(null));
    assertEquals(expectedCores, normalized.getCores().orElse(null));
  }

  private SystemProfileFacts physicalSystemProfile(Integer sockets, Integer coresPerSocket) {
    return new SystemProfileFacts(
        "host_type",
        "hypervisor_uuid",
        HardwareMeasurementType.PHYSICAL.name(),
        coresPerSocket,
        sockets,
        null,
        null,
        null,
        "arch",
        false,
        false,
        Set.of());
  }

  private SystemProfileFacts virtualSystemProfile(
      String arch, Integer sockets, Integer coresPerSocket, Integer threadsPerCore, Integer cpus) {
    return new SystemProfileFacts(
        "host_type",
        "hypervisor_uuid",
        HardwareMeasurementType.VIRTUAL.name(),
        coresPerSocket,
        sockets,
        cpus,
        threadsPerCore,
        null,
        arch,
        false,
        false,
        Set.of());
  }

  private SystemProfileFacts marketPlaceProfile(Integer sockets, Integer coresPerSocket) {
    return new SystemProfileFacts(
        "host_type",
        "hypervisor_uuid",
        HardwareMeasurementType.PHYSICAL.name(),
        coresPerSocket,
        sockets,
        null,
        null,
        null,
        "arch",
        true,
        false,
        Set.of());
  }

  private RhsmFacts rhsmFacts(String units, String role, Set<String> productIds) {
    return new RhsmFacts(
        "Premium",
        "Production",
        OffsetDateTime.now().toString(),
        false,
        role,
        units,
        null,
        null,
        productIds);
  }

  private RhsmFacts rhsmFacts() {
    return new RhsmFacts(
        "Premium",
        "Production",
        OffsetDateTime.now().toString(),
        false,
        null,
        null,
        null,
        null,
        Set.of());
  }

  private NormalizedFacts hostFacts(String subscriptionManagerId, boolean isVirtual) {
    return NormalizedFacts.builder()
        .subscriptionManagerId(subscriptionManagerId)
        .isVirtual(isVirtual)
        .build();
  }

  private NormalizedFacts hypervisorFacts(String subscriptionManagerId) {
    return NormalizedFacts.builder()
        .subscriptionManagerId(subscriptionManagerId)
        .isVirtual(false)
        .isHypervisor(true)
        .build();
  }

  private NormalizedFacts unmappedVirtualHostFacts(
      String subscriptionManagerId, String hypervisorUuid) {
    return NormalizedFacts.builder()
        .subscriptionManagerId(subscriptionManagerId)
        .isVirtual(true)
        .hypervisorUuid(hypervisorUuid)
        .isUnmappedGuest(true)
        .build();
  }

  private NormalizedFacts virtualWithCloudProviderHostFacts(
      String subscriptionManagerId, String hypervisorUuid) {
    return NormalizedFacts.builder()
        .subscriptionManagerId(subscriptionManagerId)
        .isVirtual(true)
        .cloudProviderType(HardwareMeasurementType.AWS)
        .hypervisorUuid(hypervisorUuid)
        .build();
  }
}
