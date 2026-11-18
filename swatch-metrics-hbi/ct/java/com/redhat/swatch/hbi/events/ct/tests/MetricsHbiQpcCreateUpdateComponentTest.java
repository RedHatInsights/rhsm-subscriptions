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
package com.redhat.swatch.hbi.events.ct.tests;

import static com.redhat.swatch.hbi.events.ct.utils.SwatchEventHelper.getMeasurementValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.QpcFacts;
import com.redhat.swatch.component.tests.api.hbi.SystemProfileFacts;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.Topics;
import com.redhat.swatch.hbi.events.ct.api.MessageValidators;
import com.redhat.swatch.hbi.events.ct.utils.SwatchEventHelper;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.normalization.NormalizedEventType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MetricsHbiQpcCreateUpdateComponentTest extends BaseSMHBIComponentTest {

  @BeforeAll
  static void enableEmitEventsFeatureFlag() {
    unleash.enableFlag(EMIT_EVENTS);
  }

  @AfterAll
  static void disableEmitEventsFeatureFlag() {
    unleash.disableFlag(EMIT_EVENTS);
  }

  @TestPlanName("metrics-hbi-qpc-TC001")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForQpcRhelHost(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A QPC-detected RHEL host with x86_64 architecture
    // NOTE: Arch (x86_64/aarch64/ppc64le) is required - "RHEL Ungrouped" doesn't support metrics
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .qpcFacts(QpcFacts.builder().product("RHEL").build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
                    .arch("x86_64")
                    .coresPerSocket(2)
                    .numberOfSockets(2)
                    .numberOfCpus(4)
                    .build())
            .toEvent()
            .type(hbiEventType)
            .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
            .buildCreateUpdate();

    // When: HBI event is produced to Kafka
    hostEvents.publish(hbiEvent);

    // Then: The RHEL for x86 architecture-specific tag is emitted
    String instanceId = hbiEvent.getHost().getId().toString();
    List<Event> actualEvents =
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));

    assertEquals(1, actualEvents.size(), "Expected exactly one matching SWatch event");
    Event actualEvent = actualEvents.getFirst();
    assertEquals(List.of(), actualEvent.getProductIds());
    assertNull(
        getMeasurementValue(actualEvent, "Cores"),
        "Cores should not be present - RHEL products only support Sockets metric");
    assertEquals(2.0, getMeasurementValue(actualEvent, "Sockets"), "Sockets should be 2.0");
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(
                m -> "Sockets".equals(m.getMetricId()) && "RHEL for x86".equals(m.getProductTag())),
        "Sockets measurement should have RHEL for x86 product_tag");
    assertFalse(actualEvent.getIsVirtual(), "Should be a physical host");

    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of(), Set.of("RHEL for x86"), false, false);
    assertTrue(SwatchEventHelper.eventsMatch(expectedEvent, actualEvent));
  }

  @TestPlanName("metrics-hbi-qpc-TC002")
  @ParameterizedTest
  @CsvSource({
    "created, INSTANCE_CREATED, x86_64, RHEL for x86",
    "created, INSTANCE_CREATED, aarch64, RHEL for ARM",
    "created, INSTANCE_CREATED, ppc64le, RHEL for IBM Power",
    "updated, INSTANCE_UPDATED, x86_64, RHEL for x86",
    "updated, INSTANCE_UPDATED, aarch64, RHEL for ARM",
    "updated, INSTANCE_UPDATED, ppc64le, RHEL for IBM Power"
  })
  void shouldProduceArchitectureSpecificTagForQpcRhelHost(
      String hbiEventType,
      NormalizedEventType swatchEventType,
      String arch,
      String derivedVariant) {
    // Given: A QPC-detected RHEL host with a recognized architecture
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .qpcFacts(QpcFacts.builder().product("RHEL").build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
                    .arch(arch)
                    .coresPerSocket(2)
                    .numberOfSockets(2)
                    .numberOfCpus(4)
                    .build())
            .toEvent()
            .type(hbiEventType)
            .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
            .buildCreateUpdate();

    // When: HBI event is produced to Kafka
    hostEvents.publish(hbiEvent);

    // Then: The architecture derived tag is omitted.
    String instanceId = hbiEvent.getHost().getId().toString();
    List<Event> actualEvents =
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));

    assertEquals(1, actualEvents.size(), "Expected exactly one matching SWatch event");
    Event actualEvent = actualEvents.getFirst();
    assertEquals(List.of(), actualEvent.getProductIds());
    assertNull(
        getMeasurementValue(actualEvent, "Cores"),
        "Cores should not be present - RHEL products only support Sockets metric");
    assertEquals(2.0, getMeasurementValue(actualEvent, "Sockets"), "Sockets should be 2.0");
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(
                m -> "Sockets".equals(m.getMetricId()) && derivedVariant.equals(m.getProductTag())),
        "Sockets measurement should have " + derivedVariant + " product_tag");
    assertFalse(actualEvent.getIsVirtual(), "Should be a physical host");

    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of(), Set.of(derivedVariant), false, false);
    assertTrue(SwatchEventHelper.eventsMatch(expectedEvent, actualEvent));
  }

  @TestPlanName("metrics-hbi-qpc-TC003")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldKeepAllVariantsButNotAddRhelUngrouped(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A QPC-detected RHEL host with system profile reporting multiple RHEL product IDs
    // This creates multiple variants, testing the variantCount > 1 scenario where
    // "RHEL Ungrouped" used to be added but should NOT be added anymore
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .qpcFacts(QpcFacts.builder().product("RHEL").build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
                    .arch("x86_64")
                    .coresPerSocket(4)
                    .numberOfSockets(8)
                    .numberOfCpus(32)
                    .installedProductId("69") // RHEL for x86
                    .installedProductId("71") // RHEL Workstation
                    .installedProductId("76") // RHEL Compute Node
                    .build())
            .toEvent()
            .type(hbiEventType)
            .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
            .buildCreateUpdate();

    // When: HBI event is produced to Kafka
    hostEvents.publish(hbiEvent);

    // Then: Wait for and capture the actual SWatch event from Kafka
    String instanceId = hbiEvent.getHost().getId().toString();
    List<Event> actualEvents =
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));

    assertEquals(1, actualEvents.size(), "Expected exactly one matching SWatch event");
    Event actualEvent = actualEvents.getFirst();

    // Then: Verify all 3 product IDs are present
    assertEquals(
        Set.of("69", "71", "76"),
        new HashSet<>(actualEvent.getProductIds()),
        "All 3 product IDs should be present");

    // Then: Verify Sockets measurement exists
    assertEquals(8.0, getMeasurementValue(actualEvent, "Sockets"), "Sockets should be 8.0");
    assertNull(
        getMeasurementValue(actualEvent, "Cores"),
        "Cores should not be present - RHEL products only support Sockets metric");

    // Then: Verify all 3 real variants appear in measurements
    Set<String> measurementProductTags =
        actualEvent.getMeasurements().stream()
            .filter(m -> "Sockets".equals(m.getMetricId()))
            .map(org.candlepin.subscriptions.json.Measurement::getProductTag)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());

    assertEquals(
        Set.of("RHEL for x86", "RHEL Workstation", "RHEL Compute Node"),
        measurementProductTags,
        "All 3 real RHEL variants should appear in measurements");

    // Then: Verify "RHEL Ungrouped" does NOT appear (key assertion!)
    assertFalse(
        actualEvent.getMeasurements().stream()
            .anyMatch(m -> "RHEL Ungrouped".equals(m.getProductTag())),
        "RHEL Ungrouped should NOT be added even with multiple variants (variantCount=2)");

    // Then: Verify "RHEL" marker does NOT appear in measurements (no metric support)
    assertFalse(
        actualEvent.getMeasurements().stream().anyMatch(m -> "RHEL".equals(m.getProductTag())),
        "RHEL marker should NOT appear in measurements (no metric support)");

    // Then: Verify we have exactly 3 Sockets measurements
    long socketsCount =
        actualEvent.getMeasurements().stream()
            .filter(m -> "Sockets".equals(m.getMetricId()))
            .count();
    assertEquals(3, socketsCount, "Should have 3 Sockets measurements, one per real variant");

    // Then: Verify full event structure
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent,
            swatchEventType,
            List.of("69", "71", "76"),
            Set.of("RHEL for x86", "RHEL Workstation", "RHEL Compute Node"),
            false,
            false);
    assertTrue(SwatchEventHelper.eventsMatch(expectedEvent, actualEvent));
  }
}
