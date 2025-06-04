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
package com.redhat.swatch.hbi.events.services;

import static com.redhat.swatch.hbi.events.configuration.Channels.HBI_HOST_EVENTS_IN;
import static com.redhat.swatch.hbi.events.configuration.Channels.SWATCH_EVENTS_OUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostDeleteEvent;
import com.redhat.swatch.hbi.events.kafka.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.hbi.events.normalization.NormalizedEventType;
import com.redhat.swatch.hbi.events.repository.HbiHostRelationship;
import com.redhat.swatch.hbi.events.repository.HbiHostRelationshipRepository;
import com.redhat.swatch.hbi.events.test.helpers.HbiEventTestData;
import com.redhat.swatch.hbi.events.test.helpers.HbiEventTestHelper;
import com.redhat.swatch.hbi.events.test.helpers.SwatchEventTestHelper;
import com.redhat.swatch.hbi.events.test.resources.PostgresResource;
import io.getunleash.Unleash;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.HardwareType;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.candlepin.subscriptions.json.Measurement;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
class HbiEventConsumerTest {
  // NOTE:
  //  In order to mock the unleash service used in FeatureFlags
  //  we need to disable the unleash service in the configuration
  //  file.
  @InjectMock Unleash unleash;
  @Inject @Any InMemoryConnector connector;
  @Inject ApplicationClock clock;
  @Inject ObjectMapper objectMapper;
  @InjectSpy HbiHostRelationshipRepository repo;
  @Inject HbiEventTestHelper hbiEventTestHelper;
  @Inject SwatchEventTestHelper swatchEventTestHelper;
  private InMemorySource<HbiEvent> hbiEventsIn;
  private InMemorySink<Event> swatchEventsOut;

  @BeforeEach
  @Transactional
  void setup() {
    System.out.println("Setting up HbiEventConsumerTest");
    hbiEventsIn = connector.source(HBI_HOST_EVENTS_IN);
    swatchEventsOut = connector.sink(SWATCH_EVENTS_OUT);
    swatchEventsOut.clear();
    repo.deleteAll();
  }

  @Test
  void testSwatchEventSentWithOrgIdAsMessageKey() {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    var hbiEvent =
        hbiEventTestHelper.getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());
    hbiEventsIn.send(hbiEvent);

