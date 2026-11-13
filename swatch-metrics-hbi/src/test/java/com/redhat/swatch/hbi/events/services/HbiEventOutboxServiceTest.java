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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.configuration.Channels;
import com.redhat.swatch.hbi.events.repository.HbiEventOutbox;
import com.redhat.swatch.hbi.events.repository.HbiEventOutboxRepository;
import com.redhat.swatch.hbi.events.test.helpers.HbiEventOutboxTestHelper;
import io.getunleash.Unleash;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.Awaitility;
import org.candlepin.subscriptions.json.Event;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HbiEventOutboxServiceTest {

  private static final String ORG_ID = "org123";

  // NOTE:
  //  In order to mock the unleash service used in FeatureFlags
  //  we need to disable the unleash service in the configuration
  //  file.
  @InjectMock Unleash unleash;
  @Inject HbiEventOutboxTestHelper outboxHelper;
  @Inject ApplicationConfiguration config;
  @Inject HbiEventOutboxService service;
  @InjectSpy HbiEventOutboxRepository repository;
  @Inject @Any InMemoryConnector connector;

  private InMemorySink<Event> swatchEventsOut;

  @BeforeEach
  @Transactional
  void clean() {
    swatchEventsOut = connector.sink(Channels.SWATCH_EVENTS_OUT);
    swatchEventsOut.clear();
    repository.deleteAll();
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    when(unleash.isEnabled(FeatureFlags.ENABLE_EVENT_THROTTLING)).thenReturn(false);
    // Reset batch size to default to avoid test-order dependencies
    config.setOutboxFlushBatchSize(1000);
  }

  @Test
  void testFlushOutboxRecords() {
    reset(unleash);
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    when(unleash.isEnabled(FeatureFlags.ENABLE_EVENT_THROTTLING)).thenReturn(false);

    HbiEventOutbox outbox = withExistingOutboxRecord();
    assertEquals(1, service.flushOutboxRecords(new AtomicLong(0)));

    // All records should have been removed after flushing.
    assertEquals(0, repository.count());

    assertSwatchEventSent(outbox.getSwatchEventJson());
  }

  @Test
  void testFlushOutboxRecordsEnforcesBatches() {
    reset(unleash);
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    when(unleash.isEnabled(FeatureFlags.ENABLE_EVENT_THROTTLING)).thenReturn(false);

    // Flush 1 record per batch.
    config.setOutboxFlushBatchSize(1);
    HbiEventOutbox outbox1 = withExistingOutboxRecord();
    HbiEventOutbox outbox2 = withExistingOutboxRecord();
    assertEquals(2, service.flushOutboxRecords(new AtomicLong(0)));

    // Outbox should have been processed in 3 queries (last query returns 0 records and stops loop)
    verify(repository, times(3)).findAllWithLock(config.getOutboxFlushBatchSize());

    // All records should have been removed after flushing.
    assertEquals(0, repository.findByOrgId(ORG_ID).size());

    // Ensure that event messages were sent for both batches.
    assertSwatchEventSent(outbox1.getSwatchEventJson(), outbox2.getSwatchEventJson());
  }

  @Test
  void testFlushOutboxRecordsProtectedByUnleashFlag() {
    reset(unleash);
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(false);

    withExistingOutboxRecord();
    withExistingOutboxRecord();
    withExistingOutboxRecord();
    assertEquals(3, service.flushOutboxRecords(new AtomicLong(0)));

    // All records should have been removed after flushing.
    assertEquals(0, repository.count());

    assertSwatchEventSent();
  }

  @Test
  void testThrottlingDisabledSendsAllEvents() {
    reset(unleash);
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    when(unleash.isEnabled(FeatureFlags.ENABLE_EVENT_THROTTLING)).thenReturn(false);

    config.setEventThrottleCount(2);
    HbiEventOutbox outbox1 = withExistingOutboxRecord();
    HbiEventOutbox outbox2 = withExistingOutboxRecord();
    HbiEventOutbox outbox3 = withExistingOutboxRecord();
    assertEquals(3, service.flushOutboxRecords(new AtomicLong(0)));

    // All records should have been removed after flushing.
    assertEquals(0, repository.count());

    // When throttling is disabled, all events should be sent
    assertSwatchEventSent(
        outbox1.getSwatchEventJson(), outbox2.getSwatchEventJson(), outbox3.getSwatchEventJson());
  }

  @Test
  void testThrottlingEnabledSendsEveryNthEvent() {
    reset(unleash);
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    when(unleash.isEnabled(FeatureFlags.ENABLE_EVENT_THROTTLING)).thenReturn(true);

    config.setEventThrottleCount(2);
    config.setOutboxFlushBatchSize(10);
    HbiEventOutbox outbox1 = withExistingOutboxRecord();
    HbiEventOutbox outbox2 = withExistingOutboxRecord();
    HbiEventOutbox outbox3 = withExistingOutboxRecord();
    HbiEventOutbox outbox4 = withExistingOutboxRecord();
    assertEquals(4, service.flushOutboxRecords(new AtomicLong(0)));

    // All records should have been removed after flushing.
    assertEquals(0, repository.count());

    // With throttle count of 2, first event (position 0) plus events at positions 2 and 4 should be
    // sent
    assertSwatchEventSent(outbox1.getSwatchEventJson(), outbox3.getSwatchEventJson());
  }

  @Test
  void testThrottlingEnabledAlwaysSendsLastEvent() {
    reset(unleash);
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    when(unleash.isEnabled(FeatureFlags.ENABLE_EVENT_THROTTLING)).thenReturn(true);

    config.setEventThrottleCount(3);
    config.setOutboxFlushBatchSize(10);
    HbiEventOutbox outbox1 = withExistingOutboxRecord();
    HbiEventOutbox outbox2 = withExistingOutboxRecord();
    HbiEventOutbox outbox3 = withExistingOutboxRecord();
    HbiEventOutbox outbox4 = withExistingOutboxRecord();
    HbiEventOutbox outbox5 = withExistingOutboxRecord();
    assertEquals(5, service.flushOutboxRecords(new AtomicLong(0)));

    // All records should have been removed after flushing.
    assertEquals(0, repository.count());

    // With throttle count of 3, first event (position 0) plus event at position 3 should be sent
    assertSwatchEventSent(outbox1.getSwatchEventJson(), outbox4.getSwatchEventJson());
  }

  @Test
  void testThrottlingDisabledByEmitEventsFlagPreventsAllEvents() {
    reset(unleash);
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(false);
    when(unleash.isEnabled(FeatureFlags.ENABLE_EVENT_THROTTLING)).thenReturn(false);

    withExistingOutboxRecord();
    withExistingOutboxRecord();
    withExistingOutboxRecord();
    assertEquals(3, service.flushOutboxRecords(new AtomicLong(0)));

    // All records should have been removed after flushing.
    assertEquals(0, repository.count());

    // Even with throttling disabled, if emit events is false, no events should be sent
    assertSwatchEventSent();
  }

  @Test
  void testThrottlingAcrossMultipleBatches() {
    reset(unleash);
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    when(unleash.isEnabled(FeatureFlags.ENABLE_EVENT_THROTTLING)).thenReturn(true);

    config.setEventThrottleCount(2);
    config.setOutboxFlushBatchSize(2);
    HbiEventOutbox outbox1 = withExistingOutboxRecord();
    HbiEventOutbox outbox2 = withExistingOutboxRecord();
    HbiEventOutbox outbox3 = withExistingOutboxRecord();
    HbiEventOutbox outbox4 = withExistingOutboxRecord();
    assertEquals(4, service.flushOutboxRecords(new AtomicLong(0)));

    // All records should have been removed after flushing.
    assertEquals(0, repository.count());

    // With throttle count of 2 and batch size of 2:
    // Batch 1: events 1-2, send event 1 (position 0) and skip event 2
    // Batch 2: events 3-4, send event 3 (count % 2 == 0) and skip event 4
    assertSwatchEventSent(outbox1.getSwatchEventJson(), outbox3.getSwatchEventJson());
  }

  @Test
  void testThrottlingWithCountLargerThanBatchSize() {
    reset(unleash);
    when(unleash.isEnabled(FeatureFlags.EMIT_EVENTS)).thenReturn(true);
    when(unleash.isEnabled(FeatureFlags.ENABLE_EVENT_THROTTLING)).thenReturn(true);

    config.setEventThrottleCount(3);
    config.setOutboxFlushBatchSize(2);
    HbiEventOutbox outbox1 = withExistingOutboxRecord();
    HbiEventOutbox outbox2 = withExistingOutboxRecord();
    HbiEventOutbox outbox3 = withExistingOutboxRecord();
    HbiEventOutbox outbox4 = withExistingOutboxRecord();
    HbiEventOutbox outbox5 = withExistingOutboxRecord();
    HbiEventOutbox outbox6 = withExistingOutboxRecord();
    assertEquals(6, service.flushOutboxRecords(new AtomicLong(0)));

    // All records should have been removed after flushing.
    assertEquals(0, repository.count());

    // With throttle count of 3 and batch size of 2:
    // Event 1 (count 0): send (first event)
    // Event 2 (count 1): skip
    // Event 3 (count 2): skip
    // Event 4 (count 3, 3%3==0): send, reset to 0
    // Event 5 (count 1): skip
    // Event 6 (count 2): skip
    assertSwatchEventSent(outbox1.getSwatchEventJson(), outbox4.getSwatchEventJson());
  }

  @Transactional
  HbiEventOutbox withExistingOutboxRecord() {
    HbiEventOutbox outbox = outboxHelper.createHbiEventOutbox(ORG_ID);
    repository.persist(outbox);
    return outbox;
  }

  private void assertSwatchEventSent(Event... expected) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(() -> {
          List<? extends Message<Event>> received = swatchEventsOut.received();
          assertEquals(expected.length, received.size());
          received.forEach(m -> System.out.println(m.getPayload()));
          MatcherAssert.assertThat(
              received.stream().map(Message::getPayload).toList(),
              Matchers.containsInAnyOrder(expected));
        });

    // Clear the events so that any additional calls to this method start fresh.
    swatchEventsOut.clear();
  }
}
