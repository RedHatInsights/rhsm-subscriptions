/*
 * Copyright (c) 2020 Red Hat, Inc.
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

import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.json.Event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class EventRecordRepositoryTest {
    private static final Clock CLOCK = new FixedClockConfiguration().fixedClock().getClock();

    @Autowired
    private EventRecordRepository repository;

    @Test
    void saveAndUpdate() {
        Event event = new Event();
        event.setAccountNumber("account123");
        event.setTimestamp(OffsetDateTime.now(CLOCK));
        event.setInstanceId("instanceId");
        event.setServiceType("RHEL System");
        UUID eventId = UUID.randomUUID();
        event.setEventId(eventId);
        event.setEventSource("eventSource");
        event.setDisplayName(Optional.empty());

        EventRecord record = EventRecord.fromEvent(event);
        repository.saveAndFlush(record);

        EventRecord found = repository.getOne(eventId);
        assertNull(found.getEvent().getInventoryId());
        assertNotNull(found.getEvent().getDisplayName());
        assertFalse(found.getEvent().getDisplayName().isPresent());
        assertEquals(record, found);
    }

    @Test
    void testFindBeginInclusive() {
        EventRecord oldEvent = new EventRecord();
        UUID oldId = UUID.randomUUID();
        oldEvent.setId(oldId);
        oldEvent.setAccountNumber("account123");
        oldEvent.setTimestamp(OffsetDateTime.now(CLOCK).minusSeconds(1));

        EventRecord currentEvent = new EventRecord();
        UUID currentId = UUID.randomUUID();
        currentEvent.setId(currentId);
        currentEvent.setAccountNumber("account123");
        currentEvent.setTimestamp(OffsetDateTime.now(CLOCK));

        repository.saveAll(Arrays.asList(oldEvent, currentEvent));
        repository.flush();

        List<EventRecord> found = repository
            .findByAccountNumberAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestamp("account123",
            OffsetDateTime.now(CLOCK), OffsetDateTime.now(CLOCK).plusYears(1))
            .collect(Collectors.toList());

        assertEquals(1, found.size());
        assertEquals(currentId, found.get(0).getId());
    }

    @Test
    void testFindEndExclusive() {
        EventRecord futureEvent = new EventRecord();
        UUID futureId = UUID.randomUUID();
        futureEvent.setId(futureId);
        futureEvent.setAccountNumber("account123");
        futureEvent.setTimestamp(OffsetDateTime.now(CLOCK));

        EventRecord currentEvent = new EventRecord();
        UUID currentId = UUID.randomUUID();
        currentEvent.setId(currentId);
        currentEvent.setAccountNumber("account123");
        currentEvent.setTimestamp(OffsetDateTime.now(CLOCK).minusSeconds(1));

        repository.saveAll(Arrays.asList(futureEvent, currentEvent));
        repository.flush();

        List<EventRecord> found = repository
            .findByAccountNumberAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestamp("account123",
            OffsetDateTime.now(CLOCK).minusYears(1), OffsetDateTime.now(CLOCK))
            .collect(Collectors.toList());

        assertEquals(1, found.size());
        assertEquals(currentId, found.get(0).getId());
    }
}
