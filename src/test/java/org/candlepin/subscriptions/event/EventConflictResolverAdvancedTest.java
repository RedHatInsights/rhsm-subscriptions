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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.AmendmentType;
import org.candlepin.subscriptions.json.Event.HardwareType;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Advanced test cases for EventConflictResolver focusing on edge cases and gaps in testing that
 * could lead to usage double-counting.
 */
@ExtendWith(MockitoExtension.class)
class EventConflictResolverAdvancedTest {

  private static final ApplicationClock CLOCK = new TestClockConfiguration().adjustableClock();
  private static final String TAG1 = "RHEL";
  private static final String TAG2 = "OpenShift";
  private static final String CORES = "cores";
  private static final String INSTANCE_HOURS = "instance-hours";

  @Mock private EventRecordRepository repo;
  private EventConflictResolver resolver;

  @BeforeEach
  void setupTest() {
    this.resolver = new EventConflictResolver(repo, Mappers.getMapper(ResolvedEventMapper.class));
  }

  @Test
  void testMultipleSourcesPriorityNotImplementedYet() {
    // Test case to document current behavior before implementing source prioritization
    // Currently uses "last in wins" regardless of source
    List<Event> existingEvents =
        List.of(createEvent("source1", TAG1, CORES, 10.0, CLOCK.now().minusHours(1)));

    List<Event> incomingEvents = List.of(createEvent("source2", TAG1, CORES, 5.0, CLOCK.now()));

    UsageConflictTracker tracker = new UsageConflictTracker(existingEvents);
    List<Event> resolved = resolver.processEventsSequentially(incomingEvents, tracker);

    // Currently, last in wins regardless of source
    assertEquals(2, resolved.size());
    assertTrue(resolved.get(0).getAmendmentType() == AmendmentType.DEDUCTION);
    assertEquals(-10.0, resolved.get(0).getMeasurements().get(0).getValue());
    assertEquals(5.0, resolved.get(1).getMeasurements().get(0).getValue());
  }

  @Test
  void testComplexIntraBatchScenarioWithMultipleMetrics() {
    // Test scenario: Multiple events in same batch affecting different metrics
    // for same product tag - should handle conflicts per metric independently
    List<Event> incomingEvents =
        List.of(
            createEvent("source1", TAG1, CORES, 10.0, CLOCK.now()),
            createEvent("source1", TAG1, INSTANCE_HOURS, 24.0, CLOCK.now()),
            createEvent(
                "source2", TAG1, CORES, 8.0, CLOCK.now()), // Conflicts with first cores event
            createEvent(
                "source3", TAG1, INSTANCE_HOURS, 20.0, CLOCK.now()) // Conflicts with instance-hours
            );

    UsageConflictTracker tracker = new UsageConflictTracker(List.of());
    List<Event> resolved = resolver.processEventsSequentially(incomingEvents, tracker);

    // Should have 2 final events: cores=8.0, instance-hours=20.0
    // Plus 2 deduction events for the overridden values
    assertEquals(4, resolved.size());

    // Verify final state contains latest values for each metric
    long coresEvents =
        resolved.stream()
            .filter(e -> e.getAmendmentType() == null)
            .filter(e -> CORES.equals(e.getMeasurements().get(0).getMetricId()))
            .count();
    assertEquals(1, coresEvents);

    long instanceHoursEvents =
        resolved.stream()
            .filter(e -> e.getAmendmentType() == null)
            .filter(e -> INSTANCE_HOURS.equals(e.getMeasurements().get(0).getMetricId()))
            .count();
    assertEquals(1, instanceHoursEvents);
  }

