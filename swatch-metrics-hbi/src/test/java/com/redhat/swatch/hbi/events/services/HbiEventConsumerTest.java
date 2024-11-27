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

import com.redhat.swatch.hbi.events.HbiEventTestData;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostDeleteEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostFacts;
import com.redhat.swatch.hbi.events.kafka.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SystemProfileFacts;
import io.getunleash.Unleash;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
class HbiEventConsumerTest {
  // NOTE:
  //  In order to mock the unleash service used in FeatureFlags
  //  we need to disable the unleash service in the configuration
  //  file.
  @InjectMock Unleash unleash;
  @Inject @Any InMemoryConnector connector;
  @Inject ApplicationClock clock;
  private InMemorySource<HbiEvent> hbiEventsIn;
  private InMemorySink<Event> swatchEventsOut;

  @BeforeEach
  void setup() {
    hbiEventsIn = connector.source(HBI_HOST_EVENTS_IN);
    swatchEventsOut = connector.sink(SWATCH_EVENTS_OUT);
    swatchEventsOut.clear();
  }

  @Test
  void testValidSatelliteVirtualRhelHost() {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    HbiHostCreateUpdateEvent hbiEvent =
        getCreateUpdateEvent(HbiEventTestData.getSatelliteRhelHostCreatedEvent());
    var hbiHost = hbiEvent.getHost();
    Event expected =
        new Event()
            .withServiceType("HBI_HOST")
            .withEventSource("HBI_EVENT")
            .withEventType("HBI_HOST_CREATED")
            .withTimestamp(clock.startOfCurrentHour())
            .withExpiration(Optional.of(clock.startOfCurrentHour().plusHours(1)))
            .withOrgId(hbiHost.getOrgId())
            .withInstanceId(hbiHost.getId().toString())
            .withInventoryId(Optional.of(hbiHost.id.toString()))
            .withInsightsId(Optional.of(hbiHost.insightsId))
            .withSubscriptionManagerId(Optional.of(hbiHost.subscriptionManagerId))
            .withDisplayName(Optional.of(hbiHost.displayName))
            // TODO Need a test that covers when conversion is true.
            .withSla(Sla.PREMIUM)
            .withUsage(Usage.PRODUCTION)
            .withCloudProvider(null)
            .withHardwareType(HardwareType.VIRTUAL)
            // TODO Need a test grabbing this from system profile facts.
            .withHypervisorUuid(Optional.of("bed420fa-59ef-44e5-af8a-62a24473a554"))
            .withProductTag(Set.of("RHEL for x86"))
            .withProductIds(List.of("69", "408"))
            .withMeasurements(
                List.of(
                    new Measurement().withMetricId("cores").withValue(6.0),
                    new Measurement().withMetricId("sockets").withValue(1.0)));

    hbiEventsIn.send(hbiEvent);
    assertSwatchEventSent(expected);
  }

  @Test
  void testPhysicalRhelEvent() {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    var hbiEvent = getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());
    // Override the sync timestamp fact so that it aligns with the current time
    // and is within the configured 'hostLastSyncThreshold'.
    // TODO Replacing the ApplicationClock with a static instance would likely be better.
    setRhsmSyncTimestamp(hbiEvent, clock.now().minusHours(5));

    var hbiHost = hbiEvent.getHost();
    Event expected =
        new Event()
            .withServiceType("HBI_HOST")
            .withEventSource("HBI_EVENT")
            .withEventType("HBI_HOST_CREATED")
            .withTimestamp(clock.startOfCurrentHour())
            .withExpiration(Optional.of(clock.startOfCurrentHour().plusHours(1)))
            .withOrgId(hbiHost.getOrgId())
            .withInstanceId(hbiHost.getId().toString())
            .withInventoryId(Optional.of(hbiHost.id.toString()))
            .withInsightsId(Optional.of(hbiHost.insightsId))
            .withSubscriptionManagerId(Optional.of(hbiHost.subscriptionManagerId))
            .withDisplayName(Optional.of(hbiHost.displayName))
            .withSla(Sla.SELF_SUPPORT)
            .withUsage(Usage.DEVELOPMENT_TEST)
            .withCloudProvider(null)
            .withHardwareType(HardwareType.PHYSICAL)
            .withHypervisorUuid(Optional.empty())
            .withProductTag(Set.of("RHEL for x86"))
            .withProductIds(List.of("69"))
            .withMeasurements(
                List.of(
                    new Measurement().withMetricId("cores").withValue(2.0),
                    new Measurement().withMetricId("sockets").withValue(2.0)));

