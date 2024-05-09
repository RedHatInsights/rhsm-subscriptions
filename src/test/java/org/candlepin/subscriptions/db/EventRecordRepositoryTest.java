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
package org.candlepin.subscriptions.db;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.test.ExtendWithSwatchDatabase;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ActiveProfiles({"worker", "test-inventory"})
@Import(TestClockConfiguration.class)
class EventRecordRepositoryTest implements ExtendWithSwatchDatabase {
  private static final Clock CLOCK = new TestClockConfiguration().adjustableClock().getClock();

  @Autowired private EventRecordRepository repository;

  @Test
  void saveAndUpdate() {
    Event event = new Event();
    event.setOrgId("org123");
    event.setTimestamp(OffsetDateTime.now(CLOCK));
    event.setInstanceId("instanceId");
    event.setServiceType("RHEL System");
    event.setEventId(UUID.randomUUID());
    event.setEventSource("eventSource");
    event.setDisplayName(Optional.empty());
    event.setEventType("Prometheus");

    EventRecord record = new EventRecord(event);
    repository.saveAndFlush(record);

    EventRecord found = repository.getReferenceById(record.getEventId());
    assertNull(found.getEvent().getInventoryId());
    assertNotNull(found.getEvent().getDisplayName());
    assertFalse(found.getEvent().getDisplayName().isPresent());
    assertEquals(record, found);
  }

  @Test
  void testFindBeginInclusive() {
    Event oldEvent =
        event("org123", "SOURCE", "TYPE", "INSTANCE", OffsetDateTime.now(CLOCK).minusSeconds(1));
    Event currentEvent = event("org123", "SOURCE", "TYPE", "INSTANCE", OffsetDateTime.now(CLOCK));

    repository.saveAll(Arrays.asList(new EventRecord(oldEvent), new EventRecord(currentEvent)));
    repository.flush();

    List<EventRecord> found =
        repository
            .findByOrgIdAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestamp(
                "org123", OffsetDateTime.now(CLOCK), OffsetDateTime.now(CLOCK).plusYears(1))
            .collect(Collectors.toList());

    assertEquals(1, found.size());
    assertEquals(currentEvent.getEventId(), found.get(0).getEventId());
  }

  @Test
  void testFindEndExclusive() {
    EventRecord futureEvent =
        new EventRecord(event("org123", "SOURCE", "TYPE", "INSTANCE", OffsetDateTime.now(CLOCK)));

    EventRecord currentEvent =
        new EventRecord(
            event(
                "org123", "SOURCE", "TYPE", "INSTANCE", OffsetDateTime.now(CLOCK).minusSeconds(1)));

    repository.saveAll(Arrays.asList(futureEvent, currentEvent));
    repository.flush();

    List<EventRecord> found =
        repository
            .findByOrgIdAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestamp(
                "org123", OffsetDateTime.now(CLOCK).minusYears(1), OffsetDateTime.now(CLOCK))
            .collect(Collectors.toList());

    assertEquals(1, found.size());
    assertEquals(currentEvent.getEvent().getEventId(), found.get(0).getEventId());
  }

  @SuppressWarnings({"linelength", "indentation"})
  @Test
  void findBySourceAndType() {
    EventRecord e1 =
        new EventRecord(event("org123", "SOURCE", "TYPE", "INSTANCE", OffsetDateTime.now(CLOCK)));
    EventRecord e2 =
        new EventRecord(
            event(
                "org123",
                "ANOTHER_SOURCE",
                "ANOTHER_TYPE",
                "INSTANCE",
                OffsetDateTime.now(CLOCK).minusSeconds(1)));
    EventRecord e3 =
        new EventRecord(
            event("org123", "SOURCE", "ANOTHER_TYPE", "INSTANCE", OffsetDateTime.now(CLOCK)));
    EventRecord e4 =
        new EventRecord(
            event("org123", "ANOTHER_SOURCE", "TYPE", "INSTANCE", OffsetDateTime.now(CLOCK)));

    repository.saveAll(List.of(e1, e2, e3, e4));
    repository.flush();

    List<EventRecord> found =
        repository
            .findEventRecordsByCriteria(
                e1.getOrgId(),
                e1.getEventSource(),
                e1.getEventType(),
                OffsetDateTime.now(CLOCK).minusYears(1),
                OffsetDateTime.now(CLOCK).plusYears(1))
            .collect(Collectors.toList());

    assertEquals(1, found.size());
    e1.setRecordDate(found.get(0).getRecordDate());
    assertEquals(e1, found.get(0));
  }

