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
  void shouldProduceUngroupedTagForQpcRhelHostWithoutMatchingArch(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A QPC-detected RHEL host with no architecture-specific variant
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .qpcFacts(QpcFacts.builder().product("RHEL").build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
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

    // Then: The RHEL and RHEL Ungrouped tags are emitted
    String instanceId = hbiEvent.getHost().getId().toString();
    List<Event> actualEvents =
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));

    assertEquals(1, actualEvents.size(), "Expected exactly one matching SWatch event");
    Event actualEvent = actualEvents.getFirst();
    assertEquals(Set.of("RHEL", "RHEL Ungrouped"), actualEvent.getProductTag());
    assertEquals(List.of(), actualEvent.getProductIds());
    assertEquals(2.0, getMeasurementValue(actualEvent, "sockets"));
    assertEquals(4.0, getMeasurementValue(actualEvent, "cores"));
    assertFalse(actualEvent.getIsVirtual(), "Should be a physical host");

    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of(), Set.of("RHEL", "RHEL Ungrouped"), false, false);
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
    assertEquals(Set.of("RHEL", "RHEL Ungrouped", derivedVariant), actualEvent.getProductTag());
    assertEquals(List.of(), actualEvent.getProductIds());
    assertEquals(2.0, getMeasurementValue(actualEvent, "sockets"));
    assertEquals(4.0, getMeasurementValue(actualEvent, "cores"));
    assertFalse(actualEvent.getIsVirtual(), "Should be a physical host");

    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent,
            swatchEventType,
            List.of(),
            Set.of("RHEL", "RHEL Ungrouped", derivedVariant),
            false,
            false);
    assertTrue(SwatchEventHelper.eventsMatch(expectedEvent, actualEvent));
  }
}