    hbiEventsIn.send(hbiEvent);
    assertSwatchEventSent(expected);
  }

  @Test
  void testRhsmFactsAreNotConsideredWhenOutsideOfTheSyncThreshold() {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    // The test event has a sync timestamp outside the configured 'hostLastSyncThreshold'.
    var hbiEvent = getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());
    var hbiHost = hbiEvent.getHost();
    Event expected =
        new Event()
            .withServiceType("HBI_HOST")
            .withEventSource("HBI_EVENT")
            .withEventType("HBI_HOST_CREATED")
            .withTimestamp(clock.startOfCurrentHour())
            .withExpiration(Optional.of(clock.startOfCurrentHour().plusHours(1)))
            .withOrgId(hbiHost.getOrgId())
            .withInstanceId(hbiHost.getId().toString())
            .withInventoryId(Optional.of(hbiHost.id.toString()))
            .withInsightsId(Optional.of(hbiHost.getInsightsId()))
            .withSubscriptionManagerId(Optional.of(hbiHost.getSubscriptionManagerId()))
            .withDisplayName(Optional.of(hbiHost.getDisplayName()))
            // TODO Need a test around actually setting cloud provider.
            .withHardwareType(HardwareType.PHYSICAL)
            .withHypervisorUuid(Optional.empty())
            // No expected product tags/ids since 'rhsm' facts would be skipped.
            .withProductTag(Set.of())
            .withProductIds(List.of())
            .withMeasurements(
                List.of(
                    new Measurement().withMetricId("cores").withValue(2.0),
                    // TODO Verify the socket counts are as per expected based on the system type.
                    new Measurement().withMetricId("sockets").withValue(2.0)));

    hbiEventsIn.send(hbiEvent);
    assertSwatchEventSent(expected);
  }

  @Test
  void testQpcRhelHostCreatedEvent() {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    var hbiEvent = getCreateUpdateEvent(HbiEventTestData.getQpcRhelHostCreatedEvent());
    var hbiHost = hbiEvent.getHost();
    Event expected =
        new Event()
            .withServiceType("HBI_HOST")
            .withEventSource("HBI_EVENT")
            .withEventType("HBI_HOST_CREATED")
            .withTimestamp(clock.startOfCurrentHour())
            .withExpiration(Optional.of(clock.startOfCurrentHour().plusHours(1)))
            .withOrgId(hbiHost.getOrgId())
            .withInstanceId(hbiHost.getId().toString())
            .withInventoryId(Optional.of(hbiHost.id.toString()))
            .withInsightsId(Optional.empty())
            .withSubscriptionManagerId(Optional.of(hbiHost.getSubscriptionManagerId()))
            .withDisplayName(Optional.of(hbiHost.getDisplayName()))
            .withHardwareType(HardwareType.VIRTUAL)
            .withHypervisorUuid(Optional.empty())
            .withProductTag(Set.of("RHEL Ungrouped", "RHEL for x86", "RHEL"))
            .withProductIds(List.of())
            .withMeasurements(
                List.of(
                    new Measurement().withMetricId("cores").withValue(4.0),
                    new Measurement().withMetricId("sockets").withValue(4.0)));

    hbiEventsIn.send(hbiEvent);
    assertSwatchEventSent(expected);
  }

  @Test
  void testDeletedEventIsSkipped() {
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    HbiHostDeleteEvent hbiEvent =
        HbiEventTestData.getEvent(HbiEventTestData.getHostDeletedEvent(), HbiHostDeleteEvent.class);
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

  private void assertSwatchEventSent(Event expected) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              List<? extends Message<Event>> received = swatchEventsOut.received();
              assertEquals(1, received.size());
              assertEquals(expected, received.get(0).getPayload());
            });
  }

  // Additional coverage required
  // - tests around the invalid/null/empty values of sla/usage
  // - tests around RHSM facts and Satellite + RHSM facts.
  // - tests around various cores/sockets normalization logic
  //   - systempurpose_unit
  //   - null cores/sockets.

  private HbiHostCreateUpdateEvent getCreateUpdateEvent(String messageJson) {
    HbiHostCreateUpdateEvent event =
        HbiEventTestData.getEvent(messageJson, HbiHostCreateUpdateEvent.class);

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

  private void setFact(
      HbiHostCreateUpdateEvent event, String namespace, String factName, String factValue) {
    HbiHostFacts hostFacts =
        event.getHost().getFacts().stream()
            .filter(f -> namespace.equals(f.getNamespace()))
            .findFirst()
            .orElseThrow();
    hostFacts.getFacts().put(factName, factValue);
  }
}