  @Test
  void testDeleteByTimestamp() {
    var now = OffsetDateTime.now();

    EventRecord event =
        EventRecord.builder()
            .eventId(UUID.randomUUID())
            .orgId("org123")
            .eventSource("source")
            .eventType("type")
            .instanceId("instance")
            .timestamp(now.minusDays(91L))
            .build();
    EventRecord event2 =
        EventRecord.builder()
            .eventId(UUID.randomUUID())
            .orgId("org123")
            .eventSource("source")
            .eventType("type")
            .instanceId("instance")
            .timestamp(now.minusDays(1L))
            .build();

    repository.saveAll(List.of(event, event2));

    repository.deleteInBulkEventRecordsByTimestampBefore(now.minusDays(30L));

    var results = repository.findAll();

    assertEquals(1, results.size());
  }

  @Test
  void testFindConflictingEvents() {
    Event event1 = new Event();
    event1.setOrgId("org123");
    event1.setInstanceId("instance1");
    event1.setServiceType("RHEL System");
    event1.setTimestamp(OffsetDateTime.now(CLOCK));
    event1.setEventType("event-type");
    event1.setEventSource("test-data");
    EventRecord eventRecord1 = new EventRecord(event1);

    // Duplicate event1 so that we get a duplicate hash.
    Event event2 = new Event();
    event2.setOrgId("org123");
    event2.setInstanceId("instance1");
    event2.setServiceType("RHEL System");
    event2.setTimestamp(OffsetDateTime.now(CLOCK));
    event2.setEventType("event-type");
    event2.setEventSource("test-data");
    EventRecord eventRecord2 = new EventRecord(event2);

    // Same instance different timestamp.
    Event event3 = new Event();
    event3.setOrgId("org123");
    event3.setInstanceId("instance1");
    event3.setServiceType("RHEL System");
    event3.setTimestamp(OffsetDateTime.now(CLOCK).plusHours(1));
    event3.setEventType("event-type");
    event3.setEventSource("test-data");
    EventRecord eventRecord3 = new EventRecord(event3);

    // Not included due to org_id
    Event event4 = new Event();
    event4.setOrgId("org222");
    event4.setInstanceId("instance3");
    event4.setServiceType("RHEL System");
    event4.setTimestamp(OffsetDateTime.now(CLOCK));
    event4.setEventType("event-type");
    event4.setEventSource("test-data");
    EventRecord eventRecord4 = new EventRecord(event4);

    repository.saveAllAndFlush(List.of(eventRecord1, eventRecord2, eventRecord3, eventRecord4));

    List<EventRecord> match1 = repository.findConflictingEvents(Set.of(EventKey.fromEvent(event1)));
    assertEquals(2, match1.size());
    assertTrue(match1.containsAll(List.of(eventRecord1, eventRecord2)));

    List<EventRecord> match2 = repository.findConflictingEvents(Set.of(EventKey.fromEvent(event3)));
    assertEquals(1, match2.size());
    assertTrue(match2.contains(eventRecord3));

    List<EventRecord> match3 = repository.findConflictingEvents(Set.of(EventKey.fromEvent(event4)));
    assertEquals(1, match3.size());
    assertTrue(match3.contains(eventRecord4));
  }

  private Event event(
      String orgId, String source, String type, String instanceId, OffsetDateTime time) {
    UUID eventId = UUID.randomUUID();
    Event event = new Event();
    event.setEventId(eventId);
    event.setOrgId(orgId);
    event.setTimestamp(time);
    event.setInstanceId(instanceId);
    event.setEventSource(source);
    event.setServiceType("SERVICE_TYPE");
    event.setEventType(type);
    event.setDisplayName(Optional.empty());
    return event;
  }
}
