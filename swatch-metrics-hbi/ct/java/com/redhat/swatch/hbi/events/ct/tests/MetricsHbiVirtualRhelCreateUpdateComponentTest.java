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

class MetricsHbiVirtualRhelCreateUpdateComponentTest extends BaseSMHBIComponentTest {

  @BeforeAll
  static void enableEmitEventsFeatureFlag() {
    unleash.enableFlag(EMIT_EVENTS);
  }

  @AfterAll
  static void disableEmitEventsFeatureFlag() {
    unleash.disableFlag(EMIT_EVENTS);
  }

  @TestPlanName("metrics-hbi-virtual-TC001")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForUnmappedGuestFromThreadsPerCore(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A virtual RHEL for x86 unmapped guest with threads per core set
    // Note: hypervisorUuid is set to a non-existent hypervisor UUID, making this an unmapped guest
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts()
                    .sla("Self-Support")
                    .usage("Development/Test")
                    .isVirtual(true)
                    .product("69")
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("virtual")
                    .arch("x86_64")
                    .coresPerSocket(2)
                    .numberOfSockets(2)
                    .threadsPerCore(4)
                    .hypervisorUuid("non-existent-hypervisor-uuid")
                    .build())
            .toEvent()
            .type(hbiEventType)
            .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
            .buildCreateUpdate();

    // When: HBI event is produced to Kafka
    hostEvents.publish(hbiEvent);

