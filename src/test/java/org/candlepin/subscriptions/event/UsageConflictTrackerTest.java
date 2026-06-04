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
