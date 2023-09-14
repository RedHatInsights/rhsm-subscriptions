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
package org.candlepin.subscriptions.metering.service.prometheus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
      // In tests, messages may be sent before the listener has been assigned the topic
      // so we ensure that when the listener comes online it starts from first message.
      "spring.kafka.consumer.auto-offset-reset=earliest"
    })
@DirtiesContext
// We need the "worker" profile here to consume the events that are produced by the
// profile "openshift-metering-worker".
@ActiveProfiles({"openshift-metering-worker", "worker", "test"})
@EmbeddedKafka(
    partitions = 1,
    topics = {"${rhsm-subscriptions.service-instance-ingress.incoming.topic}"})
class PrometheusEventsProducerTest {

  @Autowired PrometheusEventsProducer eventsProducer;
  @MockBean EventRecordRepository eventRepository;
  @Captor ArgumentCaptor<Collection<EventRecord>> eventsReceived;

  private final Set<Event> events = new HashSet<>();

  @BeforeEach
  public void setup() {
    events.clear();
  }

  @Test
  void testConsumeEvents() {
    givenEvent("org1", "event type 1");
    givenEvent("org2", "event type 1");
    givenEvent("org1", "event type 2");
    whenProduceEvents();
    thenAllEventsAreSaved();
    thenEventsWithOrganizationIdHasExactly("org1", 2);
    thenEventsWithOrganizationIdHasExactly("org2", 1);
  }

  private void whenProduceEvents() {
    events.forEach(eventsProducer::produce);
  }

  private void givenEvent(String orgId, String eventType) {
    var event = new Event();
    event.setEventId(UUID.randomUUID());
    event.setOrgId(orgId);
    event.setEventType(eventType);
    event.setEventSource("any");
    event.setInstanceId("any");
    event.setTimestamp(OffsetDateTime.now());
    events.add(event);
  }

  private void thenAllEventsAreSaved() {
    verify(eventRepository, timeout(3000).times(1)).saveAll(eventsReceived.capture());
  }

  private void thenEventsWithOrganizationIdHasExactly(String orgId, int expected) {
    List<Collection<EventRecord>> allRecords = eventsReceived.getAllValues();
    assertEquals(
        expected,
        allRecords.stream()
            .flatMap(Collection::stream)
            .filter(event -> event.getOrgId().equals(orgId))
            .count());
  }
}
