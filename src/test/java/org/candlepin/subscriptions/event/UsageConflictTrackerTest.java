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
package org.candlepin.subscriptions.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.Test;

class UsageConflictTrackerTest {

  private static final ApplicationClock CLOCK = new TestClockConfiguration().adjustableClock();

  private final String metricId = "M1";
  private final String tag = "Tag1";

  @Test
  void testContains() {
    Event event =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(20.0)));

    UsageConflictTracker tracker = new UsageConflictTracker(List.of(event));

    UsageConflictKey key = new UsageConflictKey(tag, metricId);
    assertTrue(tracker.contains(key));
    assertFalse(tracker.contains(new UsageConflictKey("T2", "M2")));
  }

  @Test
  void testGetLatest() {
    Event oldest =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(20.0)));
    oldest.setRecordDate(CLOCK.now().minusHours(2L));
    Event latest =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(20.0)));
    latest.setRecordDate(CLOCK.now());

    UsageConflictTracker tracker = new UsageConflictTracker(List.of(latest, oldest));
    UsageConflictKey key = new UsageConflictKey(tag, metricId);
    assertTrue(tracker.contains(key));
    assertEquals(latest, tracker.getLatest(key));
  }

  @Test
  void testGetLatestPrefersEventWithNullRecordDate() {
    Event eventWithNullRecordDate =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(20.0)));
    assertTrue(Objects.isNull(eventWithNullRecordDate.getRecordDate()));

    Event latest =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(20.0)));
    latest.setRecordDate(CLOCK.now());

    UsageConflictTracker tracker =
        new UsageConflictTracker(List.of(eventWithNullRecordDate, latest));
    UsageConflictKey key = new UsageConflictKey(tag, metricId);
    assertTrue(tracker.contains(key));
    assertEquals(eventWithNullRecordDate, tracker.getLatest(key));
  }

  @Test
  void testGetLatestEqualsLastTrackedWhenRecordDatesAreTheSame() {
    Event firstTracked =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(20.0)));
    Event lastTracked =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(20.0)));

    UsageConflictTracker tracker = new UsageConflictTracker(List.of());
    tracker.track(firstTracked);
    tracker.track(lastTracked);

    UsageConflictKey key = new UsageConflictKey(tag, metricId);
    assertTrue(tracker.contains(key));
    assertEquals(lastTracked, tracker.getLatest(key));
  }

  @Test
  void testExceptionWhenEventHasMultipleTags() {
    Event event =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(20.0)));
    event.getProductTag().add("T2");

    UsageConflictTracker tracker = new UsageConflictTracker(List.of());
    assertThrows(IllegalStateException.class, () -> tracker.track(event));
  }

  @Test
  void testExceptionWhenEventHasMultipleMeasurement() {
    Event event =
        createEvent(
            CLOCK.now(),
            List.of(
                new Measurement().withMetricId(metricId).withValue(20.0),
                new Measurement().withMetricId("M2").withValue(10.0)));

    UsageConflictTracker tracker = new UsageConflictTracker(List.of());
    assertThrows(IllegalStateException.class, () -> tracker.track(event));
  }

  @Test
  void testRecordDateNormalizationWithNanosecondPrecision() {
    // Test that events with nanosecond precision differences are treated as having the same time
    // when they differ only in nanoseconds within the same microsecond
    OffsetDateTime baseTime = OffsetDateTime.parse("2025-01-21T18:30:02.545245Z");
    OffsetDateTime timeWithNanos1 = baseTime.plusNanos(510L); // 2025-01-21T18:30:02.545245510Z
    OffsetDateTime timeWithNanos2 = baseTime.plusNanos(900L); // 2025-01-21T18:30:02.545245900Z

    Event event1 =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(20.0)));
    event1.setRecordDate(timeWithNanos1);

    Event event2 =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(30.0)));
    event2.setRecordDate(timeWithNanos2);

    // Track events in order - since normalized times are equal, last tracked should win
    UsageConflictTracker tracker = new UsageConflictTracker(List.of(event1, event2));

    UsageConflictKey key = new UsageConflictKey(tag, metricId);
    assertEquals(
        event2,
        tracker.getLatest(key),
        "When record dates normalize to the same microsecond, the last tracked event should be returned");
  }

  @Test
  void testRecordDateNormalizationWithMicrosecondDifferences() {
    // Test that events with different microsecond timestamps are handled correctly
    OffsetDateTime earlierTime = OffsetDateTime.parse("2025-01-21T18:30:02.545245Z");
    OffsetDateTime laterTime = OffsetDateTime.parse("2025-01-21T18:30:02.545246Z");

    Event earlierEvent =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(20.0)));
    earlierEvent.setRecordDate(earlierTime.plusNanos(999L)); // Add nanoseconds to earlier time

    Event laterEvent =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(30.0)));
    laterEvent.setRecordDate(laterTime.plusNanos(1L)); // Add minimal nanoseconds to later time

    UsageConflictTracker tracker = new UsageConflictTracker(List.of(earlierEvent, laterEvent));

    UsageConflictKey key = new UsageConflictKey(tag, metricId);
    assertEquals(
        laterEvent,
        tracker.getLatest(key),
        "Event with later microsecond timestamp should be returned even with nanosecond precision");
  }

  @Test
  void testRecordDateNormalizationWithMixedPrecision() {
    // Test mixing events with different timestamp precisions
    OffsetDateTime microsecondTime = OffsetDateTime.parse("2025-01-21T18:30:02.545245Z");
    OffsetDateTime nanosecondTime = microsecondTime.plusNanos(456L);

    Event microsecondEvent =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(20.0)));
    microsecondEvent.setRecordDate(microsecondTime);

    Event nanosecondEvent =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(30.0)));
    nanosecondEvent.setRecordDate(nanosecondTime);

    // Track nanosecond event first, then microsecond event
    UsageConflictTracker tracker =
        new UsageConflictTracker(List.of(nanosecondEvent, microsecondEvent));

    UsageConflictKey key = new UsageConflictKey(tag, metricId);
    assertEquals(
        microsecondEvent,
        tracker.getLatest(key),
        "When normalized times are equal, the last tracked event should win regardless of original precision");
  }

  @Test
  void testRecordDateNormalizationPrefersNullOverNormalizedTime() {
    // Test that null record date is still preferred over any normalized timestamp
    OffsetDateTime someTime = OffsetDateTime.parse("2025-01-21T18:30:02.545245456Z");

    Event eventWithTime =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(20.0)));
    eventWithTime.setRecordDate(someTime);

    Event eventWithNullTime =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(30.0)));
    // eventWithNullTime.recordDate remains null

    // Track event with time first, then event with null time
    UsageConflictTracker tracker =
        new UsageConflictTracker(List.of(eventWithTime, eventWithNullTime));

    UsageConflictKey key = new UsageConflictKey(tag, metricId);
    assertEquals(
        eventWithNullTime,
        tracker.getLatest(key),
        "Event with null record date should be preferred over event with normalized timestamp");
  }

  @Test
  void testRecordDateNormalizationWithIdenticalNormalizedTimes() {
    // Test that when multiple events normalize to exactly the same time, last tracked wins
    OffsetDateTime baseTime = OffsetDateTime.parse("2025-01-21T18:30:02.545245Z");

    Event event1 =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(10.0)));
    event1.setRecordDate(baseTime.plusNanos(100L));

    Event event2 =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(20.0)));
    event2.setRecordDate(baseTime.plusNanos(200L));

    Event event3 =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(30.0)));
    event3.setRecordDate(baseTime.plusNanos(999L));

    UsageConflictTracker tracker = new UsageConflictTracker(List.of(event1, event2, event3));

    UsageConflictKey key = new UsageConflictKey(tag, metricId);
    assertEquals(
        event3,
        tracker.getLatest(key),
        "When all events normalize to the same microsecond, the last tracked should be returned");
  }

  @Test
  void testRecordDateNormalizationWithZeroNanoseconds() {
    // Test normalization when timestamp already has zero nanoseconds
    OffsetDateTime exactMicrosecondTime = OffsetDateTime.parse("2025-01-21T18:30:02.545245Z");
    OffsetDateTime timeWithNanos = exactMicrosecondTime.plusNanos(456L);

    Event exactEvent =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(20.0)));
    exactEvent.setRecordDate(exactMicrosecondTime);

    Event nanosEvent =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(30.0)));
    nanosEvent.setRecordDate(timeWithNanos);

    UsageConflictTracker tracker = new UsageConflictTracker(List.of(exactEvent, nanosEvent));

    UsageConflictKey key = new UsageConflictKey(tag, metricId);
    assertEquals(
        nanosEvent,
        tracker.getLatest(key),
        "Events with same microsecond timestamp should result in last tracked winning");
  }

  @Test
  void testRecordDateNormalizationAcrossMicrosecondBoundary() {
    // Test events that are very close but cross microsecond boundaries
    OffsetDateTime time1 =
        OffsetDateTime.parse("2025-01-21T18:30:02.545245999Z"); // 545245μs + 999ns
    OffsetDateTime time2 = OffsetDateTime.parse("2025-01-21T18:30:02.545246001Z"); // 545246μs + 1ns

    Event event1 =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(20.0)));
    event1.setRecordDate(time1);

    Event event2 =
        createEvent(CLOCK.now(), List.of(new Measurement().withMetricId(metricId).withValue(30.0)));
    event2.setRecordDate(time2);

    UsageConflictTracker tracker = new UsageConflictTracker(List.of(event1, event2));

    UsageConflictKey key = new UsageConflictKey(tag, metricId);
    assertEquals(
        event2,
        tracker.getLatest(key),
        "Event with later microsecond should be preferred even when nanosecond difference is minimal");
  }

  private Event createEvent(OffsetDateTime timestamp, List<Measurement> measurements) {
    return new Event()
        .withOrgId("org1")
        .withEventType("test_event_type")
        .withEventSource("test_source")
        .withServiceType("test_service_type")
        .withInstanceId("instance1")
        .withProductTag(new HashSet<>(List.of(tag)))
        .withTimestamp(timestamp)
        .withMeasurements(measurements);
  }
}