    Awaitility.await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              List<? extends Message<Event>> received = swatchEventsOut.received();
              assertEquals(1, received.size());
              Optional<OutgoingKafkaRecordMetadata> metadata =
                  received.get(0).getMetadata(OutgoingKafkaRecordMetadata.class);
              assertTrue(metadata.isPresent());
              assertEquals(hbiEvent.getHost().getOrgId(), metadata.get().getKey());
            });
  }

  @Test
  void testPhysicalRhelEvent() throws Exception {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);

    var hbiEvent =
        hbiEventTestHelper.getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());

    HbiHostRelationship expectedRelationship =
        hbiEventTestHelper.relationshipFromHbiEvent(hbiEvent);

    OffsetDateTime eventTimestamp = hbiEvent.getTimestamp().toOffsetDateTime();
    Event expected =
        swatchEventTestHelper.buildPhysicalRhelEvent(
            hbiEvent.getHost(),
            NormalizedEventType.INSTANCE_CREATED,
            eventTimestamp,
            false,
            buildMeasurements(2.0, 2.0));
    hbiEventsIn.send(hbiEvent);
    assertSwatchEventSent(expected);
    assertEquals(1, repo.count());
    assertRelationshipExists(expectedRelationship);
  }

  @Test
  void testValidSatelliteReportedVirtualUnmappedRhelHost() throws Exception {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);

    String hypervisorUuid = "bed420fa-59ef-44e5-af8a-62a24473a554";
    HbiHostCreateUpdateEvent hbiEvent =
        hbiEventTestHelper.getCreateUpdateEvent(
            HbiEventTestData.getSatelliteRhelHostCreatedEvent());

    HbiHostRelationship expectedRelationship =
        hbiEventTestHelper.virtualRelationshipFromHbiEvent(hbiEvent, hypervisorUuid, true);

    OffsetDateTime eventTimestamp = hbiEvent.getTimestamp().toOffsetDateTime();

    Event expected =
        swatchEventTestHelper.createExpectedEvent(
            hbiEvent.getHost(),
            NormalizedEventType.INSTANCE_CREATED,
            eventTimestamp,
            Sla.PREMIUM,
            Usage.PRODUCTION,
            HardwareType.VIRTUAL,
            true,
            true,
            false,
            hypervisorUuid,
            List.of("69", "408"),
            Set.of("RHEL for x86"),
            buildMeasurements(6.0, 1.0));

    hbiEventsIn.send(hbiEvent);

    assertSwatchEventSent(expected);
    assertEquals(1, repo.count());
    assertRelationshipExists(expectedRelationship);
  }

  @Test
  void testRhsmFactsAreNotConsideredWhenOutsideOfTheSyncThreshold() throws Exception {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    // The test event has a syncTimestamp outside the configured 'hostLastSyncThreshold'.
    var hbiEvent = getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());
    OffsetDateTime eventTimestamp = hbiEvent.getTimestamp().toOffsetDateTime();

    HbiHostRelationship expectedRelationship =
        hbiEventTestHelper.relationshipFromHbiEvent(hbiEvent);

    // No expected product tags/ids, sla, usage are null since 'rhsm' facts would be skipped because
    // the host will be considered unregistered due to lastCheckinDate.
    Event expected =
        swatchEventTestHelper
            .buildPhysicalRhelEvent(
                hbiEvent.getHost(),
                NormalizedEventType.INSTANCE_CREATED,
                eventTimestamp,
                false,
                buildMeasurements(2.0, 2.0))
            .withProductIds(List.of())
            .withProductTag(Set.of())
            .withSla(null)
            .withUsage(null);

    hbiEventsIn.send(hbiEvent);
    assertSwatchEventSent(expected);
    assertEquals(1, repo.count());
    assertRelationshipExists(expectedRelationship);
  }

  @Test
  void testQpcRhelHostCreatedEvent() throws Exception {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    var hbiEvent =
        hbiEventTestHelper.getCreateUpdateEvent(HbiEventTestData.getQpcRhelHostCreatedEvent());

    HbiHostRelationship expectedRelationship =
        hbiEventTestHelper.virtualRelationshipFromHbiEvent(hbiEvent, null, false);

    OffsetDateTime eventTimestamp = hbiEvent.getTimestamp().toOffsetDateTime();
    Event expected =
        swatchEventTestHelper.createExpectedEvent(
            hbiEvent.getHost(),
            NormalizedEventType.INSTANCE_CREATED,
            eventTimestamp,
            null,
            null,
            HardwareType.VIRTUAL,
            true,
            false,
            false,
            null,
            List.of(),
            Set.of("RHEL Ungrouped", "RHEL for x86", "RHEL"),
            buildMeasurements(4.0, 4.0));

    hbiEventsIn.send(hbiEvent);
    assertSwatchEventSent(expected);

    assertEquals(1, repo.count());
    assertRelationshipExists(expectedRelationship);
  }

  /**
   * Tests that when an incoming HBI hypervisor host is seen for the first time, a Swatch event is
   * emitted for any currently unmapped guest host that has already been seen by the service. It is
   * expected that any RHEL host's measurements will be updated accordingly.
   */
  @Test
  void testIncomingHypervisorReCalculatesForAllUnmappedGuests() throws Exception {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);

    var virtualHostHbiEvent =
        hbiEventTestHelper.getCreateUpdateEvent(HbiEventTestData.getVirtualRhelHostCreatedEvent());
    var hypervisorEvent =
        hbiEventTestHelper.getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());

    HbiHostRelationship expectedHypervisorRelationship =
        hbiEventTestHelper.relationshipFromHbiEvent(hypervisorEvent);

    HbiHostRelationship expectedGuestRelationship =
        hbiEventTestHelper.virtualRelationshipFromHbiEvent(
            virtualHostHbiEvent, hypervisorEvent.getHost().getSubscriptionManagerId(), false);

    HbiHost guestHost = virtualHostHbiEvent.getHost();
    OffsetDateTime guestEventTimestamp = virtualHostHbiEvent.getTimestamp().toOffsetDateTime();

    Event expectedUnmappedGuestEvent =
        swatchEventTestHelper.createExpectedEvent(
            guestHost,
            NormalizedEventType.INSTANCE_CREATED,
            guestEventTimestamp,
            Sla.SELF_SUPPORT,
            Usage.DEVELOPMENT_TEST,
            HardwareType.VIRTUAL,
            true,
            true,
            false,
            hypervisorEvent.getHost().getSubscriptionManagerId(),
            List.of("69"),
            Set.of("RHEL for x86"),
            buildMeasurements(1.0, 1.0));

    OffsetDateTime hypervisorEventTimestamp = hypervisorEvent.getTimestamp().toOffsetDateTime();
    var hypervisorHbiHost = hypervisorEvent.getHost();

    Event expectedHypervisorEvent =
        swatchEventTestHelper.buildPhysicalRhelEvent(
            hypervisorHbiHost,
            NormalizedEventType.INSTANCE_CREATED,
            hypervisorEventTimestamp,
            true,
            buildMeasurements(2.0, 2.0));

    Event expectedMappedGuestEvent =
        swatchEventTestHelper.createExpectedEvent(
            guestHost,
            NormalizedEventType.INSTANCE_UPDATED,
            hypervisorEventTimestamp,
            Sla.SELF_SUPPORT,
            Usage.DEVELOPMENT_TEST,
            HardwareType.VIRTUAL,
            true,
            false,
            false,
            hypervisorEvent.getHost().getSubscriptionManagerId(),
            List.of("69"),
            Set.of("RHEL for x86"),
            buildMeasurements(1.0, 2.0));

    // Send the guest event. Results in an unmapped guest swatch event.
    hbiEventsIn.send(virtualHostHbiEvent);

    // Send the hypervisor event. Results in a hypervisor swatch event, and an updated mapped
    // guest event.
    hbiEventsIn.send(hypervisorEvent);
    assertSwatchEventSent(
        expectedUnmappedGuestEvent, expectedHypervisorEvent, expectedMappedGuestEvent);

    assertEquals(2, repo.count());
    assertRelationshipExists(expectedHypervisorRelationship);
    assertRelationshipExists(expectedGuestRelationship);
  }

  /**
   * Tests that when a guest is updated, an event is also sent for the Hypervisor to ensure that the
   * isHypervisor fact is toggled. If the hypervisor is reported when there are no known guests, it
   * will be reported as isHypervisor false. This is because we currently have no way to identify
   * that it is indeed a hypervisor from HBI facts alone. The hypervisor's subscription_manager_id
   * must map to a guest's hypervisor UUID fact.
   */
  @Test
  void testIncomingRhelGuestWithKnownHypervisorProducesGuestHostCreatedAndHypervisorUpdatedEvents()
      throws Exception {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);

    var hypervisorHostHbiEvent =
        hbiEventTestHelper.getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());

    OffsetDateTime initialHypervisorEventTimestamp =
        hypervisorHostHbiEvent.getTimestamp().toOffsetDateTime();

    var hypervisorHbiHost = hypervisorHostHbiEvent.getHost();
    Event expectedInitialHypervisorSwatchEvent =
        swatchEventTestHelper.buildPhysicalRhelEvent(
            hypervisorHbiHost,
            NormalizedEventType.INSTANCE_CREATED,
            initialHypervisorEventTimestamp,
            false,
            buildMeasurements(2.0, 2.0));

    // Send the initial HBI hypervisor event.
    hbiEventsIn.send(hypervisorHostHbiEvent);

    var virtualHostHbiEvent =
        hbiEventTestHelper.getCreateUpdateEvent(HbiEventTestData.getVirtualRhelHostCreatedEvent());

    OffsetDateTime virtualGuestTimestamp = virtualHostHbiEvent.getTimestamp().toOffsetDateTime();

    HbiHost mappedGuest = virtualHostHbiEvent.getHost();
    Event expectedMappedGuestEvent =
        swatchEventTestHelper.createExpectedEvent(
            mappedGuest,
            NormalizedEventType.INSTANCE_CREATED,
            virtualGuestTimestamp,
            Sla.SELF_SUPPORT,
            Usage.DEVELOPMENT_TEST,
            HardwareType.VIRTUAL,
            true,
            false,
            false,
            hypervisorHostHbiEvent.getHost().getSubscriptionManagerId(),
            List.of("69"),
            Set.of("RHEL for x86"),
            buildMeasurements(1.0, 2.0));

    // The hypervisor update event will have the same timestamp as the incoming guest event
    // since this is time that we determined a change to the hypervisor host's state.
    Event expectedHypervisorUpdateEvent =
        swatchEventTestHelper.buildPhysicalRhelEvent(
            hypervisorHbiHost,
            NormalizedEventType.INSTANCE_UPDATED,
            virtualGuestTimestamp,
            true,
            buildMeasurements(2.0, 2.0));

    hbiEventsIn.send(virtualHostHbiEvent);

    HbiHostRelationship expectedGuestRelationship =
        hbiEventTestHelper.virtualRelationshipFromHbiEvent(
            virtualHostHbiEvent, hypervisorHbiHost.getSubscriptionManagerId(), false);
    HbiHostRelationship expectedHypervisorRelationship =
        hbiEventTestHelper.relationshipFromHbiEvent(hypervisorHostHbiEvent);

    assertSwatchEventSent(
        expectedInitialHypervisorSwatchEvent,
        expectedMappedGuestEvent,
        expectedHypervisorUpdateEvent);
    assertEquals(2, repo.count());
    assertRelationshipExists(expectedGuestRelationship);
    assertRelationshipExists(expectedHypervisorRelationship);
  }

  @Test
  void testDeleteEventWithoutRelationshipProducesMinimalSwatchEvent() {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    HbiHostDeleteEvent hbiEvent =
        HbiEventTestData.getEvent(
            objectMapper, HbiEventTestData.getHostDeletedEvent(), HbiHostDeleteEvent.class);
    hbiEventsIn.send(hbiEvent);

    Event expectedEvent = swatchEventTestHelper.createMinimalDeleteEvent(hbiEvent);

    assertSwatchEventSent(expectedEvent);
    assertEquals(0, repo.count());
  }

  @Test
  void testDeleteWithHypervisorGuestRelationships() throws Exception {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);

    var virtualHostHbiEvent =
        hbiEventTestHelper.getCreateUpdateEvent(HbiEventTestData.getVirtualRhelHostCreatedEvent());
    var hypervisorEvent =
        hbiEventTestHelper.getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());

    // Send the guest event. Results in an unmapped guest swatch event.
    hbiEventsIn.send(virtualHostHbiEvent);

    // Send the hypervisor event. Results in a hypervisor swatch event, and an updated mapped
    // guest event.
    hbiEventsIn.send(hypervisorEvent);

    // Flush the topic so we start fresh. We expect 3 swatch events due to sending
    // the HBI events above.
    flushSwatchEvents(3);

    // Send the delete event
    HbiHostDeleteEvent hostDeletedEvent =
        hbiEventTestHelper.createHostDeleteEvent(
            hypervisorEvent.getHost().getOrgId(), hypervisorEvent.getHost().getId(), clock.now());
    hbiEventsIn.send(hostDeletedEvent);

    // The resulting swatch delete event is expected to be populated with the
    // latest known host data from the relationship, however, the type,
    // timestamp, and expiry will have been updated.
    OffsetDateTime expectedDeleteEventTimestamp =
        hostDeletedEvent.getTimestamp().toOffsetDateTime();
    Event expectedHypervisorDeletedEvent =
        swatchEventTestHelper.buildPhysicalRhelEvent(
            hypervisorEvent.getHost(),
            NormalizedEventType.INSTANCE_DELETED,
            expectedDeleteEventTimestamp,
            true,
            buildMeasurements(2.0, 2.0));

    Event expectedMappedGuestUpdateEvent =
        swatchEventTestHelper.createExpectedEvent(
            virtualHostHbiEvent.getHost(),
            NormalizedEventType.INSTANCE_UPDATED,
            expectedDeleteEventTimestamp,
            Sla.SELF_SUPPORT,
            Usage.DEVELOPMENT_TEST,
            HardwareType.VIRTUAL,
            true,
            true,
            false,
            hypervisorEvent.getHost().getSubscriptionManagerId(),
            List.of("69"),
            Set.of("RHEL for x86"),
            buildMeasurements(1.0, 1.0));

    assertSwatchEventSent(expectedHypervisorDeletedEvent, expectedMappedGuestUpdateEvent);

    HbiHostRelationship guestRelationship =
        hbiEventTestHelper.virtualRelationshipFromHbiEvent(
            virtualHostHbiEvent, hypervisorEvent.getHost().getSubscriptionManagerId(), true);

    assertEquals(1, repo.count());
    verifyRelationshipDeleted(
        hypervisorEvent.getHost().getOrgId(), hypervisorEvent.getHost().getId());
    assertRelationshipExists(guestRelationship);
  }

  private HbiHostCreateUpdateEvent getCreateUpdateEvent(String messageJson) {
    HbiHostCreateUpdateEvent event =
        HbiEventTestData.getEvent(objectMapper, messageJson, HbiHostCreateUpdateEvent.class);

    // Ensure the event is not stale.
    event.getHost().setStaleTimestamp(clock.now().plusMonths(1).toString());
    return event;
  }

  private List<Measurement> buildMeasurements(double cores, double sockets) {
    return List.of(
        new Measurement().withMetricId("cores").withValue(cores),
        new Measurement().withMetricId("sockets").withValue(sockets));
  }

  private void assertSwatchEventSent(Event... expected) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              List<? extends Message<Event>> received = swatchEventsOut.received();
              assertEquals(expected.length, received.size());
              received.forEach(m -> System.out.println(m.getPayload()));
              MatcherAssert.assertThat(
                  received.stream().map(Message::getPayload).toList(),
                  Matchers.containsInAnyOrder(expected));
            });

    // Clear the events so that any additional calls to this method starts fresh.
    swatchEventsOut.clear();
  }

  private void flushSwatchEvents(int numToFlush) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              List<? extends Message<Event>> received = swatchEventsOut.received();
              assertEquals(numToFlush, received.size());
            });
    swatchEventsOut.clear();
  }

  @Transactional
  public void assertRelationshipExists(HbiHostRelationship expectedRelationship) throws Exception {
    Optional<HbiHostRelationship> found =
        repo.findByOrgIdAndInventoryId(
            expectedRelationship.getOrgId(), expectedRelationship.getInventoryId());
    assertTrue(found.isPresent());
    assertEquals(expectedRelationship.getOrgId(), found.get().getOrgId());
    assertEquals(expectedRelationship.getInventoryId(), found.get().getInventoryId());
    assertEquals(
        expectedRelationship.getSubscriptionManagerId(), found.get().getSubscriptionManagerId());
    assertEquals(expectedRelationship.getHypervisorUuid(), found.get().getHypervisorUuid());
    assertEquals(expectedRelationship.isUnmappedGuest(), found.get().isUnmappedGuest());
    // skipping created/updated checks as they are runtime dates and are hard to pinpoint.

    assertEquals(
        objectMapper.readValue(expectedRelationship.getLatestHbiEventData(), HbiHost.class),
        objectMapper.readValue(found.get().getLatestHbiEventData(), HbiHost.class));
  }

  @Transactional
  public void verifyRelationshipDeleted(String orgId, UUID inventoryId) {
    assertTrue(repo.findByOrgIdAndInventoryId(orgId, inventoryId).isEmpty());
  }
}
