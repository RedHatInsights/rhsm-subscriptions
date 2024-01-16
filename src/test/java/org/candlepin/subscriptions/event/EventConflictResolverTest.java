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
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventConflictResolverTest {
  private static final ApplicationClock CLOCK = new TestClockConfiguration().adjustableClock();

  @Mock private EventRecordRepository repo;
  private EventConflictResolver resolver;

  @BeforeEach
  void setupTest() {
    this.resolver = new EventConflictResolver(repo, Mappers.getMapper(ResolvedEventMapper.class));
  }

  @Test
  void testNoEventConflictsYieldsNewEventRecord() {
    EventRecord expectedEvent = withExistingEvent(CLOCK.now(), "cores", 12.0);
    List<EventRecord> resolved = resolver.resolveIncomingEvents(List.of(expectedEvent.getEvent()));
    assertEquals(1, resolved.size());
    assertEquals(expectedEvent, resolved.get(0));
  }

  @Test
  void testEventConflictWithSameMeasurementsYieldsNoEvents() {
    EventRecord existingEvent = withExistingEvent(CLOCK.now(), "cores", 5.0);
    when(repo.findConflictingEvents(List.of(EventKey.fromEvent(existingEvent.getEvent()))))
        .thenReturn(List.of(existingEvent));

    assertTrue(
        resolver.resolveIncomingEvents(List.of(existingEvent.getEvent())).isEmpty(),
        "Expected resolved events to be empty.");
  }

  @Test
  void testEventConflictWithDifferentMeasurementValuesYieldsAmendmentEvents() {
    OffsetDateTime eventTimestamp = CLOCK.now();
    EventRecord existingEventRecord = withExistingEvent(eventTimestamp, "cores", 5.0);

    when(repo.findConflictingEvents(List.of(EventKey.fromEvent(existingEventRecord.getEvent()))))
        .thenReturn(List.of(existingEventRecord));

    Event incomingEvent = withIncomingEvent(eventTimestamp, "cores", 15.0);

    List<EventRecord> resolved = resolver.resolveIncomingEvents(List.of(incomingEvent));
    assertEquals(2, resolved.size());

    Event deductionEvent = resolved.get(0).getEvent();
    assertEquals(1, deductionEvent.getMeasurements().size());
    assertMeasurement(deductionEvent, "cores", -5.0);

    assertEquals(resolved.get(1), new EventRecord(incomingEvent));
  }

  @Test
  void testEventConflictWithOneDifferingMeasurementValueYieldsAmendmentsPlusNewEvent() {
    OffsetDateTime eventTimestamp = CLOCK.now();

    EventRecord existingEventRecord = withExistingEvent(eventTimestamp, "cores", 5.0);

    when(repo.findConflictingEvents(List.of(EventKey.fromEvent(existingEventRecord.getEvent()))))
        .thenReturn(List.of(existingEventRecord));

    Event incomingEvent =
        withIncomingEvent(
            eventTimestamp,
            List.of(
                new Measurement().withUom("cores").withValue(15.0),
                new Measurement().withUom("instance-hours").withValue(15.0)));

    List<EventRecord> resolved = resolver.resolveIncomingEvents(List.of(incomingEvent));
    assertEquals(2, resolved.size());

    Event deductionEvent = resolved.get(0).getEvent();
    assertEquals(1, deductionEvent.getMeasurements().size());
    assertMeasurement(deductionEvent, "cores", -5.0);

    assertEquals(resolved.get(1), new EventRecord(incomingEvent));
  }

  @Test
  void testEventConflictWithExistingAmendmentResolvesToAdditionalAmendment() {
    OffsetDateTime eventTimestamp = CLOCK.now();

    EventRecord initialExistingEvent = withExistingEvent(eventTimestamp, "cores", 5.0);
    EventRecord existingDeductionEvent = withExistingEvent(eventTimestamp, "cores", -5.0);
    EventRecord existingAdjustmentEventRecord = withExistingEvent(eventTimestamp, "cores", 15.0);

    when(repo.findConflictingEvents(
            List.of(EventKey.fromEvent(existingAdjustmentEventRecord.getEvent()))))
        .thenReturn(
            List.of(initialExistingEvent, existingDeductionEvent, existingAdjustmentEventRecord));

    Event incomingEvent = withIncomingEvent(eventTimestamp, "cores", 25.0);

    List<EventRecord> resolved = resolver.resolveIncomingEvents(List.of(incomingEvent));
    assertEquals(2, resolved.size());

    // The existing amendment event of -5.0 cores is ignored as the existing event
    // with measurement of 15 represents the current value at this timestamp. Because
    // of this, the only deduction would be -15 cores.
    Event deductionEvent = resolved.get(0).getEvent();
    assertEquals(1, deductionEvent.getMeasurements().size());
    assertMeasurement(deductionEvent, "cores", -15.0);

    assertEquals(resolved.get(1), new EventRecord(incomingEvent));
  }

  @Test
  void
      testEventConflictResolutionWithSingleIncomingEventAndMultipleExistingEventsWithMultipleMetrics() {
    OffsetDateTime eventTimestamp = CLOCK.now();
    EventRecord event1 = withExistingEvent(eventTimestamp, "cores", 10.0);
    EventRecord event2 = withExistingEvent(eventTimestamp, "instance-hours", 2.0);
    Event incomingEvent =
        withIncomingEvent(
            eventTimestamp,
            List.of(
                new Measurement().withUom("cores").withValue(15.0),
                new Measurement().withUom("instance-hours").withValue(30.0)));

    when(repo.findConflictingEvents(List.of(EventKey.fromEvent(event1.getEvent()))))
        .thenReturn(List.of(event1, event2));

    List<EventRecord> resolved = resolver.resolveIncomingEvents(List.of(incomingEvent));
    assertEquals(3, resolved.size());

    Event deductedCoresEvent = resolved.get(0).getEvent();
    assertEquals(1, deductedCoresEvent.getMeasurements().size());
    assertMeasurement(deductedCoresEvent, "cores", -10.0);

    Event deductedInstanceHoursEvent = resolved.get(1).getEvent();
    assertEquals(1, deductedInstanceHoursEvent.getMeasurements().size());
    assertMeasurement(deductedInstanceHoursEvent, "instance-hours", -2.0);

    assertEquals(new EventRecord(incomingEvent), resolved.get(2));
  }

  @Test
  void testResolutionWithSingleMetricMatchingOneOfMultipleMetrics() {
    OffsetDateTime eventTimestamp = CLOCK.now();
    EventRecord existingEvent =
        withExistingEvent(
            eventTimestamp,
            List.of(
                new Measurement().withUom("cores").withValue(2.0),
                new Measurement().withUom("instance-hours").withValue(3.0)));

    when(repo.findConflictingEvents(List.of(EventKey.fromEvent(existingEvent.getEvent()))))
        .thenReturn(List.of(existingEvent));

    Event incomingEvent = withIncomingEvent(eventTimestamp, "instance-hours", 10.0);
    List<EventRecord> resolved = resolver.resolveIncomingEvents(List.of(incomingEvent));

    assertEquals(2, resolved.size());

    Event deductedInstanceHoursEvent = resolved.get(0).getEvent();
    assertEquals(1, deductedInstanceHoursEvent.getMeasurements().size());
    assertMeasurement(deductedInstanceHoursEvent, "instance-hours", -3.0);

    assertEquals(new EventRecord(incomingEvent), resolved.get(1));
  }

  private Event createEvent(
      String orgId, String serviceType, String instanceId, OffsetDateTime timestamp) {
    return new Event()
        .withOrgId(orgId)
        .withEventType("test_event_type")
        .withEventSource("test_source")
        .withServiceType(serviceType)
        .withInstanceId(instanceId)
        .withTimestamp(timestamp);
  }

  private EventRecord createEventRecord(
      String orgId, String serviceType, String instanceId, OffsetDateTime timestamp) {
    return new EventRecord(createEvent(orgId, serviceType, instanceId, timestamp));
  }

  private EventRecord withExistingEvent(OffsetDateTime timestamp, List<Measurement> measurements) {
    Event existingEvent = withIncomingEvent(timestamp, measurements);
    EventRecord existingEventRecord = new EventRecord(existingEvent);
    existingEventRecord.prePersist();
    return existingEventRecord;
  }

  private EventRecord withExistingEvent(OffsetDateTime timestamp, String uom, Double value) {
    return withExistingEvent(timestamp, List.of(new Measurement().withUom(uom).withValue(value)));
  }

  private Event withIncomingEvent(OffsetDateTime timestamp, String uom, Double value) {
    return withIncomingEvent(timestamp, List.of(new Measurement().withUom(uom).withValue(value)));
  }

  private Event withIncomingEvent(OffsetDateTime timestamp, List<Measurement> measurements) {
    Event existingEvent = createEvent("org123", "OpenShift Cluster", "instance1", timestamp);
    existingEvent.setMeasurements(measurements);
    return existingEvent;
  }

  private void assertMeasurement(Event event, String uom, Double value) {
    List<Measurement> matching =
        event.getMeasurements().stream().filter(m -> uom.equals(m.getUom())).toList();
    Measurement measurement = matching.get(0);
    assertEquals(uom, measurement.getUom());
    assertEquals(value, measurement.getValue());
  }
}
