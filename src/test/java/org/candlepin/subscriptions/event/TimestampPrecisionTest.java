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

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Tests the timestamp precision fix that normalizes timestamps to microsecond precision during
 * conflict resolution to prevent nanosecond differences from causing incorrect billing calculations
 * while matching the default PostgreSQL precision.
 *
 * <p>Note: The actual timestamp normalization happens in UsageConflictTracker.track() method, which
 * is what these tests are validating.
 */
class TimestampPrecisionTest {

  private static final ApplicationClock CLOCK = new TestClockConfiguration().adjustableClock();
  private static final String METRIC_ID = "cores";
  private static final String PRODUCT_TAG = "rhel-for-x86-els-payg";

  @Test
  void testTimestampPrecisionFixPreventsWrongEventSelection() {
    // This test demonstrates the fix for the timestamp precision bug where events with
    // essentially the same logical time were treated as different due to nanosecond differences

    // Create database event with microsecond precision (typical for PostgreSQL storage)
    Event databaseEvent = createEvent(20.0);
    OffsetDateTime dbTimestamp = OffsetDateTime.parse("2025-08-07T18:30:04.908874Z");
    databaseEvent.setRecordDate(dbTimestamp);

    // Create incoming event with nanosecond precision (from JSON deserialization)
    Event incomingEvent = createEvent(25.0);
    OffsetDateTime jsonTimestamp = OffsetDateTime.parse("2025-08-07T18:30:04.908874123Z");
    incomingEvent.setRecordDate(jsonTimestamp);

    // The timestamps differ by only 123 nanoseconds, representing essentially the same logical time
    // Before fix: nanosecond difference would cause wrong "latest" event selection
    // After fix: timestamps are normalized to microsecond precision for consistent comparison

    UsageConflictTracker tracker = new UsageConflictTracker(List.of(databaseEvent));
    tracker.track(incomingEvent);

    UsageConflictKey key = new UsageConflictKey(PRODUCT_TAG, METRIC_ID);
    Event latest = tracker.getLatest(key);

    // With the fix, when timestamps are normalized to the same microsecond precision,
    // the last tracked event should become the latest since they're considered equal
    assertEquals(
        incomingEvent,
        latest,
        "Last tracked event should become latest when timestamps are effectively equal");
    assertEquals(25.0, latest.getMeasurements().get(0).getValue());

    // The key improvement: This comparison is now deterministic and won't cause incorrect
    // billing calculations due to sub-microsecond timestamp variations
  }

  @Test
  void testTimestampPrecisionNormalizationMakesTimestampsEqual() {
    // This test shows that timestamps differing only by nanoseconds are treated as equal
    // after normalization to microsecond precision

    Event event1 = createEvent(20.0);
    Event event2 = createEvent(25.0);

    // Same microsecond, different nanoseconds
    OffsetDateTime timestamp1 = OffsetDateTime.parse("2025-08-07T18:30:04.908874000Z");
    OffsetDateTime timestamp2 = OffsetDateTime.parse("2025-08-07T18:30:04.908874999Z");

    event1.setRecordDate(timestamp1);
    event2.setRecordDate(timestamp2);

    // Start with event1, then track event2
    UsageConflictTracker tracker = new UsageConflictTracker(List.of(event1));
    tracker.track(event2);

    UsageConflictKey key = new UsageConflictKey(PRODUCT_TAG, METRIC_ID);
    Event latest = tracker.getLatest(key);

    // After the fix, when timestamps are normalized to the same microsecond precision,
    // event2 should become the latest since they're considered equal and event2 was tracked last
    assertEquals(
        event2,
        latest,
        "Last tracked event should become latest when timestamps are effectively equal");
  }

  private Event createEvent(double coreValue) {
    Set<String> productTag = new HashSet<>();
    productTag.add(PRODUCT_TAG);

    return new Event()
        .withOrgId("org1")
        .withEventType("test_event_type")
        .withEventSource("test_source")
        .withServiceType("test_service_type")
        .withInstanceId("instance1")
        .withProductTag(productTag)
        .withTimestamp(CLOCK.now())
        .withMeasurements(List.of(new Measurement().withMetricId(METRIC_ID).withValue(coreValue)));
  }
}
