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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.RhsmFacts;
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

class MetricsHbiPhysicalRhelCreateUpdateComponentTest extends BaseSMHBIComponentTest {

  @BeforeAll
  static void enableEmitEventsFeatureFlag() {
    unleash.enableFlag(EMIT_EVENTS);
  }

  @AfterAll
  static void disableEmitEventsFeatureFlag() {
    unleash.disableFlag(EMIT_EVENTS);
  }

  @TestPlanName("metrics-hbi-physical-TC001")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForPhysicalRhsmHost(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A physical RHEL for x86 host with an HBI event built from it
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts()
                    .sla("Self-Support")
                    .usage("Development/Test")
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
                    .arch("x86_64")
                    .coresPerSocket(2)
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
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));

    Event actualEvent = actualEvents.getFirst();

    // Then: Assert specific measurement values on ACTUAL event from Kafka: sockets=2
    assertNull(
        getMeasurementValue(actualEvent, "Cores"),
        "Cores should not be present - RHEL products only support Sockets metric");
    assertEquals(2.0, getMeasurementValue(actualEvent, "Sockets"), "Sockets should be 2.0");
    // Note: Only Sockets metric has product_tag since RHEL products only support Sockets, not Cores
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(m ->
                "Sockets".equals(m.getMetricId()) && "RHEL for x86".equals(m.getProductTag())));
    assertEquals(Event.Sla.SELF_SUPPORT, actualEvent.getSla(), "SLA should be Self-Support");
    assertEquals(Event.Usage.DEVELOPMENT_TEST, actualEvent.getUsage(), "Usage should be Dev/Test");
    assertEquals(
        Event.HardwareType.PHYSICAL,
        actualEvent.getHardwareType(),
        "hardware type should be PHYSICAL");

    // Then: Verify full event matches expected structure
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);
    assertTrue(SwatchEventHelper.eventsMatch(expectedEvent, actualEvent));
  }

  @TestPlanName("metrics-hbi-physical-TC002")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldRoundUpOddSocketValueToEven(String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A physical RHEL for x86 host with odd number of sockets
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts()
                    .sla("Self-Support")
                    .usage("Development/Test")
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
                    .arch("x86_64")
                    .coresPerSocket(2)
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
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));

    Event actualEvent = actualEvents.getFirst();

    // Then: Assert specific measurement values on ACTUAL event from Kafka: sockets=4 (rounded up
    // from 3)
    assertNull(
        getMeasurementValue(actualEvent, "Cores"),
        "Cores should not be present - RHEL products only support Sockets metric");
    assertEquals(4.0, getMeasurementValue(actualEvent, "Sockets"), "Sockets should be 4.0");
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(
                m -> "Sockets".equals(m.getMetricId()) && "RHEL for x86".equals(m.getProductTag())),
        "Sockets measurement should have RHEL for x86 product_tag");

    // Then: Verify full event matches expected structure
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);
    assertTrue(SwatchEventHelper.eventsMatch(expectedEvent, actualEvent));
  }

  @TestPlanName("metrics-hbi-physical-TC003")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceConvertedTagForConversionActivity(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A physical RHEL for x86 host with conversions.activity set to true and engineering ID
    // 204
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts()
                    .sla("Self-Support")
                    .usage("Development/Test")
                    .product("204") // Engineering ID for ELS conversion
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
                    .arch("x86_64")
                    .coresPerSocket(2)
                    .numberOfSockets(2)
                    .conversionsActivity(true)
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

    Event actualEvent = actualEvents.getFirst();

    // Then: Assert specific measurement values on ACTUAL event from Kafka: sockets=2,
    // productTag=converted
    assertNull(
        getMeasurementValue(actualEvent, "Cores"),
        "Cores should not be present - RHEL products only support Sockets metric");
    assertEquals(2.0, getMeasurementValue(actualEvent, "Sockets"), "Sockets should be 2.0");
    // Note: Only Sockets metric has product_tag since RHEL products only support Sockets, not Cores
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(m ->
                "Sockets".equals(m.getMetricId())
                    && "rhel-for-x86-els-converted".equals(m.getProductTag())));
    assertEquals(
        Boolean.TRUE, actualEvent.getConversion(), "Event should be marked as a conversion");

    // Then: Verify full event matches expected structure
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent,
            swatchEventType,
            List.of("69", "204"), // Both products from defaultFacts() and explicit product()
            Set.of("rhel-for-x86-els-converted"),
            false,
            false);
    assertTrue(SwatchEventHelper.eventsMatch(expectedEvent, actualEvent));
  }

  @TestPlanName("metrics-hbi-physical-TC004")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldForceCoresAndSocketsToZeroForMarketplaceHosts(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A physical RHEL for x86 host with is_marketplace set to true
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts()
                    .sla("Self-Support")
                    .usage("Development/Test")
                    // BILLING_MODEL is NOT set to "marketplace" to ensure event is not dropped
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
                    .arch("x86_64")
                    .coresPerSocket(4)
                    .numberOfSockets(2)
                    .isMarketplace(true)
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

    Event actualEvent = actualEvents.getFirst();

    // Then: Assert specific measurement values on ACTUAL event from Kafka: sockets=0
    assertNull(
        getMeasurementValue(actualEvent, "Cores"),
        "Cores should not be present - RHEL products only support Sockets metric");
    assertEquals(0.0, getMeasurementValue(actualEvent, "Sockets"), "Sockets should be 0.0");
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(
                m -> "Sockets".equals(m.getMetricId()) && "RHEL for x86".equals(m.getProductTag())),
        "Sockets measurement should have RHEL for x86 product_tag");

    // Then: Verify full event matches expected structure
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);
    assertTrue(SwatchEventHelper.eventsMatch(expectedEvent, actualEvent));
  }

  @TestPlanName("metrics-hbi-physical-TC005")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldSuppressCoresWhenSystemPurposeUnitsSetToSockets(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A physical RHEL for x86 host with system purpose units set to "Sockets"
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts()
                    .sla("Self-Support")
                    .usage("Development/Test")
                    .systemPurposeUnits("Sockets")
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
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));

    Event actualEvent = actualEvents.getFirst();

    // Then: Assert specific measurement values on ACTUAL event from Kafka: sockets=2
    assertNull(
        getMeasurementValue(actualEvent, "Cores"),
        "Cores should not be present - RHEL products only support Sockets metric");
    assertEquals(2.0, getMeasurementValue(actualEvent, "Sockets"), "Sockets should be 2.0");
    assertTrue(
        actualEvent.getMeasurements().stream()
            .anyMatch(
                m -> "Sockets".equals(m.getMetricId()) && "RHEL for x86".equals(m.getProductTag())),
        "Sockets measurement should have RHEL for x86 product_tag");

    // Then: Verify full event matches expected structure
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);
    assertTrue(SwatchEventHelper.eventsMatch(expectedEvent, actualEvent));
  }

  @TestPlanName("metrics-hbi-physical-TC006")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldSuppressSocketsWhenSystemPurposeUnitsSetToCores(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A physical RHEL for x86 host with system purpose units set to "Cores/vCPU"
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts()
                    .sla("Self-Support")
                    .usage("Development/Test")
                    .systemPurposeUnits("Cores/vCPU")
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
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));

    Event actualEvent = actualEvents.getFirst();

    // Then: Assert specific measurement values on ACTUAL event from Kafka
    // Edge case: System purpose wants Cores/vCPU, but RHEL only supports Sockets metric
    // Result: Both Cores (not supported by product) and Sockets (suppressed by system purpose) are
    // omitted
    assertNull(
        getMeasurementValue(actualEvent, "Cores"),
        "Cores should not be present - RHEL products only support Sockets metric");
    assertNull(
        getMeasurementValue(actualEvent, "Sockets"),
        "Sockets should be omitted - suppressed by system purpose units=Cores/vCPU");

    // Then: Verify full event matches expected structure
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);
    assertTrue(SwatchEventHelper.eventsMatch(expectedEvent, actualEvent));
  }

  @TestPlanName("metrics-hbi-physical-TC007")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceMultipleMeasurementsForMultipleRhelVariants(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A physical RHEL host with RHSM reporting multiple product IDs for different variants
    // Product IDs: 69 (RHEL for x86), 71 (Workstation), 76 (Compute Node), 83 (HA)
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .product("69") // RHEL for x86
                    .product("71") // RHEL Workstation
                    .product("76") // RHEL Compute Node
                    .product("83") // rhel-for-x86-ha
                    .sla("Self-Support")
                    .usage("Production")
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
                    .arch("x86_64")
                    .coresPerSocket(16)
                    .numberOfSockets(24)
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

    Event actualEvent = actualEvents.getFirst();

    // Then: Verify product IDs (all 4 should be present)
    assertEquals(
        Set.of("69", "71", "83", "76"),
        new HashSet<>(actualEvent.getProductIds()),
        "All 4 product IDs should be present");

    // Then: Verify Sockets measurement exists with correct value
    assertEquals(
        24.0, getMeasurementValue(actualEvent, "Sockets"), "Sockets measurement should be 24.0");

    // Then: Verify Cores is NOT present (RHEL products don't support Cores metric)
    assertNull(
        getMeasurementValue(actualEvent, "Cores"),
        "Cores should not be present - RHEL products only support Sockets metric");

    // Then: Verify we have exactly 4 Sockets measurements, one for each variant
    long socketsCount =
        actualEvent.getMeasurements().stream()
            .filter(m -> "Sockets".equals(m.getMetricId()))
            .count();
    assertEquals(4, socketsCount, "Should have 4 Sockets measurements, one per variant");

    // Then: Verify each measurement has the correct product_tag
    Set<String> actualProductTags =
        actualEvent.getMeasurements().stream()
            .filter(m -> "Sockets".equals(m.getMetricId()))
            .map(org.candlepin.subscriptions.json.Measurement::getProductTag)
            .collect(java.util.stream.Collectors.toSet());

    Set<String> expectedProductTags =
        Set.of("rhel-for-x86-ha", "RHEL Workstation", "RHEL Compute Node", "RHEL for x86");

    assertEquals(
        expectedProductTags,
        actualProductTags,
        "Each variant should have its own measurement with correct product_tag");

    // Then: Verify each Sockets measurement has the same value
    actualEvent.getMeasurements().stream()
        .filter(m -> "Sockets".equals(m.getMetricId()))
        .forEach(m ->
            assertEquals(24.0, m.getValue(), "All Sockets measurements should have value 24.0"));
  }
}
