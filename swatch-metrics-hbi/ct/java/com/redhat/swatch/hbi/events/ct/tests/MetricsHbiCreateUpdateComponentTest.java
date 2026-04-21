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

class MetricsHbiCreateUpdateComponentTest extends BaseSMHBIComponentTest {

  @BeforeAll
  static void enableEmitEventsFeatureFlag() {
    unleash.enableFlag(EMIT_EVENTS);
  }

  @AfterAll
  static void disableEmitEventsFeatureFlag() {
    unleash.disableFlag(EMIT_EVENTS);
  }

  @TestPlanName("metrics-hbi-create-update-TC001")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void testHbiPhysicalRhsmHostEvent(String hbiEventType, String swatchEventType) {
    // Given: A physical RHEL for x86 host event
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

    // When: HBI event is produced to Kafka
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hbiEvent);

    // Then: Corresponding SWatch event should be produced
    waitForSwatchEvents(MessageValidators.swatchEventEquals(swatchEvent));
  }

  @TestPlanName("metrics-hbi-create-update-TC002")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void testHbiVirtualRhsmUnmappedGuestHostFromThreadsPerCore(
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
    waitForSwatchEvents(MessageValidators.swatchEventEquals(swatchEvent));
  }

  @TestPlanName("metrics-hbi-create-update-TC003")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void testHbiVirtualRhsmUnmappedGuestHostFromCpus(String hbiEventType, String swatchEventType) {
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
    waitForSwatchEvents(MessageValidators.swatchEventEquals(swatchEvent));
  }

  @TestPlanName("metrics-hbi-create-update-TC004")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void testHbiVirtualArmHostEvent(String hbiEventType, String swatchEventType) {
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
    waitForSwatchEvents(MessageValidators.swatchEventEquals(swatchEvent));
  }

  @TestPlanName("metrics-hbi-create-update-TC005")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void testHbiVirtualCloudProviderHostEvent(String hbiEventType, String swatchEventType) {
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
    waitForSwatchEvents(MessageValidators.swatchEventEquals(swatchEvent));
  }

  @TestPlanName("metrics-hbi-create-update-TC006")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void testHbiPhysicalHypervisorHostTransitionOnceFirstGuestIsKnown(
      String hbiEventType, String swatchEventType) {
    // Given: A physical hypervisor host and a mapped guest host
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

    // When: Hypervisor and guest events are produced to Kafka
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hypervisorEvent);
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, guestEvent);

    // Then: Three SWatch events should be produced (hypervisor, guest, updated hypervisor)
    waitForSwatchEvents(
        MessageValidators.swatchEventEquals(swatchEventHypervisor),
        MessageValidators.swatchEventEquals(swatchEventMappedGuest),
        MessageValidators.swatchEventEquals(swatchEventUpdatedHypervisor));
  }

  @TestPlanName("metrics-hbi-create-update-TC007")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void testHbiVirualUnmappedGuestToMappedGuestTransition(
      String hbiEventType, String swatchEventType) {
    // Given: A virtual unmapped guest and a hypervisor host
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

    // When: Guest event is produced first, then hypervisor event
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, guestEvent);
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hypervisorEvent);

    // Then: Three SWatch events should be produced (unmapped guest, hypervisor, mapped guest)
    waitForSwatchEvents(
        MessageValidators.swatchEventEquals(swatchEventUnmappedGuest),
        MessageValidators.swatchEventEquals(swatchEventHypervisor),
        MessageValidators.swatchEventEquals(swatchEventUpdatedMappedGuest));
  }

  @TestPlanName("metrics-hbi-create-update-TC008")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void testHbiVirtualMappedGuestRemappedFromOneHypervisorToAnother(
      String hbiEventType, String swatchEventType) {
    // Given: Hypervisor A with mapped guest, and hypervisor B with no guests
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    List<HbiHostCreateUpdateEvent> initialEvents =
        HbiEventHelper.getHypervisorAndGuestEvents(
            hbiEventType, List.of("69"), now, "Self-Support", "Development/Test", 1, 1, 1, 3);

    HbiHostCreateUpdateEvent hypervisorAEvent = initialEvents.get(0);
    HbiHostCreateUpdateEvent guestEvent = initialEvents.get(1);

    HbiHostCreateUpdateEvent hypervisorBEvent =
        HbiEventHelper.getRhsmHostEvent(
            hbiEventType,
            null,
            List.of("69"),
            false,
            "x86_64",
            now,
            "Self-Support",
            "Development/Test",
            1,
            1,
            null,
            null,
            null);
    String hypervisorBSubManId = hypervisorBEvent.getHost().getSubscriptionManagerId();

    Event swatchHypervisorA =
        SwatchEventHelper.createExpectedEvent(
            hypervisorAEvent, List.of("69"), Set.of("RHEL for x86"), false, false);
    Event swatchGuestMapped =
        SwatchEventHelper.createExpectedEvent(
            guestEvent, List.of("69"), Set.of("RHEL for x86"), false, false);
    Event swatchHypervisorAUpdated =
        SwatchEventHelper.createExpectedEvent(
            hypervisorAEvent, List.of("69"), Set.of("RHEL for x86"), false, true, true);
    Event swatchHypervisorB =
        SwatchEventHelper.createExpectedEvent(
            hypervisorBEvent, List.of("69"), Set.of("RHEL for x86"), false, false);

    // When: Phase 1 - Hypervisor A, guest, and hypervisor B events are produced
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hypervisorAEvent);
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, guestEvent);
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, hypervisorBEvent);

    // Then: Four SWatch events should be produced
    waitForSwatchEvents(
        MessageValidators.swatchEventEquals(swatchHypervisorA),
        MessageValidators.swatchEventEquals(swatchGuestMapped),
        MessageValidators.swatchEventEquals(swatchHypervisorAUpdated),
        MessageValidators.swatchEventEquals(swatchHypervisorB));

    // Given: Guest is re-mapped from hypervisor A to hypervisor B
    guestEvent.getHost().getSystemProfile().put("virtual_host_uuid", hypervisorBSubManId);
    guestEvent.setType("updated");

    Event swatchGuestRemapped =
        SwatchEventHelper.createExpectedEvent(
            guestEvent, List.of("69"), Set.of("RHEL for x86"), false, false, true);
    Event swatchHypervisorBUpdated =
        SwatchEventHelper.createExpectedEvent(
            hypervisorBEvent, List.of("69"), Set.of("RHEL for x86"), false, true, true);

    // When: Phase 2 - Updated guest event is produced with new hypervisor mapping
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, guestEvent);

    // Then: Two SWatch events should be produced (re-mapped guest, updated hypervisor B)
    waitForSwatchEvents(
        MessageValidators.swatchEventEquals(swatchGuestRemapped),
        MessageValidators.swatchEventEquals(swatchHypervisorBUpdated));
  }
}
