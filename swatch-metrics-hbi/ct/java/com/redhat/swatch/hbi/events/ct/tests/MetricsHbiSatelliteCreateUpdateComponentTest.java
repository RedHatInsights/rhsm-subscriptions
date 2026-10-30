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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.SatelliteFacts;
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

class MetricsHbiSatelliteCreateUpdateComponentTest extends BaseSMHBIComponentTest {

  @BeforeAll
  static void enableEmitEventsFeatureFlag() {
    unleash.enableFlag(EMIT_EVENTS);
  }

  @AfterAll
  static void disableEmitEventsFeatureFlag() {
    unleash.disableFlag(EMIT_EVENTS);
  }

  @TestPlanName("metrics-hbi-satellite-TC001")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForSatelliteReportedHost(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A physical host with Satellite role facts and installed Satellite product 250
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .subscriptionManagerId(UUID.randomUUID().toString())
            .satelliteFacts(
                SatelliteFacts.builder().role("Red Hat Enterprise Linux Server").build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
                    .arch("x86_64")
                    .coresPerSocket(4)
                    .numberOfSockets(2)
                    .installedProductId("250")
                    .build())
            .toEvent()
            .type(hbiEventType)
            .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
            .buildCreateUpdate();

    // When: HBI event is produced to Kafka
    hostEvents.publish(hbiEvent);

    // Then: Corresponding SWatch event has the Satellite Server tag and physical measurements
    String instanceId = hbiEvent.getHost().getId().toString();
    List<Event> actualEvents =
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));

    assertEquals(1, actualEvents.size(), "Expected exactly one matching SWatch event");
    Set<String> expectedTags = Set.of("Satellite Server");
    List<String> expectedProductIds = List.of("250");

    Event actualEvent = actualEvents.getFirst();
    assertEquals(expectedTags, actualEvent.getProductTag());
    assertEquals(expectedProductIds, actualEvent.getProductIds());
    assertEquals(2.0, SwatchEventHelper.getMeasurementValue(actualEvent, "sockets"));
    assertEquals(8.0, SwatchEventHelper.getMeasurementValue(actualEvent, "cores"));
    assertFalse(actualEvent.getIsVirtual(), "Should be a physical host");

    Event expectedEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, expectedProductIds, expectedTags, false, false);
    assertTrue(SwatchEventHelper.eventsMatch(expectedEvent, actualEvent));
  }
}
