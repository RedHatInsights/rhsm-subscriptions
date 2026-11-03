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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.DefaultMessageValidator;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.HostTemplates;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.Topics;
import com.redhat.swatch.hbi.events.ct.api.MessageValidators;
import com.redhat.swatch.hbi.events.ct.utils.HbiEventHelper;
import com.redhat.swatch.hbi.events.ct.utils.SwatchEventHelper;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostDeleteEvent;
import com.redhat.swatch.hbi.events.normalization.NormalizedEventType;
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
  void shouldProduceDeleteEventForPhysicalRhsmHost() {
    // Given: An existing physical RHEL host
    String insightsId = UUID.randomUUID().toString();
    HbiHostCreateUpdateEvent createEvent = givenExistingPhysicalHost(insightsId);
    String instanceId = createEvent.getHost().getId().toString();

    // Given: Delete event for the physical host
    HbiHostDeleteEvent deleteEvent =
        HbiEventHelper.getDeletedHostEvent(
            createEvent.getHost().getId().toString(),
            orgId,
            insightsId,
            OffsetDateTime.now(ZoneOffset.UTC));

    // When: Delete event is published
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, deleteEvent);

    // Then: Wait for and capture the delete SWatch event from Kafka
    List<Event> deleteEvents =
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(
                orgId, instanceId, NormalizedEventType.INSTANCE_DELETED.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));
    assertEquals(1, deleteEvents.size(), "Expected exactly 1 event");
    Event actualDeleteEvent = deleteEvents.getFirst();

    // Then: Assert specific fields on delete event
    assertEquals(
        NormalizedEventType.INSTANCE_DELETED.toString(),
        actualDeleteEvent.getEventType(),
        "Event type should be INSTANCE_DELETED");
    assertEquals(orgId, actualDeleteEvent.getOrgId(), "orgId should match");
    assertEquals(instanceId, actualDeleteEvent.getInstanceId(), "instanceId should match");
    assertEquals(4.0, getMeasurementValue(actualDeleteEvent, "cores"), "cores should be 4.0");
    assertEquals(2.0, getMeasurementValue(actualDeleteEvent, "sockets"), "sockets should be 2.0");
    assertFalse(actualDeleteEvent.getIsVirtual(), "Should not be virtual");
    assertTrue(actualDeleteEvent.getProductTag().contains("RHEL for x86"), "Should have x86 tag");

    // Then: Verify full delete event matches expected structure
    Event expectedDeleteEvent =
        SwatchEventHelper.createExpectedDeletedEvent(
            createEvent, deleteEvent, List.of("69"), Set.of("RHEL for x86"), false, false);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedDeleteEvent, actualDeleteEvent),
        "Delete event should match expected structure");
  }

  @TestPlanName("metrics-hbi-delete-TC002")
  @Test
  void shouldProduceDeleteEventForHypervisorAndUpdateGuest() {
    // Given: An existing hypervisor with a mapped guest
    String hypervisorSubManId = UUID.randomUUID().toString();
    String hypervisorInsightsId = UUID.randomUUID().toString();

    HbiHostCreateUpdateEvent[] events =
        givenExistingHypervisorAndGuest(hypervisorSubManId, hypervisorInsightsId, null);
    HbiHostCreateUpdateEvent hypervisorEvent = events[0];
    HbiHostCreateUpdateEvent guestEvent = events[1];

    String hypervisorInstanceId = hypervisorEvent.getHost().getId().toString();
    String guestInstanceId = guestEvent.getHost().getId().toString();

    // Given: Delete event for the hypervisor
    HbiHostDeleteEvent deleteEvent =
        HbiEventHelper.getDeletedHostEvent(
            hypervisorEvent.getHost().getId().toString(),
            orgId,
            hypervisorInsightsId,
            OffsetDateTime.now(ZoneOffset.UTC));

    // When: Hypervisor delete event is published
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, deleteEvent);

    // Then: Wait for and capture the hypervisor delete event
    List<Event> hypervisorDeleteEvents =
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(
                orgId, hypervisorInstanceId, NormalizedEventType.INSTANCE_DELETED.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));
    assertEquals(1, hypervisorDeleteEvents.size(), "Expected exactly 1 event");
    Event actualHypervisorDeleteEvent = hypervisorDeleteEvents.getFirst();

    // Then: Assert specific fields on hypervisor delete event
    assertEquals(
        NormalizedEventType.INSTANCE_DELETED.toString(),
        actualHypervisorDeleteEvent.getEventType());
    assertTrue(actualHypervisorDeleteEvent.getIsHypervisor(), "Should still show as hypervisor");
    assertEquals(2.0, getMeasurementValue(actualHypervisorDeleteEvent, "cores"));
    assertEquals(2.0, getMeasurementValue(actualHypervisorDeleteEvent, "sockets"));

    // Then: Wait for and capture the guest update event (becomes unmapped)
    List<Event> guestUpdateEvents =
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(
                orgId, guestInstanceId, NormalizedEventType.INSTANCE_UPDATED.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));
    assertEquals(1, guestUpdateEvents.size(), "Expected exactly 1 event");
    Event actualGuestUpdateEvent = guestUpdateEvents.getFirst();

    // Then: Assert specific fields on guest update event
    assertTrue(actualGuestUpdateEvent.getIsVirtual(), "Should be virtual");
    assertTrue(actualGuestUpdateEvent.getIsUnmappedGuest(), "Should now be unmapped");
    assertEquals(
        1.0, getMeasurementValue(actualGuestUpdateEvent, "sockets"), "Unmapped guest sockets=1");

    // Then: Verify full events match expected structure
    Event expectedHypervisorDeleteEvent =
        SwatchEventHelper.createExpectedDeletedEvent(
            hypervisorEvent, deleteEvent, List.of("69"), Set.of("RHEL for x86"), false, true);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedHypervisorDeleteEvent, actualHypervisorDeleteEvent),
        "Hypervisor delete event should match");

    Event expectedGuestUpdateEvent =
        SwatchEventHelper.createExpectedEvent(
            guestEvent,
            NormalizedEventType.INSTANCE_UPDATED,
            List.of("69"),
            Set.of("RHEL for x86"),
            true,
            false);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedGuestUpdateEvent, actualGuestUpdateEvent),
        "Guest update event should match");
  }

  @TestPlanName("metrics-hbi-delete-TC003")
  @Test
  void shouldProduceDeleteEventForMappedGuestAndUpdateHypervisor() {
    // Given: An existing hypervisor with a mapped guest
    String hypervisorSubManId = UUID.randomUUID().toString();
    String guestInsightsId = UUID.randomUUID().toString();

    HbiHostCreateUpdateEvent[] events =
        givenExistingHypervisorAndGuest(hypervisorSubManId, null, guestInsightsId);
    HbiHostCreateUpdateEvent hypervisorEvent = events[0];
    HbiHostCreateUpdateEvent guestEvent = events[1];

    String hypervisorInstanceId = hypervisorEvent.getHost().getId().toString();
    String guestInstanceId = guestEvent.getHost().getId().toString();

    // Given: Delete event for the guest
    HbiHostDeleteEvent deleteEvent =
        HbiEventHelper.getDeletedHostEvent(
            guestEvent.getHost().getId().toString(),
            orgId,
            guestInsightsId,
            OffsetDateTime.now(ZoneOffset.UTC));

    // When: Guest delete event is published
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, deleteEvent);

    // Then: Wait for and capture the guest delete event
    List<Event> guestDeleteEvents =
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(
                orgId, guestInstanceId, NormalizedEventType.INSTANCE_DELETED.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));
    assertEquals(1, guestDeleteEvents.size(), "Expected exactly 1 event");
    Event actualGuestDeleteEvent = guestDeleteEvents.getFirst();

    // Then: Assert specific fields on guest delete event
    assertEquals(
        NormalizedEventType.INSTANCE_DELETED.toString(), actualGuestDeleteEvent.getEventType());
    assertTrue(actualGuestDeleteEvent.getIsVirtual(), "Should be virtual");
    assertEquals(2.0, getMeasurementValue(actualGuestDeleteEvent, "cores"));
    assertEquals(3.0, getMeasurementValue(actualGuestDeleteEvent, "sockets"));

    // Then: Wait for and capture the hypervisor update event (becomes non-hypervisor)
    // Note: There's already an INSTANCE_UPDATED event from setup (isHypervisor=true)
    // We need to filter for the one with isHypervisor=false
    List<Event> hypervisorUpdateEvents =
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            new DefaultMessageValidator<>(
                event ->
                    orgId.equals(event.getOrgId())
                        && hypervisorInstanceId.equals(event.getInstanceId())
                        && NormalizedEventType.INSTANCE_UPDATED
                            .toString()
                            .equals(event.getEventType())
                        && Boolean.FALSE.equals(event.getIsHypervisor()), // Must NOT be hypervisor
                Event.class),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));
    assertEquals(1, hypervisorUpdateEvents.size(), "Expected exactly 1 event");
    Event actualHypervisorUpdateEvent = hypervisorUpdateEvents.getFirst();

    // Then: Assert specific fields on hypervisor update event
    assertFalse(actualHypervisorUpdateEvent.getIsVirtual(), "Should not be virtual");
    assertFalse(
        actualHypervisorUpdateEvent.getIsHypervisor(), "Should no longer be marked as hypervisor");

    // Then: Verify full events match expected structure
    Event expectedGuestDeleteEvent =
        SwatchEventHelper.createExpectedDeletedEvent(
            guestEvent, deleteEvent, List.of("69"), Set.of("RHEL for x86"), false, false);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedGuestDeleteEvent, actualGuestDeleteEvent),
        "Guest delete event should match");

    Event expectedHypervisorUpdateEvent =
        SwatchEventHelper.createExpectedEvent(
            hypervisorEvent,
            NormalizedEventType.INSTANCE_UPDATED,
            List.of("69"),
            Set.of("RHEL for x86"),
            false,
            false);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedHypervisorUpdateEvent, actualHypervisorUpdateEvent),
        "Hypervisor update event should match");
  }

  @TestPlanName("metrics-hbi-delete-TC004")
  @Test
  void shouldProduceDeleteEventForUnseenHost() {
    // Given: A delete event for an unseen host (host was never created/updated before)
    String unseenHostId = UUID.randomUUID().toString();
    String unseenInsightsId = UUID.randomUUID().toString();

    HbiHostDeleteEvent deleteEvent =
        HbiEventHelper.getDeletedHostEvent(
            unseenHostId, orgId, unseenInsightsId, OffsetDateTime.now(ZoneOffset.UTC));

    // When: Delete event is published for unseen host
    kafkaBridge.produceKafkaMessage(Topics.HBI_EVENT_IN, deleteEvent);

    // Then: Wait for and capture the delete SWatch event from Kafka
    // Note: For unseen hosts, instanceId equals inventoryId since there's no prior context
    List<Event> deleteEvents =
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(
                orgId, unseenHostId, NormalizedEventType.INSTANCE_DELETED.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));
    assertEquals(1, deleteEvents.size(), "Expected exactly 1 event");
    Event actualDeleteEvent = deleteEvents.getFirst();

    // Then: Assert specific fields on unseen host delete event
    // Unseen hosts have minimal metadata: only orgId, instanceId, inventoryId, insightsId
    assertEquals(
        NormalizedEventType.INSTANCE_DELETED.toString(),
        actualDeleteEvent.getEventType(),
        "Event type should be INSTANCE_DELETED");
    assertEquals(orgId, actualDeleteEvent.getOrgId(), "orgId should match");
    assertEquals(unseenHostId, actualDeleteEvent.getInstanceId(), "instanceId should match");
    assertEquals(
        unseenHostId, actualDeleteEvent.getInventoryId().orElse(null), "inventoryId should match");
    assertEquals(
        unseenInsightsId,
        actualDeleteEvent.getInsightsId().orElse(null),
        "insightsId should match");

    // Unseen hosts have no measurements, products, or tags
    assertNull(actualDeleteEvent.getMeasurements(), "Unseen host should have no measurements");

    // Then: Verify full delete event matches expected structure
    Event expectedDeleteEvent =
        SwatchEventHelper.createExpectedDeletedEvent(null, deleteEvent, null, null, false, false);
    assertTrue(
        SwatchEventHelper.eventsMatch(expectedDeleteEvent, actualDeleteEvent),
        "Unseen host delete event should match expected minimal structure");
  }

  // ===== Helper Methods =====

  /**
   * Create and publish a physical RHEL host, then wait for the create event to confirm it exists.
   *
   * @param insightsId the insightsId to use for the host
   * @return the HBI create event that was published
   */
  private HbiHostCreateUpdateEvent givenExistingPhysicalHost(String insightsId) {
    HbiHostCreateUpdateEvent createEvent =
        hostEvents
            .createHost(orgId)
            .apply(HostTemplates.conduitPhysicalRhelSelfSupportDevTest(2, 4))
            .insightsId(insightsId)
            .toEvent()
            .type("created")
            .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
            .buildCreateUpdate();

    hostEvents.publish(createEvent);

    // Wait for create event to confirm host exists
    String instanceId = createEvent.getHost().getId().toString();
    thenSwatchEventsAppear(
        MessageValidators.swatchEventMatches(
            orgId, instanceId, NormalizedEventType.INSTANCE_CREATED.toString()));

    return createEvent;
  }

  /**
   * Create and publish a hypervisor and mapped guest, then wait for all 3 create/update events
   * (hypervisor create, guest create, hypervisor transition to isHypervisor=true).
   *
   * @param hypervisorSubManId the subscription manager ID for the hypervisor
   * @param hypervisorInsightsId optional insightsId for the hypervisor (can be null)
   * @param guestInsightsId optional insightsId for the guest (can be null)
   * @return array with [0]=hypervisor event, [1]=guest event
   */
  private HbiHostCreateUpdateEvent[] givenExistingHypervisorAndGuest(
      String hypervisorSubManId, String hypervisorInsightsId, String guestInsightsId) {

    HbiHostCreateUpdateEvent hypervisorEvent =
        hostEvents
            .createHost(orgId)
            .apply(HostTemplates.conduitPhysicalRhelSelfSupportDevTest(2, 2))
            .subscriptionManagerId(hypervisorSubManId)
            .insightsId(hypervisorInsightsId)
            .toEvent()
            .type("created")
            .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
            .buildCreateUpdate();

    hostEvents.publish(hypervisorEvent);
    String hypervisorInstanceId = hypervisorEvent.getHost().getId().toString();

    thenSwatchEventsAppear(
        MessageValidators.swatchEventMatches(
            orgId, hypervisorInstanceId, NormalizedEventType.INSTANCE_CREATED.toString()));

    HbiHostCreateUpdateEvent guestEvent =
        hostEvents
            .createHost(orgId)
            .apply(
                HostTemplates.conduitVirtualRhelGuestSelfSupportDevTest(1, 3, hypervisorSubManId))
            .insightsId(guestInsightsId)
            .toEvent()
            .type("created")
            .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
            .buildCreateUpdate();

    hostEvents.publish(guestEvent);

    // Wait for the create/update events (guest, hypervisor transition)
    String guestInstanceId = guestEvent.getHost().getId().toString();

    thenSwatchEventsAppear(
        MessageValidators.swatchEventMatches(
            orgId, guestInstanceId, NormalizedEventType.INSTANCE_CREATED.toString()),
        MessageValidators.swatchEventMatches(
            orgId, hypervisorInstanceId, NormalizedEventType.INSTANCE_UPDATED.toString()));

    return new HbiHostCreateUpdateEvent[] {hypervisorEvent, guestEvent};
  }
}
