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

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.Topics;
import com.redhat.swatch.hbi.events.ct.api.MessageValidators;
import com.redhat.swatch.hbi.events.ct.utils.HbiEventHelper;
import com.redhat.swatch.hbi.events.ct.utils.SwatchEventHelper;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
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
      String hbiEventType, String swatchEventType) {
    // Given: A virtual RHEL unmapped guest with threads per core set
    HbiHostCreateUpdateEvent hbiEvent =
        HbiEventHelper.getRhsmHostEvent(
            hbiEventType,
            null,
            List.of("69"),
            true,
            "x86_64",
            OffsetDateTime.now(ZoneOffset.UTC),
            "Self-Support",
            "Development/Test",
            2,
            2,
            4,
            null,
            null);

    Event swatchEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, List.of("69"), Set.of("RHEL for x86"), true, false);

    // When: HBI event is produced to Kafka
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiEvent);

    // Then: Corresponding SWatch event should be produced
    thenSwatchEventsAppear(MessageValidators.swatchEventEquals(swatchEvent));
  }

  @TestPlanName("metrics-hbi-virtual-TC002")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForUnmappedGuestFromCpus(
      String hbiEventType, String swatchEventType) {
    // Given: A virtual RHEL unmapped guest with CPUs set
    HbiHostCreateUpdateEvent hbiEvent =
        HbiEventHelper.getRhsmHostEvent(
            hbiEventType,
            null,
            List.of("69"),
            true,
            "x86_64",
            OffsetDateTime.now(ZoneOffset.UTC),
            "Self-Support",
            "Development/Test",
            2,
            2,
            null,
            4,
            null);

    Event swatchEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, List.of("69"), Set.of("RHEL for x86"), true, false);

    // When: HBI event is produced to Kafka
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiEvent);

    // Then: Corresponding SWatch event should be produced
    thenSwatchEventsAppear(MessageValidators.swatchEventEquals(swatchEvent));
  }

  @TestPlanName("metrics-hbi-virtual-TC003")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForVirtualArmHost(String hbiEventType, String swatchEventType) {
    // Given: A virtual RHEL for ARM host event
    HbiHostCreateUpdateEvent hbiEvent =
        HbiEventHelper.getRhsmHostEvent(
            hbiEventType,
            null,
            List.of("419"),
            true,
            "arm",
            OffsetDateTime.now(ZoneOffset.UTC),
            "Self-Support",
            "Development/Test",
            2,
            2,
            null,
            null,
            null);

    Event swatchEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, List.of("419"), Set.of("RHEL for ARM"), true, false);

    // When: HBI event is produced to Kafka
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiEvent);

    // Then: Corresponding SWatch event should be produced
    thenSwatchEventsAppear(MessageValidators.swatchEventEquals(swatchEvent));
  }

  @TestPlanName("metrics-hbi-virtual-TC004")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldProduceSwatchEventForCloudProviderHost(String hbiEventType, String swatchEventType) {
    // Given: A virtual cloud provider (AWS) host event
    HbiHostCreateUpdateEvent hbiEvent =
        HbiEventHelper.getRhsmHostEvent(
            hbiEventType,
            null,
            List.of("69"),
            true,
            "x86_64",
            OffsetDateTime.now(ZoneOffset.UTC),
            "Self-Support",
            "Development/Test",
            8,
            4,
            null,
            4,
            "aws");

    Event swatchEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, List.of("69"), Set.of("RHEL for x86"), true, false);

    // When: HBI event is produced to Kafka
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiEvent);

    // Then: Corresponding SWatch event should be produced
    thenSwatchEventsAppear(MessageValidators.swatchEventEquals(swatchEvent));
  }
}
