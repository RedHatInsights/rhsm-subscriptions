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

    // Then: Corresponding SWatch event should be produced
    Event swatchEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);

    thenSwatchEventsAppear(MessageValidators.swatchEventEquals(swatchEvent));
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

    // Then: Corresponding SWatch event should be produced with sockets rounded up to 4
    Event swatchEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);

    thenSwatchEventsAppear(MessageValidators.swatchEventEquals(swatchEvent));
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

    // Then: Corresponding SWatch event should be produced with rhel-for-x86-els-converted tag
    Event swatchEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent,
            swatchEventType,
            List.of("69", "204"), // Both products from defaultFacts() and explicit product()
            Set.of("rhel-for-x86-els-converted"),
            false,
            false);

    thenSwatchEventsAppear(MessageValidators.swatchEventEquals(swatchEvent));
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

    // Then: Corresponding SWatch event should be produced with cores and sockets forced to 0
    Event swatchEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);

    thenSwatchEventsAppear(MessageValidators.swatchEventEquals(swatchEvent));
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

    // Then: Corresponding SWatch event should be produced with only socket measurements
    Event swatchEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);

    thenSwatchEventsAppear(MessageValidators.swatchEventEquals(swatchEvent));
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

    // Then: Corresponding SWatch event should be produced with only cores measurements
    Event swatchEvent =
        SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);

    thenSwatchEventsAppear(MessageValidators.swatchEventEquals(swatchEvent));
  }
}
