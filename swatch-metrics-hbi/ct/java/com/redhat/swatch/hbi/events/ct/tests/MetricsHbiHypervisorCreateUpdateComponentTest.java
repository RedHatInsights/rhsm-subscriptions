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

import com.redhat.swatch.component.tests.api.DefaultMessageValidator;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.HostBuilder;
import com.redhat.swatch.component.tests.api.hbi.HostTemplates;
import com.redhat.swatch.hbi.events.ct.utils.SwatchEventHelper;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.normalization.NormalizedEventType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MetricsHbiHypervisorCreateUpdateComponentTest extends BaseSMHBIComponentTest {

  @BeforeAll
  static void enableEmitEventsFeatureFlag() {
    unleash.enableFlag(EMIT_EVENTS);
  }

  @AfterAll
  static void disableEmitEventsFeatureFlag() {
    unleash.disableFlag(EMIT_EVENTS);
  }

  @TestPlanName("metrics-hbi-hypervisor-TC001")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldTransitionPhysicalHostToHypervisorWhenGuestIsKnown(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A physical hypervisor host and a mapped guest host
    OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);
    HypervisorGuestEvents hosts = createHypervisorAndGuest(hbiEventType, timestamp);
    HbiHostCreateUpdateEvent hypervisorEvent = hosts.hypervisorEvent();
    HbiHostCreateUpdateEvent guestEvent = hosts.guestEvent();
    String hypervisorSubManId = hypervisorEvent.getHost().getSubscriptionManagerId();

    Event swatchEventHypervisor =
        SwatchEventHelper.createExpectedEvent(
            hypervisorEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);
    Event swatchEventMappedGuest =
        SwatchEventHelper.createExpectedEvent(
            guestEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);
    Event swatchEventUpdatedHypervisor =
        SwatchEventHelper.createExpectedEvent(
            hypervisorEvent,
            NormalizedEventType.INSTANCE_UPDATED,
            List.of("69"),
            Set.of("RHEL for x86"),
            false,
            true);

    // When: Hypervisor and guest events are produced to Kafka
    hostEvents.publish(hypervisorEvent);
    hostEvents.publish(guestEvent);

    // Then: Three SWatch events should be produced (hypervisor, guest, updated hypervisor)
    List<Event> actualEvents =
        thenSwatchEventsAppear(
            swatchEventMatchesExpectedState(swatchEventHypervisor),
            swatchEventMatchesExpectedState(swatchEventMappedGuest),
            swatchEventMatchesExpectedState(swatchEventUpdatedHypervisor));
    assertFalse(actualEvents.get(0).getIsVirtual(), "Hypervisor should be physical");
    assertTrue(actualEvents.get(1).getIsVirtual(), "Mapped guest should be virtual");
    assertFalse(actualEvents.get(1).getIsUnmappedGuest(), "Guest should be mapped");
    assertEquals(Optional.of(hypervisorSubManId), actualEvents.get(1).getHypervisorUuid());
    assertTrue(actualEvents.get(2).getIsHypervisor(), "Hypervisor should be marked after mapping");
    assertMatches(swatchEventHypervisor, actualEvents.get(0));
    assertMatches(swatchEventMappedGuest, actualEvents.get(1));
    assertMatches(swatchEventUpdatedHypervisor, actualEvents.get(2));
  }

  @TestPlanName("metrics-hbi-hypervisor-TC002")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldTransitionUnmappedGuestToMappedGuest(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: A virtual unmapped guest and a hypervisor host
    OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);
    HypervisorGuestEvents hosts = createHypervisorAndGuest(hbiEventType, timestamp);
    HbiHostCreateUpdateEvent hypervisorEvent = hosts.hypervisorEvent();
    HbiHostCreateUpdateEvent guestEvent = hosts.guestEvent();
    String hypervisorSubManId = hypervisorEvent.getHost().getSubscriptionManagerId();

    Event swatchEventUnmappedGuest =
        SwatchEventHelper.createExpectedEvent(
            guestEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), true, false);
    Event swatchEventHypervisor =
        SwatchEventHelper.createExpectedEvent(
            hypervisorEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, true);
    Event swatchEventUpdatedMappedGuest =
        SwatchEventHelper.createExpectedEvent(
            guestEvent,
            NormalizedEventType.INSTANCE_UPDATED,
            List.of("69"),
            Set.of("RHEL for x86"),
            false,
            false);

    // When: Guest event is produced first, then hypervisor event
    hostEvents.publish(guestEvent);
    hostEvents.publish(hypervisorEvent);

    // Then: Three SWatch events should be produced (unmapped guest, hypervisor, mapped guest)
    List<Event> actualEvents =
        thenSwatchEventsAppear(
            swatchEventMatchesExpectedState(swatchEventUnmappedGuest),
            swatchEventMatchesExpectedState(swatchEventHypervisor),
            swatchEventMatchesExpectedState(swatchEventUpdatedMappedGuest));
    assertTrue(actualEvents.getFirst().getIsVirtual(), "Guest should be virtual");
    assertTrue(actualEvents.get(0).getIsUnmappedGuest(), "Guest should initially be unmapped");
    assertEquals(Optional.of(hypervisorSubManId), actualEvents.get(0).getHypervisorUuid());
    assertTrue(actualEvents.get(1).getIsHypervisor(), "Hypervisor should be marked after mapping");
    assertFalse(actualEvents.get(2).getIsUnmappedGuest(), "Guest should be mapped");
    assertEquals(Optional.of(hypervisorSubManId), actualEvents.get(2).getHypervisorUuid());
    assertMatches(swatchEventUnmappedGuest, actualEvents.get(0));
    assertMatches(swatchEventHypervisor, actualEvents.get(1));
    assertMatches(swatchEventUpdatedMappedGuest, actualEvents.get(2));
  }

  @TestPlanName("metrics-hbi-hypervisor-TC003")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldRemapGuestFromOneHypervisorToAnother(
      String hbiEventType, NormalizedEventType swatchEventType) {
    // Given: Hypervisor A with mapped guest, and hypervisor B with no guests
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    HypervisorGuestEvents hosts = createHypervisorAndGuest(hbiEventType, now);
    HbiHostCreateUpdateEvent hypervisorAEvent = hosts.hypervisorEvent();
    HostBuilder guestHost = hosts.guestHost();
    HbiHostCreateUpdateEvent guestEvent = hosts.guestEvent();
    HbiHostCreateUpdateEvent hypervisorBEvent =
        hostEvents
            .createHost(orgId)
            .apply(HostTemplates.conduitPhysicalRhelSelfSupportDevTest(1, 1))
            .toEvent()
            .type(hbiEventType)
            .timestamp(now)
            .buildCreateUpdate();
    String hypervisorBSubManId = hypervisorBEvent.getHost().getSubscriptionManagerId();

    Event swatchHypervisorA =
        SwatchEventHelper.createExpectedEvent(
            hypervisorAEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);
    Event swatchGuestMapped =
        SwatchEventHelper.createExpectedEvent(
            guestEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);
    Event swatchHypervisorAUpdated =
        SwatchEventHelper.createExpectedEvent(
            hypervisorAEvent,
            NormalizedEventType.INSTANCE_UPDATED,
            List.of("69"),
            Set.of("RHEL for x86"),
            false,
            true);
    Event swatchHypervisorB =
        SwatchEventHelper.createExpectedEvent(
            hypervisorBEvent, swatchEventType, List.of("69"), Set.of("RHEL for x86"), false, false);

    // When: Phase 1 - Hypervisor A, guest, and hypervisor B events are produced
    hostEvents.publish(hypervisorAEvent);
    hostEvents.publish(guestEvent);
    hostEvents.publish(hypervisorBEvent);

    // Then: Four SWatch events should be produced
    List<Event> actualEvents =
        thenSwatchEventsAppear(
            swatchEventMatchesExpectedState(swatchHypervisorA),
            swatchEventMatchesExpectedState(swatchGuestMapped),
            swatchEventMatchesExpectedState(swatchHypervisorAUpdated),
            swatchEventMatchesExpectedState(swatchHypervisorB));
    assertTrue(actualEvents.get(1).getIsVirtual(), "Mapped guest should be virtual");
    assertFalse(actualEvents.get(1).getIsUnmappedGuest(), "Guest should be mapped to hypervisor A");
    assertEquals(
        Optional.of(hypervisorAEvent.getHost().getSubscriptionManagerId()),
        actualEvents.get(1).getHypervisorUuid());
    assertTrue(
        actualEvents.get(2).getIsHypervisor(), "Hypervisor A should be marked as hypervisor");
    assertFalse(actualEvents.get(3).getIsHypervisor(), "Hypervisor B should not yet have guests");
    assertMatches(swatchHypervisorA, actualEvents.get(0));
    assertMatches(swatchGuestMapped, actualEvents.get(1));
    assertMatches(swatchHypervisorAUpdated, actualEvents.get(2));
    assertMatches(swatchHypervisorB, actualEvents.get(3));

    // Given: Guest is re-mapped from hypervisor A to hypervisor B
    guestHost.systemProfileFacts(
        guestHost.getSystemProfileFacts().toBuilder().hypervisorUuid(hypervisorBSubManId).build());
    HbiHostCreateUpdateEvent remappedGuestEvent =
        guestHost.toEvent().type("updated").timestamp(now).buildCreateUpdate();

    Event swatchGuestRemapped =
        SwatchEventHelper.createExpectedEvent(
            remappedGuestEvent,
            NormalizedEventType.INSTANCE_UPDATED,
            List.of("69"),
            Set.of("RHEL for x86"),
            false,
            false);
    Event swatchHypervisorBUpdated =
        SwatchEventHelper.createExpectedEvent(
            hypervisorBEvent,
            NormalizedEventType.INSTANCE_UPDATED,
            List.of("69"),
            Set.of("RHEL for x86"),
            false,
            true);

    // When: Phase 2 - Updated guest event is produced with new hypervisor mapping
    hostEvents.publish(remappedGuestEvent);

    // Then: Two SWatch events should be produced (re-mapped guest, updated hypervisor B)
    actualEvents =
        thenSwatchEventsAppear(
            swatchEventMatchesExpectedState(swatchGuestRemapped, true),
            swatchEventMatchesExpectedState(swatchHypervisorBUpdated));
    assertTrue(actualEvents.getFirst().getIsVirtual(), "Remapped guest should remain virtual");
    assertFalse(actualEvents.get(0).getIsUnmappedGuest(), "Guest should be mapped to hypervisor B");
    assertEquals(Optional.of(hypervisorBSubManId), actualEvents.get(0).getHypervisorUuid());
    assertTrue(
        actualEvents.get(1).getIsHypervisor(), "Hypervisor B should be marked as hypervisor");
    assertMatches(swatchGuestRemapped, actualEvents.get(0));
    assertMatches(swatchHypervisorBUpdated, actualEvents.get(1));
  }

  private HypervisorGuestEvents createHypervisorAndGuest(
      String hbiEventType, OffsetDateTime timestamp) {
    HostBuilder hypervisorHost =
        hostEvents
            .createHost(orgId)
            .apply(HostTemplates.conduitPhysicalRhelSelfSupportDevTest(1, 1));
    HbiHostCreateUpdateEvent hypervisorEvent =
        hypervisorHost.toEvent().type(hbiEventType).timestamp(timestamp).buildCreateUpdate();
    HostBuilder guestHost =
        hostEvents
            .createHost(orgId)
            .apply(
                HostTemplates.conduitVirtualRhelGuestSelfSupportDevTest(
                    1, 3, hypervisorEvent.getHost().getSubscriptionManagerId()));
    HbiHostCreateUpdateEvent guestEvent =
        guestHost.toEvent().type(hbiEventType).timestamp(timestamp).buildCreateUpdate();
    return new HypervisorGuestEvents(hypervisorEvent, guestHost, guestEvent);
  }

  private record HypervisorGuestEvents(
      HbiHostCreateUpdateEvent hypervisorEvent,
      HostBuilder guestHost,
      HbiHostCreateUpdateEvent guestEvent) {}

  private DefaultMessageValidator<Event> swatchEventMatchesExpectedState(Event expectedEvent) {
    return swatchEventMatchesExpectedState(expectedEvent, false);
  }

  private DefaultMessageValidator<Event> swatchEventMatchesExpectedState(
      Event expectedEvent, boolean matchHypervisorUuid) {
    return new DefaultMessageValidator<>(
        actualEvent ->
            expectedEvent.getOrgId().equals(actualEvent.getOrgId())
                && expectedEvent.getInstanceId().equals(actualEvent.getInstanceId())
                && expectedEvent.getEventType().equals(actualEvent.getEventType())
                && Objects.equals(expectedEvent.getIsVirtual(), actualEvent.getIsVirtual())
                && Objects.equals(
                    expectedEvent.getIsUnmappedGuest(), actualEvent.getIsUnmappedGuest())
                && Objects.equals(expectedEvent.getIsHypervisor(), actualEvent.getIsHypervisor())
                && (!matchHypervisorUuid
                    || Objects.equals(
                        expectedEvent.getHypervisorUuid(), actualEvent.getHypervisorUuid())),
        Event.class);
  }

  private void assertMatches(Event expectedEvent, Event actualEvent) {
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedEvent, actualEvent),
        "SWatch event should match expected structure");
  }
}
