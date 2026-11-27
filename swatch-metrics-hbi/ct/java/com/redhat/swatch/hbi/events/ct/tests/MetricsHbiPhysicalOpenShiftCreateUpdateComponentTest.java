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
 * Component tests for OpenShift Container Platform physical host events.
 *
 * <p>These tests verify that physical OpenShift hosts correctly generate measurements for BOTH
 * Cores and Sockets metrics with per-measurement product_tags. Physical hosts use the calculation:
 * cores = coresPerSocket × numberOfSockets (no threadsPerCore division like virtual hosts).
 *
 * <p>Key differences from virtual OpenShift tests:
 *
 * <ul>
 *   <li>Physical cores calculation: cores = coresPerSocket × sockets (not vCPU with threadsPerCore)
 *   <li>Modulo-2 socket rounding applies (odd sockets rounded up to even)
 *   <li>No unmapped guest or cloud provider scenarios (physical only)
 *   <li>Both Cores and Sockets measurements have product_tag="OpenShift Container Platform"
 * </ul>
 */
class MetricsHbiPhysicalOpenShiftCreateUpdateComponentTest extends BaseSMHBIComponentTest {

  @BeforeAll
  static void enableEmitEventsFeatureFlag() {
    unleash.enableFlag(EMIT_EVENTS);
  }

  @AfterAll
  static void disableEmitEventsFeatureFlag() {
    unleash.disableFlag(EMIT_EVENTS);
  }

  @TestPlanName("metrics-hbi-physical-openshift-TC001")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForPhysicalOpenShiftHost(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A physical OpenShift Container Platform host
    // Note: Physical calculation is cores = coresPerSocket × sockets (no threadsPerCore)
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts() // Adds product "69" (RHEL for x86)
                    .product("290") // OpenShift Container Platform
                    .sla("Premium")
                    .usage("Production")
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
                    .arch("x86_64")
                    .coresPerSocket(4)
                    .numberOfSockets(2)
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

    // Then: Assert BOTH Cores and Sockets measurements with physical calculation
    // Physical: cores = coresPerSocket × sockets = 4 × 2 = 8
    // Sockets: 2 (no rounding needed for even value)
    assertEquals(8.0, getMeasurementValue(actualEvent, "Cores"), "Cores should be 8.0");
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

    assertEquals(Event.Sla.PREMIUM, actualEvent.getSla(), "SLA should be Premium");
    assertEquals(Event.Usage.PRODUCTION, actualEvent.getUsage(), "Usage should be Production");
    assertEquals(
        Event.HardwareType.PHYSICAL,
        actualEvent.getHardwareType(),
        "Hardware type should be PHYSICAL");

    // Then: Verify full event matches expected structure
    // Note: defaultFacts() adds product "69", but pruning removes "RHEL for x86" from product_tags
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent,
            swatchEventType,
            List.of("69", "290"), // Both product IDs present
            Set.of("OpenShift Container Platform"), // Only OpenShift tag after pruning
            false,
            false);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedEvent, actualEvent),
        "Full event should match expected structure");
  }

  @TestPlanName("metrics-hbi-physical-openshift-TC002")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldRoundUpOddSocketValueToEven(String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A physical OpenShift host with odd number of sockets
    // Note: Physical hosts apply modulo-2 rounding (odd sockets rounded up)
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
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
                    .arch("x86_64")
                    .coresPerSocket(8)
                    .numberOfSockets(3) // Odd value should be rounded up to 4
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

    // Then: Assert measurements with socket rounding applied
    // Physical: cores = coresPerSocket × sockets = 8 × 3 = 24
    // Sockets: 3 rounded up to 4 (modulo-2 rounding for physical hosts)
    assertEquals(24.0, getMeasurementValue(actualEvent, "Cores"), "Cores should be 24.0");
    assertEquals(
        4.0, getMeasurementValue(actualEvent, "Sockets"), "Sockets should be 4.0 (rounded up)");

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

    assertEquals(Event.Sla.STANDARD, actualEvent.getSla(), "SLA should be Standard");
    assertEquals(Event.Usage.PRODUCTION, actualEvent.getUsage(), "Usage should be Production");
    assertEquals(
        Event.HardwareType.PHYSICAL,
        actualEvent.getHardwareType(),
        "Hardware type should be PHYSICAL");

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
}