  @Test
  void testEventOrderingWithinBatchMatters() {
    // Test that processing order within batch affects final result
    // This verifies the sequential processing behavior
    List<Event> batchOrder1 =
        List.of(
            createEvent("source1", TAG1, CORES, 10.0, CLOCK.now()),
            createEvent("source2", TAG1, CORES, 20.0, CLOCK.now()));

    List<Event> batchOrder2 =
        List.of(
            createEvent("source2", TAG1, CORES, 20.0, CLOCK.now()),
            createEvent("source1", TAG1, CORES, 10.0, CLOCK.now()));

    UsageConflictTracker tracker1 = new UsageConflictTracker(List.of());
    List<Event> resolved1 = resolver.processEventsSequentially(batchOrder1, tracker1);

    UsageConflictTracker tracker2 = new UsageConflictTracker(List.of());
    List<Event> resolved2 = resolver.processEventsSequentially(batchOrder2, tracker2);

    // Final values should be different based on processing order
    double finalValue1 =
        resolved1.stream()
            .filter(e -> e.getAmendmentType() == null)
            .findFirst()
            .get()
            .getMeasurements()
            .get(0)
            .getValue();

    double finalValue2 =
        resolved2.stream()
            .filter(e -> e.getAmendmentType() == null)
            .findFirst()
            .get()
            .getMeasurements()
            .get(0)
            .getValue();

    assertEquals(20.0, finalValue1); // Last in batch order 1
    assertEquals(10.0, finalValue2); // Last in batch order 2
  }

  @Test
  void testZeroValueEventsStillCreateDeductions() {
    // Test that zero-value events still trigger conflict resolution
    // This is important to prevent phantom usage from remaining
    List<Event> existingEvents =
        List.of(createEvent("source1", TAG1, CORES, 10.0, CLOCK.now().minusHours(1)));

    List<Event> incomingEvents = List.of(createEvent("source2", TAG1, CORES, 0.0, CLOCK.now()));

    UsageConflictTracker tracker = new UsageConflictTracker(existingEvents);
    List<Event> resolved = resolver.processEventsSequentially(incomingEvents, tracker);

    assertEquals(2, resolved.size());
    assertTrue(resolved.get(0).getAmendmentType() == AmendmentType.DEDUCTION);
    assertEquals(-10.0, resolved.get(0).getMeasurements().get(0).getValue());
    assertEquals(0.0, resolved.get(1).getMeasurements().get(0).getValue());
  }

  @Test
  void testNegativeValueEventsHandling() {
    // Test handling of negative measurement values
    // Verify they don't cause double-negative scenarios
    List<Event> existingEvents =
        List.of(createEvent("source1", TAG1, CORES, 10.0, CLOCK.now().minusHours(1)));

    List<Event> incomingEvents = List.of(createEvent("source2", TAG1, CORES, -5.0, CLOCK.now()));

    UsageConflictTracker tracker = new UsageConflictTracker(existingEvents);
    List<Event> resolved = resolver.processEventsSequentially(incomingEvents, tracker);

    assertEquals(2, resolved.size());
    assertTrue(resolved.get(0).getAmendmentType() == AmendmentType.DEDUCTION);
    assertEquals(-10.0, resolved.get(0).getMeasurements().get(0).getValue());
    assertEquals(-5.0, resolved.get(1).getMeasurements().get(0).getValue());
  }

  @Test
  void testDifferentInstancesSameOrgSameTimestamp() {
    // Test that events for different instances don't conflict
    // even if they share org and timestamp
    Event instance1Event = createEvent("source1", TAG1, CORES, 10.0, CLOCK.now());
    instance1Event.setInstanceId("instance1");

    Event instance2Event = createEvent("source1", TAG1, CORES, 15.0, CLOCK.now());
    instance2Event.setInstanceId("instance2");

    List<Event> incomingEvents = List.of(instance1Event, instance2Event);
    UsageConflictTracker tracker = new UsageConflictTracker(List.of());
    List<Event> resolved = resolver.processEventsSequentially(incomingEvents, tracker);

    // Should have both events with no deductions since different instances
    assertEquals(2, resolved.size());
    assertTrue(resolved.stream().noneMatch(e -> e.getAmendmentType() != null));
  }

  private Event createEvent(
      String source, String productTag, String metricId, Double value, OffsetDateTime timestamp) {
    return new Event()
        .withOrgId("org1")
        .withEventType("test_event_type")
        .withEventSource(source)
        .withServiceType("test_service_type")
        .withInstanceId("instance_1")
        .withProductTag(Set.of(productTag))
        .withTimestamp(timestamp)
        .withMeasurements(List.of(new Measurement().withMetricId(metricId).withValue(value)))
        .withHardwareType(HardwareType.PHYSICAL);
  }
}
