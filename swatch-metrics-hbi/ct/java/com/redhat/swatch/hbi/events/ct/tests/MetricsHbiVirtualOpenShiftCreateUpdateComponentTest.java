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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.RhsmFacts;
import com.redhat.swatch.component.tests.api.hbi.SystemProfileFacts;
import com.redhat.swatch.hbi.events.ct.api.MessageValidators;
import com.redhat.swatch.hbi.events.ct.utils.SwatchEventHelper;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.normalization.NormalizedEventType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Component tests for OpenShift Container Platform virtual host events.
 *
 * <p>These tests verify that OpenShift Container Platform products correctly generate measurements
 * for BOTH Cores and Sockets metrics with per-measurement product_tags. This differs from RHEL
 * products which only support the Sockets metric.
 *
 * <p>Key differences from RHEL tests:
 *
 * <ul>
 *   <li>Product ID 290 (OpenShift Container Platform) supports both Cores and Sockets metrics
 *   <li>Both measurements should have product_tag="OpenShift Container Platform"
 *   <li>No RHEL-specific unmapped guest socket override (OpenShift uses calculated sockets)
 *   <li>Included subscription pruning removes "RHEL for x86" from product_tags
 * </ul>
 */
class MetricsHbiVirtualOpenShiftCreateUpdateComponentTest extends BaseSMHBIComponentTest {

  @BeforeAll
  static void enableEmitEventsFeatureFlag() {
    unleash.enableFlag(EMIT_EVENTS);
  }

  @AfterAll
  static void disableEmitEventsFeatureFlag() {
    unleash.disableFlag(EMIT_EVENTS);
  }

  @TestPlanName("metrics-hbi-virtual-openshift-TC001")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForUnmappedGuestWithBothCoresAndSockets(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A virtual OpenShift Container Platform unmapped guest with threads per core set
    // Note: hypervisorUuid is set to a non-existent hypervisor UUID, making this an unmapped guest
    // Key difference from RHEL: OpenShift supports BOTH Cores and Sockets metrics
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts() // Adds product "69" (RHEL for x86)
                    .product("290") // OpenShift Container Platform
                    .sla("Self-Support")
                    .usage("Development/Test")
                    .isVirtual(true)
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("virtual")
                    .arch("x86_64")
                    .coresPerSocket(2)
                    .numberOfSockets(2)
                    .threadsPerCore(2)
                    .hypervisorUuid("non-existent-hypervisor-uuid")
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
        thenSwatchEventsAppear(
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()));
    assertEquals(1, actualEvents.size(), "Expected exactly 1 event");
    Event actualEvent = actualEvents.getFirst();

    // Then: Assert BOTH Cores and Sockets measurements exist with correct values
    // With coresPerSocket=2, sockets=2, threadsPerCore=2:
    // cores = ceil((2 * 2) / 2) = ceil(4 / 2) = 2
    // sockets = 2 (no RHEL-specific unmapped guest override for OpenShift)
    assertEquals(2.0, getMeasurementValue(actualEvent, "Cores"), "Cores should be 2.0");
    assertEquals(2.0, getMeasurementValue(actualEvent, "Sockets"), "Sockets should be 2.0");

