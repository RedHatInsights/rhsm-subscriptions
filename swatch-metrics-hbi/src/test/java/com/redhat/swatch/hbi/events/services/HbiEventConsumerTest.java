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
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.hbi.events.HbiEventTestData;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostDeleteEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostFacts;
import com.redhat.swatch.hbi.events.kafka.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SystemProfileFacts;
import com.redhat.swatch.hbi.events.repository.HbiHostRelationshipRepository;
import com.redhat.swatch.hbi.events.test.resources.PostgresResource;
import io.getunleash.Unleash;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
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
import java.util.stream.Stream;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
  private InMemorySource<HbiEvent> hbiEventsIn;
  private InMemorySink<Event> swatchEventsOut;

  @BeforeEach
  @Transactional
  void setup() {
    hbiEventsIn = connector.source(HBI_HOST_EVENTS_IN);
    swatchEventsOut = connector.sink(SWATCH_EVENTS_OUT);
    swatchEventsOut.clear();
    repo.deleteAll();
  }

  @Test
  void testPhysicalRhelEvent() {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    var hbiEvent = getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());
    // Override the syncTimestamp fact so that it aligns with the current time
    // and is within the configured 'hostLastSyncThreshold'.
    setRhsmSyncTimestamp(hbiEvent, clock.now().minusHours(5));

    OffsetDateTime eventTimestamp = hbiEvent.getTimestamp().toOffsetDateTime();
    Event expected =
        buildPhysicalRhelEvent(
            hbiEvent.getHost(),
            "HBI_HOST_CREATED",
            eventTimestamp,
            false,
            buildMeasurements(2.0, 2.0));
    hbiEventsIn.send(hbiEvent);
    assertSwatchEventSent(expected);
  }

  @Test
  void testValidSatelliteReportedVirtualUnmappedRhelHost() {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    HbiHostCreateUpdateEvent hbiEvent =
        getCreateUpdateEvent(HbiEventTestData.getSatelliteRhelHostCreatedEvent());

    OffsetDateTime eventTimestamp = hbiEvent.getTimestamp().toOffsetDateTime();

    Event expected =
        createExpectedEvent(
            hbiEvent.getHost(),
            "HBI_HOST_CREATED",
            eventTimestamp,
            Sla.PREMIUM,
            Usage.PRODUCTION,
            HardwareType.VIRTUAL,
            true,
            true,
            false,
            "bed420fa-59ef-44e5-af8a-62a24473a554",
            List.of("69", "408"),
            Set.of("RHEL for x86"),
            buildMeasurements(6.0, 1.0));
    hbiEventsIn.send(hbiEvent);
    assertSwatchEventSent(expected);
  }

  @Test
  void testRhsmFactsAreNotConsideredWhenOutsideOfTheSyncThreshold() {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    // The test event has a syncTimestamp outside the configured 'hostLastSyncThreshold'.
    var hbiEvent = getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());
    OffsetDateTime eventTimestamp = hbiEvent.getTimestamp().toOffsetDateTime();

    // No expected product tags/ids, sla, usage are null since 'rhsm' facts would be skipped because
    // the host will be considered unregistered due to lastCheckinDate.
    Event expected =
        buildPhysicalRhelEvent(
                hbiEvent.getHost(),
                "HBI_HOST_CREATED",
                eventTimestamp,
                false,
                buildMeasurements(2.0, 2.0))
            .withProductIds(List.of())
            .withProductTag(Set.of())
            .withSla(null)
            .withUsage(null);

    hbiEventsIn.send(hbiEvent);
    assertSwatchEventSent(expected);
  }

  @Test
  void testQpcRhelHostCreatedEvent() {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    var hbiEvent = getCreateUpdateEvent(HbiEventTestData.getQpcRhelHostCreatedEvent());
    OffsetDateTime eventTimestamp = hbiEvent.getTimestamp().toOffsetDateTime();

    Event expected =
        createExpectedEvent(
            hbiEvent.getHost(),
            "HBI_HOST_CREATED",
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
  }

  @Test
  void testDeletedEventIsSkipped() {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    HbiHostDeleteEvent hbiEvent =
        HbiEventTestData.getEvent(
            objectMapper, HbiEventTestData.getHostDeletedEvent(), HbiHostDeleteEvent.class);
    hbiEventsIn.send(hbiEvent);

    Awaitility.await()
        .atMost(Duration.ofSeconds(1))
        .catchUncaughtExceptions()
        .untilAsserted(
            () -> {
              List<? extends Message<Event>> received = swatchEventsOut.received();
              assertEquals(0, received.size());
            });
  }

  static Stream<Arguments> filterTestArgs() {
    ApplicationClock clock = new ApplicationClock();
    OffsetDateTime notStale = clock.now().plusMonths(1);
    OffsetDateTime stale = clock.now().minusMonths(2);
    return Stream.of(
        // staleTimestamp, billingModel, hostType, shouldBeFiltered
        Arguments.of(notStale, "marketplace", "good_host_type", true),
        // Valid events should not be filtered.
        Arguments.of(notStale, "annual", "good_host_type", false),
        // Stale hosts should be filtered.
        Arguments.of(stale, "annual", "good_host_type", true),
        // Hosts with type 'edge' should be filtered.
        Arguments.of(notStale, "annual", "edge", true),
        // Nulls are considered valid when filtering.
        Arguments.of(notStale, null, null, false),
        // Empty strings are considered valid when filtering.
        Arguments.of(notStale, "", "", false));
  }

  @ParameterizedTest
  @MethodSource("filterTestArgs")
  void testIncomingEventFilter(
      OffsetDateTime staleTimestamp,
      String billingModel,
      String hostType,
      boolean shouldBeFiltered) {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    var hbiEvent = getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());
    hbiEvent.getHost().setStaleTimestamp(staleTimestamp.toString());
    setBillingModel(hbiEvent, billingModel);
    setHostType(hbiEvent, hostType);

    int expectedEventCount = shouldBeFiltered ? 0 : 1;

    hbiEventsIn.send(hbiEvent);
    Awaitility.await()
        .atMost(Duration.ofSeconds(1))
        .catchUncaughtExceptions()
        .untilAsserted(
            () -> {
              List<? extends Message<Event>> received = swatchEventsOut.received();
              assertEquals(expectedEventCount, received.size());
            });
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
        getCreateUpdateEvent(HbiEventTestData.getVirtualRhelHostCreatedEvent());
    var hypervisorEvent = getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());

    // Override the syncTimestamp fact so that it aligns with the current time
    // and is within the configured 'hostLastSyncThreshold'.
    setRhsmSyncTimestamp(virtualHostHbiEvent, clock.now().minusHours(5));
    setRhsmSyncTimestamp(hypervisorEvent, clock.now().minusHours(5));

    HbiHost guestHost = virtualHostHbiEvent.getHost();
    OffsetDateTime guestEventTimestamp = virtualHostHbiEvent.getTimestamp().toOffsetDateTime();

    Event expectedUnmappedGuestEvent =
        createExpectedEvent(
            guestHost,
            "HBI_HOST_CREATED",
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
        buildPhysicalRhelEvent(
            hypervisorHbiHost,
            "HBI_HOST_CREATED",
            hypervisorEventTimestamp,
            true,
            buildMeasurements(2.0, 2.0));

    Event expectedMappedGuestEvent =
        createExpectedEvent(
            guestHost,
            "HBI_HOST_MAPPED_GUEST_UPDATE",
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
  }

  /**
   * Tests that when a guest is updated, an event is also sent for the Hypervisor to ensure that the
   * isHypervisor fact is toggled. If the hypervisor is reported when there are no known guests, it
   * will be reported as isHypervisor false. This is because we currently have no way to identify
   * that it is indeed a hypervisor from HBI facts alone. The hypervisor's subscription_manager_id
   * must map to a guest's hypervisor UUID fact.
   */
  @Test
  void
      testIncomingRhelGuestWithKnownHypervisorProducesGuestHostCreatedAndHypervisorUpdatedEvents() {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);

    var hypervisorHostHbiEvent =
        getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());
    // Override the syncTimestamp fact so that it aligns with the current time
    // and is within the configured 'hostLastSyncThreshold'.
    setRhsmSyncTimestamp(hypervisorHostHbiEvent, clock.now().minusHours(5));
    OffsetDateTime initialHypervisorEventTimestamp =
        hypervisorHostHbiEvent.getTimestamp().toOffsetDateTime();

    var hypervisorHbiHost = hypervisorHostHbiEvent.getHost();
    Event expectedInitialHypervisorSwatchEvent =
        buildPhysicalRhelEvent(
            hypervisorHbiHost,
            "HBI_HOST_CREATED",
            initialHypervisorEventTimestamp,
            false,
            buildMeasurements(2.0, 2.0));

    // Send the initial HBI hypervisor event.
    hbiEventsIn.send(hypervisorHostHbiEvent);

    var virtualHostHbiEvent =
        getCreateUpdateEvent(HbiEventTestData.getVirtualRhelHostCreatedEvent());
    // Override the syncTimestamp fact so that it aligns with the current time
    // and is within the configured 'hostLastSyncThreshold'.
    setRhsmSyncTimestamp(virtualHostHbiEvent, clock.now().minusHours(5));
    OffsetDateTime virtualGuestTimestamp = virtualHostHbiEvent.getTimestamp().toOffsetDateTime();

    HbiHost mappedGuest = virtualHostHbiEvent.getHost();
    Event expectedMappedGuestEvent =
        createExpectedEvent(
            mappedGuest,
            "HBI_HOST_CREATED",
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
        buildPhysicalRhelEvent(
            hypervisorHbiHost,
            "HBI_HOST_HYPERVISOR_UPDATED",
            virtualGuestTimestamp,
            true,
            buildMeasurements(2.0, 2.0));

    hbiEventsIn.send(virtualHostHbiEvent);
    assertSwatchEventSent(
        expectedInitialHypervisorSwatchEvent,
        expectedMappedGuestEvent,
        expectedHypervisorUpdateEvent);
  }

  private HbiHostCreateUpdateEvent getCreateUpdateEvent(String messageJson) {
    HbiHostCreateUpdateEvent event =
        HbiEventTestData.getEvent(objectMapper, messageJson, HbiHostCreateUpdateEvent.class);

    // Ensure the event is not stale.
    event.getHost().setStaleTimestamp(clock.now().plusMonths(1).toString());
    return event;
  }

  private void setRhsmSyncTimestamp(HbiHostCreateUpdateEvent event, OffsetDateTime syncTimestamp) {
    setFact(
        event,
        RhsmFacts.RHSM_FACTS_NAMESPACE,
        RhsmFacts.SYNC_TIMESTAMP_FACT,
        syncTimestamp.toString());
  }

  private void setBillingModel(HbiHostCreateUpdateEvent event, String billingModel) {
    setFact(event, RhsmFacts.RHSM_FACTS_NAMESPACE, RhsmFacts.BILLING_MODEL, billingModel);
  }

  private void setHostType(HbiHostCreateUpdateEvent event, String hostType) {
    event.getHost().getSystemProfile().put(SystemProfileFacts.HOST_TYPE_FACT, hostType);
  }

  private Event buildPhysicalRhelEvent(
      HbiHost host,
      String eventType,
      OffsetDateTime timestamp,
      boolean isHypervisor,
      List<Measurement> measurements) {
    return createExpectedEvent(
        host,
        eventType,
        timestamp,
        Sla.SELF_SUPPORT,
        Usage.DEVELOPMENT_TEST,
        HardwareType.PHYSICAL,
        false,
        false,
        isHypervisor,
        null,
        List.of("69"),
        Set.of("RHEL for x86"),
        measurements);
  }

  private Event createExpectedEvent(
      HbiHost host,
      String eventType,
      OffsetDateTime timestamp,
      Sla sla,
      Usage usage,
      HardwareType hardwareType,
      boolean isVirtual,
      boolean isUnmappedGuest,
      boolean isHypervisor,
      String hypervisorUuid,
      List<String> productIds,
      Set<String> tags,
      List<Measurement> measurements) {
    return new Event()
        .withServiceType(HbiEventConsumer.EVENT_SERVICE_TYPE)
        .withEventSource(HbiEventConsumer.EVENT_SOURCE)
        .withEventType(eventType)
        .withTimestamp(timestamp)
        .withLastSeen(OffsetDateTime.parse(host.getUpdated()))
        .withExpiration(Optional.of(timestamp.plusHours(1)))
        .withOrgId(host.getOrgId())
        .withInstanceId(host.getId().toString())
        .withInventoryId(Optional.of(host.id.toString()))
        .withInsightsId(Optional.ofNullable(host.insightsId))
        .withSubscriptionManagerId(Optional.of(host.subscriptionManagerId))
        .withDisplayName(Optional.of(host.displayName))
        .withSla(sla)
        .withUsage(usage)
        .withCloudProvider(null)
        .withHardwareType(hardwareType)
        .withHypervisorUuid(Optional.ofNullable(hypervisorUuid))
        .withProductTag(tags)
        .withProductIds(productIds)
        .withIsVirtual(isVirtual)
        .withIsUnmappedGuest(isUnmappedGuest)
        .withIsHypervisor(isHypervisor)
        .withMeasurements(measurements);
  }

  private List<Measurement> buildMeasurements(double cores, double sockets) {
    return List.of(
        new Measurement().withMetricId("cores").withValue(cores),
        new Measurement().withMetricId("sockets").withValue(sockets));
  }

  private void setFact(
      HbiHostCreateUpdateEvent event, String namespace, String factName, String factValue) {
    HbiHostFacts hostFacts =
        event.getHost().getFacts().stream()
            .filter(f -> namespace.equals(f.getNamespace()))
            .findFirst()
            .orElseThrow();
    hostFacts.getFacts().put(factName, factValue);
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
  }
}