    // Then: Wait for the actual SWatch event from Kafka using minimal key-based matcher
    String instanceId = hbiEvent.getHost().getId().toString();
    List<Event> actualEvents =
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));

    Event actualEvent = actualEvents.getFirst();

    // Then: Assert specific measurement values on ACTUAL event from Kafka: cores=1, sockets=1
    assertEquals(1.0, getMeasurementValue(actualEvent, "cores"), "cores should be 1.0");
    assertEquals(1.0, getMeasurementValue(actualEvent, "sockets"), "sockets should be 1.0");
    assertTrue(actualEvent.getIsUnmappedGuest(), "Should be unmapped guest");
    assertTrue(actualEvent.getProductTag().contains("RHEL for x86"), "Should have x86 product tag");

    // Then: Verify full event matches expected structure
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), true, false);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedEvent, actualEvent),
        "Full event should match expected structure");
  }

  @TestPlanName("metrics-hbi-virtual-TC002")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForUnmappedGuestFromCpus(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A virtual RHEL for x86 unmapped guest with CPUs set (no threadsPerCore)
    // Note: hypervisorUuid is set to a non-existent hypervisor UUID, making this an unmapped guest
    // Difference from TC001: uses numberOfCpus instead of threadsPerCore for vCPU calculation
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts()
                    .sla("Self-Support")
                    .usage("Development/Test")
                    .isVirtual(true)
                    .product("69")
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("virtual")
                    .arch("x86_64")
                    .coresPerSocket(2)
                    .numberOfSockets(2)
                    .numberOfCpus(4)
                    .hypervisorUuid("non-existent-hypervisor-uuid")
                    .build())
            .toEvent()
            .type(hbiEventType)
            .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
            .buildCreateUpdate();

    // When: HBI event is produced to Kafka
    hostEvents.publish(hbiEvent);

    // Then: Wait for the actual SWatch event from Kafka using minimal key-based matcher
    String instanceId = hbiEvent.getHost().getId().toString();
    List<Event> actualEvents =
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));

    Event actualEvent = actualEvents.getFirst();

    // Then: Assert specific measurement values on ACTUAL event from Kafka: cores=4, sockets=1
    assertEquals(4.0, getMeasurementValue(actualEvent, "cores"), "cores should be 4.0");
    assertEquals(1.0, getMeasurementValue(actualEvent, "sockets"), "sockets should be 1.0");
    assertTrue(actualEvent.getIsUnmappedGuest(), "Should be unmapped guest");
    assertEquals(Event.Sla.SELF_SUPPORT, actualEvent.getSla(), "SLA should be Self-Support");
    assertEquals(Event.Usage.DEVELOPMENT_TEST, actualEvent.getUsage(), "Usage should be Dev/Test");
    assertTrue(actualEvent.getProductTag().contains("RHEL for x86"), "Should have x86 product tag");

    // Then: Verify full event matches expected structure
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), true, false);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedEvent, actualEvent),
        "Full event should match expected structure");
  }

  @TestPlanName("metrics-hbi-virtual-TC003")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForVirtualArmHost(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A virtual RHEL for ARM host event
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts()
                    .sla("Self-Support")
                    .usage("Development/Test")
                    .isVirtual(true)
                    .product("419")
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("virtual")
                    .arch("arm")
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

    // Then: Assert specific measurement values on ACTUAL event from Kafka: cores=4 (physical calc:
    // 2×2),
    // sockets=2
    assertEquals(4.0, getMeasurementValue(actualEvent, "cores"), "cores should be 4.0");
    assertEquals(2.0, getMeasurementValue(actualEvent, "sockets"), "sockets should be 2.0");
    assertTrue(actualEvent.getProductTag().contains("RHEL for ARM"), "Should have ARM product tag");

    // Then: Verify full event matches expected structure
    // Note: defaultFacts() adds product "69", so actual event has both 69 and 419
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent,
            swatchEventType,
            List.of("69", "419"),
            Set.of("RHEL for x86", "RHEL for ARM"),
            false,
            false);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedEvent, actualEvent),
        "Full event should match expected structure");
  }

  @TestPlanName("metrics-hbi-virtual-TC004")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForCloudProviderHost(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A virtual cloud provider (AWS) RHEL for x86 host event
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts()
                    .sla("Self-Support")
                    .usage("Development/Test")
                    .isVirtual(true)
                    .product("69")
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("virtual")
                    .arch("x86_64")
                    .coresPerSocket(8)
                    .numberOfSockets(4)
                    .numberOfCpus(4)
                    .cloudProvider("aws")
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

    // Then: Assert specific measurement values on ACTUAL event from Kafka: sockets=1 (cloud
    // override)
    assertEquals(1.0, getMeasurementValue(actualEvent, "sockets"), "sockets should be 1.0");
    assertEquals(Event.CloudProvider.AWS, actualEvent.getCloudProvider(), "Should be AWS");
    assertEquals(Event.HardwareType.CLOUD, actualEvent.getHardwareType(), "Should be CLOUD type");

    // Then: Verify full event matches expected structure
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedEvent, actualEvent),
        "Full event should match expected structure");
  }

  @TestPlanName("metrics-hbi-virtual-TC005")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForUnmappedGuestWithDefaultThreadsPerCore(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A virtual RHEL for x86 unmapped guest with neither threadsPerCore nor numberOfCpus set
    // Note: hypervisorUuid is set to a non-existent hypervisor UUID, making this an unmapped guest
    // Difference from TC001: neither threadsPerCore nor numberOfCpus is set, so service should use
    // default threadsPerCore=2.0
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .rhsmFacts(
                RhsmFacts.builder()
                    .defaultFacts()
                    .sla("Self-Support")
                    .usage("Development/Test")
                    .isVirtual(true)
                    .product("69")
                    .build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("virtual")
                    .arch("x86_64")
                    .coresPerSocket(2)
                    .numberOfSockets(2)
                    // Note: threadsPerCore is NOT set, numberOfCpus is NOT set
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
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));

    Event actualEvent = actualEvents.getFirst();

    // Then: Assert specific measurement values on ACTUAL event from Kafka
    // With coresPerSocket=2, sockets=2, and default threadsPerCore=2.0:
    // cores = ceil((2 * 2) / 2.0) = ceil(4 / 2.0) = ceil(2.0) = 2
    // sockets = 1 (unmapped virtual RHEL guest rule)
    assertEquals(2.0, getMeasurementValue(actualEvent, "cores"), "cores should be 2.0");
    assertEquals(1.0, getMeasurementValue(actualEvent, "sockets"), "sockets should be 1.0");
    assertTrue(actualEvent.getIsUnmappedGuest(), "Should be unmapped guest");
    assertTrue(actualEvent.getProductTag().contains("RHEL for x86"), "Should have x86 product tag");

    // Then: Verify full event matches expected structure
    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), true, false);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedEvent, actualEvent),
        "Full event should match expected structure");
  }
}