    // Then: Verify BOTH measurements have product_tag for OpenShift Container Platform
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(m ->
                "Cores".equals(m.getMetricId())
                    && "OpenShift Container Platform".equals(m.getProductTag())),
        "Cores measurement should have OpenShift Container Platform product_tag");
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(m ->
                "Sockets".equals(m.getMetricId())
                    && "OpenShift Container Platform".equals(m.getProductTag())),
        "Sockets measurement should have OpenShift Container Platform product_tag");

    assertTrue(actualEvent.getIsUnmappedGuest(), "Should be unmapped guest");
    assertEquals(Event.Sla.SELF_SUPPORT, actualEvent.getSla(), "SLA should be Self-Support");
    assertEquals(Event.Usage.DEVELOPMENT_TEST, actualEvent.getUsage(), "Usage should be Dev/Test");

    // Then: Verify full event matches expected structure
    // Note: defaultFacts() adds product "69", but pruning removes "RHEL for x86" from product_tags
    // because it's an included subscription of OpenShift Container Platform
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent,
            swatchEventType,
            List.of("69", "290"), // Both product IDs present
            Set.of("OpenShift Container Platform"), // Only OpenShift tag after pruning
            true,
            false);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedEvent, actualEvent),
        "Full event should match expected structure");
  }

  @TestPlanName("metrics-hbi-virtual-openshift-TC002")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForUnmappedGuestFromCpus(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A virtual OpenShift Container Platform unmapped guest with CPUs set (no
    // threadsPerCore)
    // Note: hypervisorUuid is set to a non-existent hypervisor UUID, making this an unmapped guest
    // Difference from TC001: uses numberOfCpus for calculated threadsPerCore
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts()
                    .product("290") // OpenShift Container Platform
                    .sla("Premium")
                    .usage("Production")
                    .isVirtual(true)
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("virtual")
                    .arch("x86_64")
                    .coresPerSocket(4)
                    .numberOfSockets(2)
                    .numberOfCpus(16) // Used to calculate threadsPerCore
                    .hypervisorUuid("non-existent-hypervisor-uuid")
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
        thenSwatchEventsAppear(
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()));
    assertEquals(1, actualEvents.size(), "Expected exactly 1 event");
    Event actualEvent = actualEvents.getFirst();

    // Then: Assert BOTH Cores and Sockets measurements
    // threadsPerCore = numberOfCpus / (coresPerSocket * sockets) = 16 / (4 * 2) = 16 / 8 = 2.0
    // cores = ceil((4 * 2) / 2.0) = ceil(8 / 2.0) = ceil(4.0) = 4
    // sockets = 2 (no RHEL-specific override)
    assertEquals(4.0, getMeasurementValue(actualEvent, "Cores"), "Cores should be 4.0");
    assertEquals(2.0, getMeasurementValue(actualEvent, "Sockets"), "Sockets should be 2.0");

    // Then: Verify BOTH measurements have product_tag
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(m ->
                "Cores".equals(m.getMetricId())
                    && "OpenShift Container Platform".equals(m.getProductTag())),
        "Cores measurement should have OpenShift Container Platform product_tag");
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(m ->
                "Sockets".equals(m.getMetricId())
                    && "OpenShift Container Platform".equals(m.getProductTag())),
        "Sockets measurement should have OpenShift Container Platform product_tag");

    assertTrue(actualEvent.getIsUnmappedGuest(), "Should be unmapped guest");
    assertEquals(Event.Sla.PREMIUM, actualEvent.getSla(), "SLA should be Premium");
    assertEquals(Event.Usage.PRODUCTION, actualEvent.getUsage(), "Usage should be Production");

    // Then: Verify full event matches expected structure
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent,
            swatchEventType,
            List.of("69", "290"),
            Set.of("OpenShift Container Platform"),
            true,
            false);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedEvent, actualEvent),
        "Full event should match expected structure");
  }

  @TestPlanName("metrics-hbi-virtual-openshift-TC003")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForCloudProviderHost(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A virtual cloud provider (AWS) OpenShift Container Platform host event
    // Key difference: OpenShift doesn't have RHEL-specific unmapped guest socket override,
    // but cloud provider still forces sockets=1
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts()
                    .product("290") // OpenShift Container Platform
                    .sla("Standard")
                    .usage("Production")
                    .isVirtual(true)
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("virtual")
                    .arch("x86_64")
                    .coresPerSocket(8)
                    .numberOfSockets(4)
                    .numberOfCpus(16)
                    .cloudProvider("aws") // Cloud provider forces sockets=1
                    .hypervisorUuid(null)
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
        thenSwatchEventsAppear(
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()));
    assertEquals(1, actualEvents.size(), "Expected exactly 1 event");
    Event actualEvent = actualEvents.getFirst();

    // Then: Assert measurements
    // threadsPerCore = 16 / (8 * 4) = 16 / 32 = 0.5
    // cores = ceil((8 * 4) / 0.5) = ceil(32 / 0.5) = ceil(64) = 64
    // sockets = 1 (cloud provider override applies to all products)
    assertEquals(64.0, getMeasurementValue(actualEvent, "Cores"), "Cores should be 64.0");
    assertEquals(1.0, getMeasurementValue(actualEvent, "Sockets"), "Sockets should be 1.0");

    // Then: Verify BOTH measurements have product_tag
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(m ->
                "Cores".equals(m.getMetricId())
                    && "OpenShift Container Platform".equals(m.getProductTag())),
        "Cores measurement should have OpenShift Container Platform product_tag");
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(m ->
                "Sockets".equals(m.getMetricId())
                    && "OpenShift Container Platform".equals(m.getProductTag())),
        "Sockets measurement should have OpenShift Container Platform product_tag");

    assertEquals(Event.CloudProvider.AWS, actualEvent.getCloudProvider(), "Should be AWS");
    assertEquals(Event.HardwareType.CLOUD, actualEvent.getHardwareType(), "Should be CLOUD type");

    // Then: Verify full event matches expected structure
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent,
            swatchEventType,
            List.of("69", "290"),
            Set.of("OpenShift Container Platform"),
            false,
            false);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedEvent, actualEvent),
        "Full event should match expected structure");
  }

  @TestPlanName("metrics-hbi-virtual-openshift-TC004")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForUnmappedGuestWithDefaultThreadsPerCore(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A virtual OpenShift unmapped guest with neither threadsPerCore nor numberOfCpus set
    // Service should use default threadsPerCore=2.0 for vCPU calculation
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts()
                    .product("290") // OpenShift Container Platform
                    .sla("Standard")
                    .usage("Production")
                    .isVirtual(true)
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("virtual")
                    .arch("x86_64")
                    .coresPerSocket(2)
                    .numberOfSockets(2)
                    // No threadsPerCore set - will use default 2.0
                    // No numberOfCpus set - cannot calculate threadsPerCore
                    .hypervisorUuid("non-existent-hypervisor-uuid")
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
        thenSwatchEventsAppear(
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()));
    assertEquals(1, actualEvents.size(), "Expected exactly 1 event");
    Event actualEvent = actualEvents.getFirst();

    // Then: Assert measurements calculated with default threadsPerCore=2.0
    // cores = ceil((coresPerSocket * sockets) / threadsPerCore)
    //       = ceil((2 * 2) / 2.0) = ceil(4 / 2.0) = ceil(2.0) = 2
    // sockets = 2 (NO RHEL-specific unmapped guest override for OpenShift)
    assertEquals(2.0, getMeasurementValue(actualEvent, "Cores"), "Cores should be 2.0");
    assertEquals(2.0, getMeasurementValue(actualEvent, "Sockets"), "Sockets should be 2.0");

    // Then: Verify BOTH measurements have product_tag
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(m ->
                "Cores".equals(m.getMetricId())
                    && "OpenShift Container Platform".equals(m.getProductTag())),
        "Cores measurement should have OpenShift Container Platform product_tag");
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(m ->
                "Sockets".equals(m.getMetricId())
                    && "OpenShift Container Platform".equals(m.getProductTag())),
        "Sockets measurement should have OpenShift Container Platform product_tag");

    assertTrue(actualEvent.getIsUnmappedGuest(), "Should be unmapped guest");
    assertEquals(Event.Sla.STANDARD, actualEvent.getSla(), "SLA should be Standard");
    assertEquals(Event.Usage.PRODUCTION, actualEvent.getUsage(), "Usage should be Production");

    // Then: Verify full event matches expected structure
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent,
            swatchEventType,
            List.of("69", "290"),
            Set.of("OpenShift Container Platform"),
            true,
            false);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedEvent, actualEvent),
        "Full event should match expected structure");
  }

  @TestPlanName("metrics-hbi-virtual-openshift-TC005")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForUnmappedGuestFromThreadsPerCore(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A virtual OpenShift Container Platform unmapped guest with high threadsPerCore
    // Note: numberOfSockets=1, threadsPerCore=4 for vCPU calculation verification
    // This test demonstrates cores calculation with high threadsPerCore value
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts()
                    .product("290") // OpenShift Container Platform
                    .sla("Standard")
                    .usage("Production")
                    .isVirtual(true)
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("virtual")
                    .arch("x86_64")
                    .coresPerSocket(1)
                    .numberOfSockets(1)
                    .threadsPerCore(4)
                    .hypervisorUuid("non-existent-hypervisor-uuid")
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
        thenSwatchEventsAppear(
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()));
    assertEquals(1, actualEvents.size(), "Expected exactly 1 event");
    Event actualEvent = actualEvents.getFirst();

    // Then: Assert specific measurement values on ACTUAL event from Kafka
    // With coresPerSocket=1, sockets=1, threadsPerCore=4:
    // cores = ceil((1 * 1) / 4) = ceil(1 / 4) = ceil(0.25) = 1
    // sockets = 1 (input was already 1, not because of RHEL override)
    assertEquals(1.0, getMeasurementValue(actualEvent, "Cores"), "Cores should be 1.0");
    assertEquals(1.0, getMeasurementValue(actualEvent, "Sockets"), "Sockets should be 1.0");

    // Then: Verify BOTH Cores and Sockets measurements have product_tag for OpenShift
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(m ->
                "Cores".equals(m.getMetricId())
                    && "OpenShift Container Platform".equals(m.getProductTag())),
        "Cores measurement should have OpenShift Container Platform product_tag");
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(m ->
                "Sockets".equals(m.getMetricId())
                    && "OpenShift Container Platform".equals(m.getProductTag())),
        "Sockets measurement should have OpenShift Container Platform product_tag");

    assertTrue(actualEvent.getIsUnmappedGuest(), "Should be unmapped guest");

    // Then: Verify full event matches expected structure
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent,
            swatchEventType,
            List.of("69", "290"),
            Set.of("OpenShift Container Platform"),
            true,
            false);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedEvent, actualEvent),
        "Full event should match expected structure");
  }
}
