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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.redhat.swatch.hbi.events.HypervisorGuestRepository;
import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.normalization.facts.HostFacts;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SatelliteFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SystemProfileFacts;
import java.time.OffsetDateTime;
import java.util.List;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeasurementNormalizerTest {

  @Mock HypervisorGuestRepository hypervisorGuestRepository;
  private MeasurementNormalizer measurementNormalizer;

  @BeforeEach
  void setup() {
    measurementNormalizer =
        new MeasurementNormalizer(new ApplicationConfiguration(), hypervisorGuestRepository);
  }

  @Test
  void testRhsmNormalization() {
    SystemProfileFacts systemProfileFacts = systemProfileFacts(6, 2);
    RhsmFacts rhsmFacts = rhsmFacts(null, null, Set.of("69"));
    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts, Optional.of(rhsmFacts), Optional.empty(), Optional.empty(), false);

    HostFacts hostFacts = hostFacts(UUID.randomUUID().toString(), false);
    NormalizedMeasurements normalized =
        measurementNormalizer.getMeasurements(
            hostFacts, systemProfileFacts, Optional.of(rhsmFacts), productNormalizer);
    assertEquals(2, normalized.getSockets().orElse(null));
    assertEquals(12, normalized.getCores().orElse(null));
  }

  static Stream<Arguments> syspurposeUnitsArgs() {
    return Stream.of(
        arguments("Sockets", 2, null),
        arguments("Cores/vCPU", null, 4),
        arguments("Untracked", 2, 4));
  }

  @ParameterizedTest
  @MethodSource("syspurposeUnitsArgs")
  void testSyspurposeUnits(String unit, Integer sockets, Integer cores) {
    SystemProfileFacts systemProfileFacts = systemProfileFacts(2, 2);
    RhsmFacts rhsmFacts = rhsmFacts(unit, null, Set.of());
    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts, Optional.of(rhsmFacts), Optional.empty(), Optional.empty(), false);

    HostFacts hostFacts = hostFacts(UUID.randomUUID().toString(), false);
    NormalizedMeasurements normalized =
        measurementNormalizer.getMeasurements(
            hostFacts, systemProfileFacts, Optional.of(rhsmFacts), productNormalizer);
    assertEquals(sockets, normalized.getSockets().orElse(null));
    assertEquals(cores, normalized.getCores().orElse(null));
  }

  @Test
  void testSyspurposeUnitsUnspecified() {
    SystemProfileFacts systemProfileFacts = systemProfileFacts(2, 2);
    RhsmFacts rhsmFacts = rhsmFacts();
    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts, Optional.of(rhsmFacts), Optional.empty(), Optional.empty(), false);

    HostFacts hostFacts = hostFacts(UUID.randomUUID().toString(), false);
    NormalizedMeasurements normalized =
        measurementNormalizer.getMeasurements(
            hostFacts, systemProfileFacts, Optional.of(rhsmFacts), productNormalizer);
    assertEquals(2, normalized.getSockets().orElse(null));
    assertEquals(4, normalized.getCores().orElse(null));
  }

  private SystemProfileFacts systemProfileFacts(Integer coresPerSocket, Integer sockets) {
    return new SystemProfileFacts(
        "hypervisor_uuid",
        "infra",
        coresPerSocket,
        sockets,
        null,
        null,
        null,
        "arch",
        false,
        false,
        List.of());
  }

  private RhsmFacts rhsmFacts(String units, String role, Set<String> productIds) {
    return new RhsmFacts(
        "Premium", "Production", OffsetDateTime.now().toString(), false, role, units, productIds);
  }

  private RhsmFacts rhsmFacts() {
    return new RhsmFacts(
        "Premium", "Production", OffsetDateTime.now().toString(), false, null, null, Set.of());
  }

  private SatelliteFacts satelliteFacts(String role) {
    return new SatelliteFacts("Premium", "Production", role, "hypervisor_uuid");
  }

  private HostFacts hostFacts(String subscriptionManagerId, boolean isVirtual) {
    return HostFacts.builder()
        .subscriptionManagerId(subscriptionManagerId)
        .isVirtual(isVirtual)
        .build();
  }
}
