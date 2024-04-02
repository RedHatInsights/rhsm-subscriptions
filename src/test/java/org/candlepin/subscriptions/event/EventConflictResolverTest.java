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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.AmendmentType;
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
    EventRecord expectedEventRecord = withExistingEvent("instance1", CLOCK.now(), "cores", 12.0);
    Event expectedEvent = expectedEventRecord.getEvent();

    List<EventRecord> resolved =
        resolver.resolveIncomingEvents(Map.of(EventKey.fromEvent(expectedEvent), expectedEvent));
    assertEquals(1, resolved.size());
    EventRecord resolvedEvent = resolved.get(0);
    assertNull(resolvedEvent.getEvent().getAmendmentType());
    assertEquals(expectedEventRecord, resolvedEvent);
  }

  @Test
  void testEventConflictWithSameMeasurementsYieldsNoEvents() {
    EventRecord existingEventRecord = withExistingEvent("instance1", CLOCK.now(), "cores", 5.0);
    Event existingEvent = existingEventRecord.getEvent();
    EventKey existingEventKey = EventKey.fromEvent(existingEvent);
    Map<EventKey, Event> existingEventMap = Map.of(existingEventKey, existingEvent);

    when(repo.findConflictingEvents(existingEventMap.keySet()))
        .thenReturn(List.of(existingEventRecord));

    List<EventRecord> resolved = resolver.resolveIncomingEvents(existingEventMap);
    assertTrue(resolved.isEmpty(), "Expected resolved events to be empty.");
  }

  @Test
  void testEventConflictWithDifferentMeasurementValuesYieldsAmendmentEvents() {
    OffsetDateTime eventTimestamp = CLOCK.now();
    String instanceId = "instance1";
    EventRecord existingEventRecord = withExistingEvent(instanceId, eventTimestamp, "cores", 5.0);

    when(repo.findConflictingEvents(Set.of(EventKey.fromEvent(existingEventRecord.getEvent()))))
        .thenReturn(List.of(existingEventRecord));

    Event incomingEvent = withIncomingEvent(instanceId, eventTimestamp, "cores", 15.0);

    List<EventRecord> resolved =
        resolver.resolveIncomingEvents(Map.of(EventKey.fromEvent(incomingEvent), incomingEvent));
    assertEquals(2, resolved.size());

    Event deductionEvent = resolved.get(0).getEvent();
    assertEquals(AmendmentType.DEDUCTION, deductionEvent.getAmendmentType());
    assertEquals(1, deductionEvent.getMeasurements().size());
    assertMetricIdValue(deductionEvent, "cores", -5.0);

    assertEquals(resolved.get(1), new EventRecord(incomingEvent));
  }

  @Test
  void testEventConflictWithOneDifferingMeasurementValueYieldsAmendmentsPlusNewEvent() {
    OffsetDateTime eventTimestamp = CLOCK.now();
    String instanceId = "instance1";

    EventRecord existingEventRecord = withExistingEvent(instanceId, eventTimestamp, "cores", 5.0);

    when(repo.findConflictingEvents(Set.of(EventKey.fromEvent(existingEventRecord.getEvent()))))
        .thenReturn(List.of(existingEventRecord));

    Event incomingEvent =
        withIncomingEvent(
            instanceId,
            eventTimestamp,
            List.of(
                new Measurement().withMetricId("cores").withValue(15.0),
                new Measurement().withMetricId("instance-hours").withValue(15.0)));

    List<EventRecord> resolved =
        resolver.resolveIncomingEvents(Map.of(EventKey.fromEvent(incomingEvent), incomingEvent));

    assertEquals(2, resolved.size());

    Event deductionEvent = resolved.get(0).getEvent();
    assertEquals(AmendmentType.DEDUCTION, deductionEvent.getAmendmentType());
    assertEquals(1, deductionEvent.getMeasurements().size());
    assertMetricIdValue(deductionEvent, "cores", -5.0);

    assertEquals(resolved.get(1), new EventRecord(incomingEvent));
  }

  @Test
  void testEventConflictWithExistingAmendmentResolvesToAdditionalAmendment() {
    OffsetDateTime eventTimestamp = CLOCK.now();
    String instanceId = "instance1";

    EventRecord initialExistingEvent = withExistingEvent(instanceId, eventTimestamp, "cores", 5.0);
    EventRecord existingDeductionEvent =
        withExistingEvent(instanceId, eventTimestamp, "cores", -5.0);
    EventRecord existingAdjustmentEventRecord =
        withExistingEvent(instanceId, eventTimestamp, "cores", 15.0);

    when(repo.findConflictingEvents(
            Set.of(EventKey.fromEvent(existingAdjustmentEventRecord.getEvent()))))
        .thenReturn(
            List.of(initialExistingEvent, existingDeductionEvent, existingAdjustmentEventRecord));

    Event incomingEvent = withIncomingEvent(instanceId, eventTimestamp, "cores", 25.0);

    List<EventRecord> resolved =
        resolver.resolveIncomingEvents(Map.of(EventKey.fromEvent(incomingEvent), incomingEvent));
    assertEquals(2, resolved.size());

    // The existing amendment event of -5.0 cores is ignored as the existing event
    // with measurement of 15 represents the current value at this timestamp. Because
    // of this, the only deduction would be -15 cores.
    Event deductionEvent = resolved.get(0).getEvent();
    assertEquals(AmendmentType.DEDUCTION, deductionEvent.getAmendmentType());
    assertEquals(1, deductionEvent.getMeasurements().size());
    assertMetricIdValue(deductionEvent, "cores", -15.0);

    assertEquals(resolved.get(1), new EventRecord(incomingEvent));
  }

  @Test
  void
      testEventConflictResolutionWithSingleIncomingEventAndMultipleExistingEventsWithMultipleMetrics() {
    OffsetDateTime eventTimestamp = CLOCK.now();
    String instanceId = "instance1";
    EventRecord event1 = withExistingEvent(instanceId, eventTimestamp, "cores", 10.0);
    EventRecord event2 = withExistingEvent(instanceId, eventTimestamp, "instance-hours", 2.0);
    Event incomingEvent =
        withIncomingEvent(
            instanceId,
            eventTimestamp,
            List.of(
                new Measurement().withMetricId("cores").withValue(15.0),
                new Measurement().withMetricId("instance-hours").withValue(30.0)));

    when(repo.findConflictingEvents(Set.of(EventKey.fromEvent(event1.getEvent()))))
        .thenReturn(List.of(event1, event2));

    List<EventRecord> resolved =
        resolver.resolveIncomingEvents(Map.of(EventKey.fromEvent(incomingEvent), incomingEvent));
    assertEquals(3, resolved.size());
    assertDeductionEvent(resolved.get(0).getEvent(), instanceId, "cores", -10.0);
    assertDeductionEvent(resolved.get(1).getEvent(), instanceId, "instance-hours", -2.0);
    assertEquals(new EventRecord(incomingEvent), resolved.get(2));
  }

  @Test
  void testResolutionWithSingleMetricMatchingOneOfMultipleMetrics() {
    OffsetDateTime eventTimestamp = CLOCK.now();
    String instanceId = "instance1";
    EventRecord existingEvent =
        withExistingEvent(
            instanceId,
            eventTimestamp,
            List.of(
                new Measurement().withMetricId("cores").withValue(2.0),
                new Measurement().withMetricId("instance-hours").withValue(3.0)));

    when(repo.findConflictingEvents(Set.of(EventKey.fromEvent(existingEvent.getEvent()))))
        .thenReturn(List.of(existingEvent));

    Event incomingEvent = withIncomingEvent(instanceId, eventTimestamp, "instance-hours", 10.0);
    List<EventRecord> resolved =
        resolver.resolveIncomingEvents(Map.of(EventKey.fromEvent(incomingEvent), incomingEvent));

    assertEquals(2, resolved.size());

    Event deductionEvent = resolved.get(0).getEvent();
    assertEquals(AmendmentType.DEDUCTION, deductionEvent.getAmendmentType());
    assertEquals(1, deductionEvent.getMeasurements().size());
    assertMetricIdValue(deductionEvent, "instance-hours", -3.0);

    assertEquals(new EventRecord(incomingEvent), resolved.get(1));
  }

  @Test
  void testEventResolutionWithMultipleInstances() {
    OffsetDateTime eventTimestamp = CLOCK.now();
    String instance1Id = "instance1";
    EventRecord i1ExistingEvent = withExistingEvent(instance1Id, eventTimestamp, "cores", 10.0);
    Event i1IncomingEvent = withIncomingEvent(instance1Id, eventTimestamp, "cores", 20.0);

    String instance2Id = "instance2";
    EventRecord i2ExistingEvent = withExistingEvent(instance2Id, eventTimestamp, "cores", 1.0);
    Event i2IncomingEvent = withIncomingEvent(instance2Id, eventTimestamp, "cores", 4.0);

    Set<EventKey> lookupKeys =
        Set.of(
            EventKey.fromEvent(i1ExistingEvent.getEvent()),
            EventKey.fromEvent(i2ExistingEvent.getEvent()));
    when(repo.findConflictingEvents(lookupKeys))
        .thenReturn(List.of(i1ExistingEvent, i2ExistingEvent));

    Map<EventKey, Event> incomingEvents =
        Map.of(
            EventKey.fromEvent(i1IncomingEvent), i1IncomingEvent,
            EventKey.fromEvent(i2IncomingEvent), i2IncomingEvent);

    List<EventRecord> resolved =
        resolver.resolveIncomingEvents(incomingEvents).stream()
            .sorted(Comparator.comparing(EventRecord::getInstanceId))
            .toList();
    assertEquals(4, resolved.size());
    assertDeductionEvent(resolved.get(0).getEvent(), instance1Id, "cores", -10.0);
    assertResolvedEvent(resolved.get(1).getEvent(), instance1Id, "cores", 20.0);
    assertDeductionEvent(resolved.get(2).getEvent(), instance2Id, "cores", -1.0);
    assertResolvedEvent(resolved.get(3).getEvent(), instance2Id, "cores", 4.0);
  }

  @Test
  void testEventResolutionWillPreferMetricIdOverUomButSupportsBoth() {
    // NOTE We should never see a case where the metric_id and uom are different
    //      for a single measurement, but will test the edge case just in case.
    OffsetDateTime eventTimestamp = CLOCK.now();
    String instanceId = "instance1";
    EventRecord event1 =
        withExistingEvent(
            instanceId,
            eventTimestamp,
            List.of(
                new Measurement().withUom("CoresIgnored").withMetricId("Cores").withValue(1.0),
                new Measurement().withMetricId("Instance-hours").withValue(5.0)));

    Event incomingEvent =
        withIncomingEvent(
            instanceId,
            eventTimestamp,
            List.of(
                // Should be applied to the existing Cores value.
                new Measurement().withUom("Cores").withValue(15.0),
                new Measurement().withUom("Instance-hours").withMetricId("").withValue(30.0)));

    when(repo.findConflictingEvents(Set.of(EventKey.fromEvent(event1.getEvent()))))
        .thenReturn(List.of(event1));

    List<EventRecord> resolved =
        resolver.resolveIncomingEvents(Map.of(EventKey.fromEvent(incomingEvent), incomingEvent));
    assertEquals(3, resolved.size());
    assertDeductionEvent(resolved.get(0).getEvent(), instanceId, "Cores", -1.0);
    assertDeductionEvent(resolved.get(1).getEvent(), instanceId, "Instance-hours", -5.0);
    assertEquals(new EventRecord(incomingEvent), resolved.get(2));
  }

  private Event createEvent(String instanceId, OffsetDateTime timestamp) {
    return new Event()
        .withOrgId("org1")
        .withEventType("test_event_type")
        .withEventSource("test_source")
        .withServiceType("test_service_type")
        .withInstanceId(instanceId)
        .withTimestamp(timestamp);
  }

  private EventRecord withExistingEvent(
      String instanceId, OffsetDateTime timestamp, List<Measurement> measurements) {
    Event existingEvent = withIncomingEvent(instanceId, timestamp, measurements);
    EventRecord existingEventRecord = new EventRecord(existingEvent);
    existingEventRecord.prePersist();
    return existingEventRecord;
  }

  private EventRecord withExistingEvent(
      String instanceId, OffsetDateTime timestamp, String uom, Double value) {
    return withExistingEvent(
        instanceId, timestamp, List.of(new Measurement().withMetricId(uom).withValue(value)));
  }

  private Event withIncomingEvent(
      String instanceId, OffsetDateTime timestamp, String uom, Double value) {
    return withIncomingEvent(
        instanceId, timestamp, List.of(new Measurement().withMetricId(uom).withValue(value)));
  }

  private Event withIncomingEvent(
      String instanceId, OffsetDateTime timestamp, List<Measurement> measurements) {
    Event existingEvent = createEvent(instanceId, timestamp);
    existingEvent.setMeasurements(measurements);
    return existingEvent;
  }

  private void assertMetricIdValue(Event event, String metricId, Double value) {
    List<Measurement> matching =
        event.getMeasurements().stream().filter(m -> metricId.equals(m.getMetricId())).toList();
    Measurement measurement = matching.get(0);
    assertEquals(metricId, measurement.getMetricId());
    assertEquals(value, measurement.getValue());
  }

  private void assertResolvedEvent(Event event, String instanceId, String metricId, Double value) {
    assertEquals(instanceId, event.getInstanceId());
    assertNull(event.getAmendmentType());
    assertMetricIdValue(event, metricId, value);
  }

  private void assertDeductionEvent(
      Event deductionEvent, String instanceId, String metricId, Double value) {
    assertEquals(instanceId, deductionEvent.getInstanceId());
    assertEquals(AmendmentType.DEDUCTION, deductionEvent.getAmendmentType());
    assertMetricIdValue(deductionEvent, metricId, value);
  }
}
