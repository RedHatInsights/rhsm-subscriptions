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
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostDeleteEvent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MetricsHbiDeleteComponentTest extends BaseSMHBIComponentTest {

  @BeforeAll
  static void enableEmitEventsFeatureFlag() {
    unleash.enableFlag(EMIT_EVENTS);
  }

  @AfterAll
  static void disableEmitEventsFeatureFlag() {
    unleash.disableFlag(EMIT_EVENTS);
  }

  @TestPlanName("metrics-hbi-delete-TC001")
  @Test
  void testHbiDeletePhysicalRhsmHostEvent() {
    // Given: An existing physical RHEL host
    HbiHostCreateUpdateEvent hbiEvent = getExistingHostEvent();

    HbiHostDeleteEvent hbiDeleteEvent =
        HbiEventHelper.getDeletedHostEvent(
            hbiEvent.getHost().getId().toString(),
            hbiEvent.getHost().getOrgId(),
            hbiEvent.getHost().getInsightsId(),
            OffsetDateTime.now(ZoneOffset.UTC));

    Event swatchEvent =
        SwatchEventHelper.createExpectedDeletedEvent(
            hbiEvent, hbiDeleteEvent, List.of("69"), Set.of("RHEL for x86"), false, false);

    // When: Delete event is produced to Kafka
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiDeleteEvent);

    // Then: Corresponding SWatch delete event should be produced
    waitForSwatchEvents(MessageValidators.swatchEventEquals(swatchEvent));
  }

  @TestPlanName("metrics-hbi-delete-TC002")
  @Test
  void testHbiDeleteHypervisorHost() {
    // Given: An existing hypervisor with a mapped guest
    List<HbiHostCreateUpdateEvent> hbiEvents = getExistingHypervisorAndGuestEvents();

    HbiHostCreateUpdateEvent hypervisorEvent = hbiEvents.get(0);
    HbiHostCreateUpdateEvent guestEvent = hbiEvents.get(1);

    HbiHostDeleteEvent hbiDeleteEvent =
        HbiEventHelper.getDeletedHostEvent(
            hypervisorEvent.getHost().getId().toString(),
            hypervisorEvent.getHost().getOrgId(),
            hypervisorEvent.getHost().getInsightsId(),
            OffsetDateTime.now(ZoneOffset.UTC));

    Event swatchEventHypervisor =
        SwatchEventHelper.createExpectedDeletedEvent(
            hypervisorEvent, hbiDeleteEvent, List.of("69"), Set.of("RHEL for x86"), false, true);
    Event swatchEventGuest =
        SwatchEventHelper.createExpectedEvent(
            guestEvent, List.of("69"), Set.of("RHEL for x86"), true, false, true);

    // When: Hypervisor delete event is produced to Kafka
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiDeleteEvent);

    // Then: Two SWatch events should be produced (delete hypervisor, update guest to unmapped)
    waitForSwatchEvents(
        MessageValidators.swatchEventEquals(swatchEventHypervisor),
        MessageValidators.swatchEventEquals(swatchEventGuest));
  }

  @TestPlanName("metrics-hbi-delete-TC003")
  @Test
  void testHbiDeleteMappedGuestHost() {
    // Given: An existing hypervisor with a mapped guest
    List<HbiHostCreateUpdateEvent> hbiEvents = getExistingHypervisorAndGuestEvents();

    HbiHostCreateUpdateEvent hypervisorEvent = hbiEvents.get(0);
    HbiHostCreateUpdateEvent guestEvent = hbiEvents.get(1);

    HbiHostDeleteEvent hbiDeleteEvent =
        HbiEventHelper.getDeletedHostEvent(
            guestEvent.getHost().getId().toString(),
            guestEvent.getHost().getOrgId(),
            guestEvent.getHost().getInsightsId(),
            OffsetDateTime.now(ZoneOffset.UTC));

    Event swatchEventGuest =
        SwatchEventHelper.createExpectedDeletedEvent(
            guestEvent, hbiDeleteEvent, List.of("69"), Set.of("RHEL for x86"), false, false);
    Event swatchEventHypervisor =
        SwatchEventHelper.createExpectedEvent(
            hypervisorEvent, List.of("69"), Set.of("RHEL for x86"), false, false, true);

    // When: Mapped guest delete event is produced to Kafka
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiDeleteEvent);

    // Then: Two SWatch events should be produced (delete guest, update hypervisor to
    // non-hypervisor)
    waitForSwatchEvents(
        MessageValidators.swatchEventEquals(swatchEventGuest),
        MessageValidators.swatchEventEquals(swatchEventHypervisor));
  }

  @TestPlanName("metrics-hbi-delete-TC004")
  @Test
  void testHbiDeleteEventWhenGuestHostHasNotBeenSeen() {
    // Given: A delete event for an unseen host
    HbiHostDeleteEvent hbiDeleteEvent =
        HbiEventHelper.getDeletedHostEvent(
            UUID.randomUUID().toString(),
            "12345678",
            UUID.randomUUID().toString(),
            OffsetDateTime.now(ZoneOffset.UTC));

    Event swatchEvent =
        SwatchEventHelper.createExpectedDeletedEvent(
            null, hbiDeleteEvent, List.of("69"), Set.of("RHEL for x86"), false, false);

    // When: Delete event is produced to Kafka
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiDeleteEvent);

    // Then: SWatch delete event should be produced with minimal metadata
    waitForSwatchEvents(MessageValidators.swatchEventEquals(swatchEvent));
  }

  private HbiHostCreateUpdateEvent getExistingHostEvent() {
    HbiHostCreateUpdateEvent hbiEvent =
        HbiEventHelper.getRhsmHostEvent(
            "created",
            null,
            List.of("69"),
            false,
            "x86_64",
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
            hbiEvent, List.of("69"), Set.of("RHEL for x86"), false, false);

    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiEvent);

    waitForSwatchEvents(MessageValidators.swatchEventEquals(swatchEvent));

    return hbiEvent;
  }

  private List<HbiHostCreateUpdateEvent> getExistingHypervisorAndGuestEvents() {
    List<HbiHostCreateUpdateEvent> hbiEvents =
        HbiEventHelper.getHypervisorAndGuestEvents(
            "created",
            List.of("69"),
            OffsetDateTime.now(ZoneOffset.UTC),
            "Self-Support",
            "Development/Test",
            1,
            1,
            1,
            3);

    HbiHostCreateUpdateEvent hypervisorEvent = hbiEvents.get(0);
    HbiHostCreateUpdateEvent guestEvent = hbiEvents.get(1);

    Event swatchEventHypervisor =
        SwatchEventHelper.createExpectedEvent(
            hypervisorEvent, List.of("69"), Set.of("RHEL for x86"), false, false);
    Event swatchEventMappedGuest =
        SwatchEventHelper.createExpectedEvent(
            guestEvent, List.of("69"), Set.of("RHEL for x86"), false, false);
    Event swatchEventUpdatedHypervisor =
        SwatchEventHelper.createExpectedEvent(
            hypervisorEvent, List.of("69"), Set.of("RHEL for x86"), false, true, true);

    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hypervisorEvent);
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, guestEvent);

    waitForSwatchEvents(
        MessageValidators.swatchEventEquals(swatchEventHypervisor),
        MessageValidators.swatchEventEquals(swatchEventMappedGuest),
        MessageValidators.swatchEventEquals(swatchEventUpdatedHypervisor));

    return hbiEvents;
  }
}
