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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CreateUpdateEventIngestionTest extends BaseSMHBIComponentTest {

  @BeforeEach
  void setupTest() {
    unleash.enableFlag(EMIT_EVENTS);
  }

  @AfterEach
  void teardown() {
    unleash.disableFlag(EMIT_EVENTS);
  }

  /*
   * Verify service accepts HBI Create/Update events for a physical x86 host and produce the
   * expected Swatch Event messages.
   *
   * test_steps:
   * 1. Toggle feature flag to allow service to emit swatch events.
   * 2. Send a created/updated message to the HBI event topic to simulate that a host was
   * created in HBI.
   * 3. Flush the outbox table.
   *
   * expected_results:
   * 1. The swatch-metrics-hbi service will ingest the event and should create an outbox
   * record for a Swatch Event message containing the measurements for the host represented
   * by the HBI event. NOTE: We expect the same result regardless of whether the HBI event was
   * host created OR host updated.
   * 2. When the outbox table is flushed, SWatch events are sent for each outbox record
   * that was flushed.
   */
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void testHbiPhysicalRhsmHostEvent(String hbiEventType, String swatchEventType) {
    HbiHostCreateUpdateEvent hbiEvent =
        HbiEventHelper.getRhsmHostEvent(
            hbiEventType,
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
  }

  /*
   * Verify service accepts HBI Create/Update events for a virtual x86 host and produce the
   * expected Swatch Event messages with metrics applied using virtual CPUs from threads per core fact.
   *
   * test_steps:
   * 1. Toggle feature flag to allow service to emit swatch events.
   * 2. Send a created/updated message to the HBI event topic to simulate that a host
   * was created in HBI.
   * 3. Flush the outbox table.
   *
   * expected_results:
   * 1. The swatch-metrics-hbi service will ingest the event and should create an outbox
   * record for a Swatch Event message containing the measurements for the host represented
   * by the HBI event. NOTE: We expect the same result regardless of whether the HBI event was
   * host created OR host updated.
   * 2. When the outbox table is flushed, SWatch events are sent for each outbox record
   * that was flushed.
   */
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void testHbiVirtualRhsmUnmappedGuestHostFromThreadsPerCore(
      String hbiEventType, String swatchEventType) {
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

    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiEvent);

    flushOutbox(1);

    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEvent),
        1);
  }

  /*
   * Verify service accepts HBI Create/Update events for a virtual x86 host and produce the
   * expected Swatch Event messages with metrics applied using virtual CPUs from CPUs fact.
   *
   * test_steps:
   * 1. Toggle feature flag to allow service to emit swatch events.
   * 2. Send a created/updated message to the HBI event topic to simulate that a host
   * was created in HBI.
   * 3. Flush the outbox table.
   *
   * expected_results:
   * 1. The swatch-metrics-hbi service will ingest the event and should create an outbox
   * record for a Swatch Event message containing the measurements for the host represented
   * by the HBI event. NOTE: We expect the same result regardless of whether the HBI event was
   * host created OR host updated.
   * 2. When the outbox table is flushed, SWatch events are sent for each outbox record
   * that was flushed.
   */
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void testHbiVirtualRhsmUnmappedGuestHostFromCpus(String hbiEventType, String swatchEventType) {
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

    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiEvent);

    flushOutbox(1);

    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEvent),
        1);
  }

  /*
   * Verify service accepts HBI Create/Update events for a virtual RHEL for ARM host and produce the
   * expected Swatch Event messages with metrics applied when no CPUs or threads facts found.
   *
   * test_steps:
   * 1. Toggle feature flag to allow service to emit swatch events.
   * 2. Send a created/updated message to the HBI event topic to simulate that a host
   * was created in HBI.
   * 3. Flush the outbox table.
   *
   * expected_results:
   * 1. The swatch-metrics-hbi service will ingest the event and should create an outbox
   * record for a Swatch Event message containing the measurements for the host represented
   * by the HBI event. NOTE: We expect the same result regardless of whether the HBI event was
   * host created OR host updated.
   * 2. When the outbox table is flushed, SWatch events are sent for each outbox record
   * that was flushed.
   */
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void testHbiVirtualArmHostEvent(String hbiEventType, String swatchEventType) {
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

    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiEvent);

    flushOutbox(1);

    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEvent),
        1);
  }

  /*
   * Verify service accepts HBI Create/Update events for a virtual cloud provider host and produce
   * the expected Swatch Event messages.
   *
   * test_steps:
   * 1. Toggle feature flag to allow service to emit swatch events.
   * 2. Send a created/updated message to the HBI event topic to simulate that a host
   * was created in HBI.
   * 3. Flush the outbox table.
   *
   * expected_results:
   * 1. The swatch-metrics-hbi service will ingest the event and should create an outbox
   * record for a Swatch Event message containing the measurements for the host represented
   * by the HBI event. NOTE: We expect the same result regardless of whether the HBI event was
   * host created OR host updated.
   * 2. When the outbox table is flushed, SWatch events are sent for each outbox record
   * that was flushed.
   */
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void testHbiVirtualCloudProviderHostEvent(String hbiEventType, String swatchEventType) {
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

    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiEvent);

    flushOutbox(1);

    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEvent),
        1);
  }

  /*
   * Verify service transitions a host to a hypervisor after the first guest becomes known.
   *
   * test_steps:
   * 1. Toggle feature flag to allow service to emit swatch events.
   * 2. Send a message to the HBI event topic to simulate that a physical host
   * was created in HBI.
   * 3. Send a message to the HBI event topic to simulate that the guest is now
   * known in HBI.
   * 4. Flush the outbox table.
   *
   * expected_results:
   * 1. The swatch-metrics-hbi service will ingest the physical host event and create
   * an outbox record for a Swatch Event message representing a physical host, with
   * measurement reflecting a physical host state.
   * 2. The swatch-metrics-hbi service will ingest the guest event and will create an
   * outbox record for two Swatch event messages:
   *    a) An event representing a mapped guest with correct measurements.
   *    b) An event representing the changes to the existing physical host which is now
   *    marked as hypervisor.
   * 3. When the outbox table is flushed, SWatch events are sent for each outbox
   * record that was flushed.
   */
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void testHbiPhysicalHypervisorHostTransitionOnceFirstGuestIsKnown(
      String hbiEventType, String swatchEventType) {
    List<HbiHostCreateUpdateEvent> hbiEvents =
        HbiEventHelper.getHypervisorAndGuestEvents(
            hbiEventType,
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
  }

  /*
   * Verify service transitions a guest from unmapped to mapped when the hypervisor is known.
   *
   * test_steps:
   * 1. Toggle feature flag to allow service to emit swatch events.
   * 2. Send a message to the HBI event topic to simulate that an unmapped guest
   * was created in HBI.
   * 3. Send a message to the HBI event topic to simulate that the guest's hypervisor
   * is known in HBI.
   * 4. Flush the outbox table.
   *
   * expected_results:
   * 1. The swatch-metrics-hbi service will ingest the guest event and create
   * an outbox record for a Swatch Event message representing a mapped guest host,
   * with measurement reflecting the mapped state.
   * 2. The swatch-metrics-hbi service will ingest the hypervisor event and will
   * create two outbox records:
   *    a) A record containing a SWatch event that represents the new hypervisor.
   *    b) A record with a SWatch event representing the newly updated values for the
   *    unmapped guest.
   * 3. When the outbox table is flushed, SWatch events are sent for each outbox
   * record that was flushed.
   */
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void testHbiVirualUnmappedGuestToMappedGuestTransition(
      String hbiEventType, String swatchEventType) {
    List<HbiHostCreateUpdateEvent> hbiEvents =
        HbiEventHelper.getHypervisorAndGuestEvents(
            hbiEventType,
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

    Event swatchEventUnmappedGuest =
        SwatchEventHelper.createExpectedEvent(
            guestEvent, List.of("69"), Set.of("RHEL for x86"), true, false);
    Event swatchEventHypervisor =
        SwatchEventHelper.createExpectedEvent(
            hypervisorEvent, List.of("69"), Set.of("RHEL for x86"), false, true);
    Event swatchEventUpdatedMappedGuest =
        SwatchEventHelper.createExpectedEvent(
            guestEvent, List.of("69"), Set.of("RHEL for x86"), false, false, true);

    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, guestEvent);
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hypervisorEvent);

    flushOutbox(3);

    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEventUnmappedGuest),
        1);
    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEventHypervisor),
        1);
    kafkaBridge.waitForKafkaMessage(
        Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.swatchEventEquals(swatchEventUpdatedMappedGuest),
        1);
  }
}
