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

    // Then: Assert specific measurement values on ACTUAL event from Kafka: cores=4, sockets=2
    assertEquals(4.0, getMeasurementValue(actualEvent, "cores"), "cores should be 4.0");
    assertEquals(2.0, getMeasurementValue(actualEvent, "sockets"), "sockets should be 2.0");
    assertEquals(Event.Sla.SELF_SUPPORT, actualEvent.getSla(), "SLA should be Self-Support");
    assertEquals(Event.Usage.DEVELOPMENT_TEST, actualEvent.getUsage(), "Usage should be Dev/Test");
    assertTrue(actualEvent.getProductTag().contains("RHEL for x86"), "Should have x86 product tag");
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

    // Then: Assert specific measurement values on ACTUAL event from Kafka:
    // cores=6 (2 cores_per_socket x 3 number_of_sockets separate from socket normalization to 4),
    // sockets=4
    assertEquals(6.0, getMeasurementValue(actualEvent, "cores"), "cores should be 6.0");
    assertEquals(4.0, getMeasurementValue(actualEvent, "sockets"), "sockets should be 4.0");

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

    // Then: Assert specific measurement values on ACTUAL event from Kafka: cores=4, sockets=2,
    // productTag=converted
    assertEquals(4.0, getMeasurementValue(actualEvent, "cores"), "cores should be 4.0");
    assertEquals(2.0, getMeasurementValue(actualEvent, "sockets"), "sockets should be 2.0");
    assertTrue(
        actualEvent.getProductTag().contains("rhel-for-x86-els-converted"),
        "Should have converted product tag");
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

    // Then: Assert specific measurement values on ACTUAL event from Kafka: cores=0, sockets=0
    assertEquals(0.0, getMeasurementValue(actualEvent, "cores"), "cores should be 0.0");
    assertEquals(0.0, getMeasurementValue(actualEvent, "sockets"), "sockets should be 0.0");

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

    // Then: Assert specific measurement values on ACTUAL event from Kafka: cores omitted, sockets=2
    assertNull(getMeasurementValue(actualEvent, "cores"), "cores should be omitted");
    assertEquals(2.0, getMeasurementValue(actualEvent, "sockets"), "sockets should be 2.0");

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

    // Then: Assert specific measurement values on ACTUAL event from Kafka: cores=8, sockets omitted
    assertEquals(8.0, getMeasurementValue(actualEvent, "cores"), "cores should be 8.0");
    assertNull(getMeasurementValue(actualEvent, "sockets"), "sockets should be omitted");

    // Then: Verify full event matches expected structure
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);
    assertTrue(SwatchEventHelper.eventsMatch(expectedEvent, actualEvent));
  }
}
