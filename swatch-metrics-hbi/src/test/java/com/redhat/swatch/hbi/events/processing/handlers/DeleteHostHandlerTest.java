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
package com.redhat.swatch.hbi.events.processing.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostDeleteEvent;
import com.redhat.swatch.hbi.events.normalization.NormalizedEventType;
import com.redhat.swatch.hbi.events.repository.HbiHostRelationship;
import com.redhat.swatch.hbi.events.repository.HbiHostRelationshipRepository;
import com.redhat.swatch.hbi.events.test.helpers.HbiEventTestData;
import com.redhat.swatch.hbi.events.test.helpers.HbiEventTestHelper;
import com.redhat.swatch.hbi.events.test.helpers.SwatchEventTestHelper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.HardwareType;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DeleteHostHandlerTest {

  @InjectMock HbiHostRelationshipRepository relationshipRepo;
  @Inject DeleteHostHandler handler;

  @Inject HbiEventTestHelper hbiEventTestHelper;
  @Inject SwatchEventTestHelper swatchEventHelper;

  /**
   * Test that when a DeleteHostEvent is processed, that the associated relationship is deleted from
   * the database and that a single swatch delete event is created.
   */
  @Test
  void testNonHypervisorHostDeletedEventWhenRelationshipExists() {
    HbiHostCreateUpdateEvent initialHostCreateEvent =
        hbiEventTestHelper.getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());
    HbiHostRelationship hostRelationship =
        hbiEventTestHelper.relationshipFromHbiEvent(initialHostCreateEvent);

    HbiHostDeleteEvent physicalHostDeleteEvent =
        hbiEventTestHelper.createHostDeleteEvent(
            initialHostCreateEvent.getHost().getOrgId(),
            initialHostCreateEvent.getHost().getId(),
            hbiEventTestHelper.getClock().now());

    when(relationshipRepo.findByOrgIdAndInventoryId(
            physicalHostDeleteEvent.getOrgId(), physicalHostDeleteEvent.getId()))
        .thenReturn(Optional.of(hostRelationship));

    Event expected =
        swatchEventHelper.buildPhysicalRhelEvent(
            initialHostCreateEvent.getHost(),
            NormalizedEventType.INSTANCE_DELETED,
            physicalHostDeleteEvent.getTimestamp().toOffsetDateTime(),
            false,
            swatchEventHelper.buildMeasurements(2.0, 2.0));

    List<Event> events = handler.handleEvent(physicalHostDeleteEvent);
    // Only expecting a swatch event for the deleted instance.
    assertEquals(1, events.size());
    assertEquals(expected, events.get(0));

    // Verify the attempt to delete the relationship.
    verify(relationshipRepo, times(1)).delete(any());
    verify(relationshipRepo).delete(eq(hostRelationship));
  }

  /**
   * Test that when a DeleteHostEvent is processed for a hypervisor, all associated guest
   * relationships are updated and an event is sent for all guests indicating that its hypervisor
   * was deleted and all guests are updated to 'unmapped' from 'mapped'.
   */
  @Test
  void testPhysicalHypervisorHostDeletedEventWithNoMappedGuests() {
    HbiHostCreateUpdateEvent initialHypervisorCreateEvent =
        hbiEventTestHelper.getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());

    HbiHostRelationship hypervisorRelationship =
        hbiEventTestHelper.relationshipFromHbiEvent(initialHypervisorCreateEvent);
    HbiHostDeleteEvent hypervisorHostDeleteEvent =
        hbiEventTestHelper.createHostDeleteEvent(
            initialHypervisorCreateEvent.getHost().getOrgId(),
            initialHypervisorCreateEvent.getHost().getId(),
            hbiEventTestHelper.getClock().now());

    // Guest relationships
    HbiHostCreateUpdateEvent guest1CreatedEvent =
        createGuestCreatedEvent(initialHypervisorCreateEvent);
    HbiHostRelationship guest1Relationship =
        hbiEventTestHelper.virtualRelationshipFromHbiEvent(
            guest1CreatedEvent, hypervisorRelationship.getSubscriptionManagerId(), false);

    HbiHostCreateUpdateEvent guest2CreatedEvent =
        createGuestCreatedEvent(initialHypervisorCreateEvent);
    HbiHostRelationship guest2Relationship =
        hbiEventTestHelper.virtualRelationshipFromHbiEvent(
            guest2CreatedEvent, hypervisorRelationship.getSubscriptionManagerId(), false);

    // Ensure the hypervisor facts reflect that this is a hypervisor.
    when(relationshipRepo.guestCount(
            hypervisorRelationship.getOrgId(), hypervisorRelationship.getSubscriptionManagerId()))
        .thenReturn(2L);

    // Mock the hypervisor relationship lookup.
    when(relationshipRepo.findByOrgIdAndInventoryId(
            hypervisorHostDeleteEvent.getOrgId(), hypervisorHostDeleteEvent.getId()))
        .thenReturn(Optional.of(hypervisorRelationship));

    // The hypervisor has 2 matching guest relationships.
    when(relationshipRepo.findMappedGuests(
            hypervisorRelationship.getOrgId(), hypervisorRelationship.getSubscriptionManagerId()))
        .thenReturn(List.of(guest1Relationship, guest2Relationship));

    // Expected Swatch events.
    Event expectedHypervisorEvent =
        swatchEventHelper.buildPhysicalRhelEvent(
            initialHypervisorCreateEvent.getHost(),
            NormalizedEventType.INSTANCE_DELETED,
            hypervisorHostDeleteEvent.getTimestamp().toOffsetDateTime(),
            true,
            swatchEventHelper.buildMeasurements(2.0, 2.0));

    Event expectedGuest1Event =
        swatchEventHelper.createExpectedEvent(
            guest1CreatedEvent.getHost(),
            NormalizedEventType.INSTANCE_UPDATED,
            hypervisorHostDeleteEvent.getTimestamp().toOffsetDateTime(),
            Sla.SELF_SUPPORT,
            Usage.DEVELOPMENT_TEST,
            HardwareType.VIRTUAL,
            true,
            // Expected that the guest is now unmapped
            true,
            false,
            hypervisorRelationship.getSubscriptionManagerId(),
            List.of("69"),
            Set.of("RHEL for x86"),
            swatchEventHelper.buildMeasurements(1.0, 1.0));

    Event expectedGuest2Event =
        swatchEventHelper.createExpectedEvent(
            guest2CreatedEvent.getHost(),
            NormalizedEventType.INSTANCE_UPDATED,
            hypervisorHostDeleteEvent.getTimestamp().toOffsetDateTime(),
            Sla.SELF_SUPPORT,
            Usage.DEVELOPMENT_TEST,
            HardwareType.VIRTUAL,
            true,
            // Expected that the guest is now unmapped
            true,
            false,
            hypervisorRelationship.getSubscriptionManagerId(),
            List.of("69"),
            Set.of("RHEL for x86"),
            swatchEventHelper.buildMeasurements(1.0, 1.0));

    List<Event> events = handler.handleEvent(hypervisorHostDeleteEvent);
    assertEquals(3, events.size());
    assertEquals(expectedHypervisorEvent, events.get(0));
    assertEquals(expectedGuest1Event, events.get(1));
    assertEquals(expectedGuest2Event, events.get(2));

    // Verify the attempt to delete the relationship.
    verify(relationshipRepo).delete(eq(hypervisorRelationship));
    verify(relationshipRepo, never()).delete(eq(guest1Relationship));
    verify(relationshipRepo, never()).delete(eq(guest2Relationship));
  }

  /**
   * Test that when a DeleteHostEvent is processed for a mapped guest, a swatch event is sent for
   * the associated hypervisor.
   */
  @Test
  void testMappedGuestDeleteEventUpdatesHypervisor() {
    HbiHostCreateUpdateEvent initialHypervisorCreateEvent =
        hbiEventTestHelper.getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());

    HbiHostRelationship hypervisorRelationship =
        hbiEventTestHelper.relationshipFromHbiEvent(initialHypervisorCreateEvent);

    HbiHostCreateUpdateEvent guestCreatedEvent =
        createGuestCreatedEvent(initialHypervisorCreateEvent);

    HbiHostRelationship guestRelationship =
        hbiEventTestHelper.virtualRelationshipFromHbiEvent(
            guestCreatedEvent, hypervisorRelationship.getSubscriptionManagerId(), false);

    HbiHostDeleteEvent guestHostDeleteEvent =
        hbiEventTestHelper.createHostDeleteEvent(
            guestCreatedEvent.getHost().getOrgId(),
            guestCreatedEvent.getHost().getId(),
            hbiEventTestHelper.getClock().now());

    // Ensure the host facts reflect the correct isHypervisor state.
    when(relationshipRepo.guestCount(
            hypervisorRelationship.getOrgId(), hypervisorRelationship.getSubscriptionManagerId()))
        .thenReturn(1L);
    when(relationshipRepo.guestCount(
            guestRelationship.getOrgId(), guestRelationship.getSubscriptionManagerId()))
        .thenReturn(0L);

    // Ensure the guest relationship is found
    when(relationshipRepo.findByOrgIdAndInventoryId(
            guestHostDeleteEvent.getOrgId(), guestHostDeleteEvent.getId()))
        .thenReturn(Optional.of(guestRelationship));

    // Ensure the hypervisor is returned when the guest looks it up by its subman ID.
    when(relationshipRepo.findByOrgIdAndSubscriptionManagerId(
            hypervisorRelationship.getOrgId(), hypervisorRelationship.getSubscriptionManagerId()))
        .thenReturn(Optional.of(hypervisorRelationship));

    Event expectedGuestDeletedEvent =
        swatchEventHelper.createExpectedEvent(
            guestCreatedEvent.getHost(),
            NormalizedEventType.INSTANCE_DELETED,
            guestHostDeleteEvent.getTimestamp().toOffsetDateTime(),
            Sla.SELF_SUPPORT,
            Usage.DEVELOPMENT_TEST,
            HardwareType.VIRTUAL,
            true,
            false,
            false,
            hypervisorRelationship.getSubscriptionManagerId(),
            List.of("69"),
            Set.of("RHEL for x86"),
            swatchEventHelper.buildMeasurements(1.0, 2.0));

    Event expectedHypervisorEvent =
        swatchEventHelper.buildPhysicalRhelEvent(
            initialHypervisorCreateEvent.getHost(),
            NormalizedEventType.INSTANCE_UPDATED,
            guestHostDeleteEvent.getTimestamp().toOffsetDateTime(),
            true,
            swatchEventHelper.buildMeasurements(2.0, 2.0));

    List<Event> events = handler.handleEvent(guestHostDeleteEvent);
    assertEquals(2, events.size());
    assertEquals(expectedGuestDeletedEvent, events.get(0));
    assertEquals(expectedHypervisorEvent, events.get(1));

    verify(relationshipRepo).delete(eq(guestRelationship));
    verify(relationshipRepo, never()).delete(eq(hypervisorRelationship));
  }

  /**
   * Tests that when processing a HbiHostDeleteEvent that does not have a known relationship, a
   * minimal swatch event is sent that provides enough information to act on this event when it is
   * being ingested.
   */
  @Test
  void testDeleteHostEventResultsInMinimalSwatchDeleteEventWhenRelationshipDoesNotExist() {
    HbiHostDeleteEvent physicalHostDeleteEvent =
        hbiEventTestHelper.createHostDeleteEvent(
            "orgId", UUID.randomUUID(), hbiEventTestHelper.getClock().now());

    when(relationshipRepo.findByOrgIdAndInventoryId(
            physicalHostDeleteEvent.getOrgId(), physicalHostDeleteEvent.getId()))
        .thenReturn(Optional.empty());

    Event expectedDeleteEvent = swatchEventHelper.createMinimalDeleteEvent(physicalHostDeleteEvent);

    List<Event> events = handler.handleEvent(physicalHostDeleteEvent);
    assertEquals(1, events.size());
    assertEquals(expectedDeleteEvent, events.get(0));

    verify(relationshipRepo, atMostOnce())
        .findByOrgIdAndInventoryId(
            eq(physicalHostDeleteEvent.getOrgId()), eq(physicalHostDeleteEvent.getId()));
    verify(relationshipRepo, never()).delete(any());
  }

  private HbiHostCreateUpdateEvent createGuestCreatedEvent(
      HbiHostCreateUpdateEvent initialHypervisorCreateEvent) {
    return hbiEventTestHelper.createTemplatedGuestCreatedEvent(
        initialHypervisorCreateEvent.getHost().getOrgId(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        initialHypervisorCreateEvent.getHost().getSubscriptionManagerId());
  }
}
