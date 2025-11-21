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

import com.redhat.swatch.component.tests.utils.Topics;
import com.redhat.swatch.hbi.events.ct.HbiEventHelper;
import com.redhat.swatch.hbi.events.ct.SwatchEventHelper;
import com.redhat.swatch.hbi.events.ct.api.MessageValidators;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostDeleteEvent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeleteEventIngestionTest extends BaseSMHBIComponentTest {

  @BeforeEach
  void setupTest() {
    unleash.enableFlag(EMIT_EVENTS);
  }

  @AfterEach
  void teardown() {
    unleash.disableFlag(EMIT_EVENTS);
  }

  /*
   * Verify service will accept an HBI delete event and produces an outbox record containing
   * the correct Swatch event message JSON.
   *
   * test_steps:
   * 1. Toggle feature flag to allow service to emit swatch events.
   * 2. Send a created message to the HBI event topic to simulate that a host was created
   * in HBI.
   * 3. Send a delete event to the HBI event topic.
   * 4. Flush the outbox table.
   *
   * expected_results:
   * 1. The swatch-metrics-hbi service will ingest the delete event and should create an
   * outbox record containing the Swatch event JSON with an INSTANCE_DELETED type and should
   * contain the last known measurements for the host represented by the HBI event.
   * 2. When the outbox table is flushed, SWatch events are sent for the outbox record that
   * was flushed.
   */
  @Test
  void testHbiDeletePhysicalRhsmHostEvent() {
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

    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiDeleteEvent);

    flushOutbox(1);

    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEvent),
        1);
  }

  /*
   * Verify that when the service processes an HBI delete event for a hypervisor, it should emit
   * a swatch INSTANCE_DELETED event for the hypervisor, and an INSTANCE_UPDATED swatch event for
   * all mapped guests.
   *
   * test_steps:
   * 1. Toggle feature flag to allow service to emit swatch events.
   * 2. Send a created message to the HBI event topic to initialize the host relationships and
   * map the hypervisor and guest.
   * 3. Send a delete event message to the HBI event topic for the hypervisor.
   * 4. Flush the outbox table.
   *
   * expected_results:
   * 1. The swatch-metrics-hbi service will ingest the delete event and create an outbox
   * record for the following swatch events:
   *    a) A swatch event with type INSTANCE_DELETED for the hypervisor and should contain the
   *    last known measurements and metadata for the hypervisor.
   *    b) A swatch event with type INSTANCE_UPDATED for the guest and should contain the
   *    updated measurements and metadata for the guest. The guest should now be unmapped, and
   *    its measurements should reflect this change.
   * 2. When the outbox table is flushed, SWatch events are sent for each outbox record that
   * was flushed.
   */
  @Test
  void testHbiDeleteHypervisorHost() {
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

    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiDeleteEvent);

    flushOutbox(2);

    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEventHypervisor),
        1);
    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEventGuest),
        1);
  }

  /*
   * Verify that when the service processes an HBI delete event for a mapped guest, it should emit
   * a swatch INSTANCE_DELETED event for the guest, and an INSTANCE_UPDATED swatch event for
   * the hypervisor.
   *
   * test_steps:
   * 1. Toggle feature flag to allow service to emit swatch events.
   * 2. Send a created message to the HBI event topic to initialize the host relationships and
   * map the hypervisor and guest.
   * 3. Send a delete event message to the HBI event topic for the guest.
   * 4. Flush the outbox table.
   *
   * expected_results:
   * 1. The swatch-metrics-hbi service will ingest the delete event and create an outbox
   * record for the following swatch events:
   *    a) A swatch event with type INSTANCE_DELETED for the guest and should contain the
   *    last known measurements and metadata for the guest.
   *    b) A swatch event with type INSTANCE_UPDATED for the hypervisor and should contain the
   *    updated measurements and metadata for the hypervisor. The hypervisor should no longer be
   *    identified as a hypervisor (as per current nightly tally behavior).
   * 2. When the outbox table is flushed, SWatch events are sent for each outbox record that
   * was flushed.
   */
  @Test
  void testHbiDeleteMappedGuestHost() {
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

    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiDeleteEvent);

    flushOutbox(2);

    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEventGuest),
        1);
    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEventHypervisor),
        1);
  }

  /*
   * Verify that when the service processes an HBI delete event for a host that has not been seen
   * (no created/updated event has been processed), an outbox record is created containing the JSON
   * for a Swatch INSTANCE_DELETED event. This JSON should contain the bare minimum host metadata.
   * In this case, there was no existing host relationship that stored the last known metadata.
   *
   * test_steps:
   * 1. Toggle feature flag to allow service to emit swatch events.
   * 2. Send a delete event message to the HBI event topic for the unseen guest.
   * 3. Flush the outbox table.
   *
   * expected_results:
   * 1. The swatch-metrics-hbi service will ingest the delete event and should create an outbox
   * record with swatch event JSON containing the bare minimum host metadata provided by the
   * incoming HBI event.
   * 2. When the outbox table is flushed, SWatch events are sent for the outbox record that was flushed.
   */
  @Test
  void testHbiDeleteEventWhenGuestHostHasNotBeenSeen() {
    HbiHostDeleteEvent hbiDeleteEvent =
        HbiEventHelper.getDeletedHostEvent(
            UUID.randomUUID().toString(),
            "12345678",
            UUID.randomUUID().toString(),
            OffsetDateTime.now(ZoneOffset.UTC));

    Event swatchEvent =
        SwatchEventHelper.createExpectedDeletedEvent(
            null, hbiDeleteEvent, List.of("69"), Set.of("RHEL for x86"), false, false);

    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiDeleteEvent);

    flushOutbox(1);

    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEvent),
        1);
  }

  // --- helpers: get existing HBI events for delete tests ---

  /*
   * Produces a single "created" HBI host, flushes outbox, and waits for the expected
   * SWatch event. Returns the original HBI event so the caller can build
   * a matching delete event.
   */
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

    flushOutbox(1);

    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEvent),
        1);

    return hbiEvent;
  }

  /*
   * Produces a hypervisor and a mapped guest "created" HBI events, flushes outbox,
   * and waits for the expected Swatch events (hypervisor, guest, updated hypervisor).
   * Returns both HBI events in order: [hypervisor, guest].
   */
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

    flushOutbox(3);

    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEventHypervisor),
        1);
    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEventMappedGuest),
        1);
    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEventUpdatedHypervisor),
        1);

    return hbiEvents;
  }
}
