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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.AmendmentType;
import org.candlepin.subscriptions.json.Measurement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Responsible for resolving conflicting {@link org.candlepin.subscriptions.db.model.EventRecord}s
 * for incoming {@link org.candlepin.subscriptions.json.Event} messages and resolving them. A
 * conflict occurs when an incoming event shares the same lookup hash as existing event records in
 * the DB. Since event records can share the same timestamp, all existing records will be included
 * during resolution.
 *
 * <pre>
 * Example Conflict/Resolutions:
 *
 * No Conflicts
 *  - Incoming event is valid and no resolution required.
 * Conflicting event with equal measurements
 *  - Incoming Event is ignored.
 * Conflicting event with different measurements
 *  - EventRecord added with a negative accumulative measurement value
 *  - e.g. An incoming event has a measurement of 10 cores. An existing EventRecord matching
 *         the lookup hash has a measurement of 20 cores. An EventRecord would be created with
 *         a cores value of -10 and another EventRecord created to apply the new value of 20
 *         cores.
 * </pre>
 */
@Slf4j
@Component
public class EventConflictResolver {

  private final EventRecordRepository eventRecordRepository;
  private final EventMapper resolvedEventMapper;

  @Autowired
  public EventConflictResolver(
      EventRecordRepository eventRecordRepository, EventMapper resolvedEventMapper) {
    this.eventRecordRepository = eventRecordRepository;
    this.resolvedEventMapper = resolvedEventMapper;
  }

  public List<EventRecord> resolveIncomingEvents(Map<EventKey, Event> eventsToResolve) {
    log.info("Resolving existing events for incoming batch.");
    Map<EventKey, List<EventRecord>> allConflicting = getConflictingEvents(eventsToResolve);
    // Nothing to resolve
    if (allConflicting.isEmpty()) {
      log.info("No conflicting incoming events in batch. Nothing to resolve.");
      return eventsToResolve.values().stream().map(EventRecord::new).toList();
    }

    // Resolve any conflicting events.
    List<EventRecord> resolvedEvents = new LinkedList<>();
    eventsToResolve.forEach(
        (key, event) -> {
          if (allConflicting.containsKey(key)) {
            List<EventRecord> resolvedConflicts =
                resolveEventConflicts(event, allConflicting.get(key));
            // When there is a conflict with no resolution, the incoming event is a duplicate
            // and there is no need to add it as resolved.
            if (resolvedConflicts.isEmpty()) {
              return;
            }
            resolvedEvents.addAll(resolvedConflicts);
          }
          // Include the incoming event since this event will be the new value.
          resolvedEvents.add(new EventRecord(event));
        });

    log.info("Resolved {} events", resolvedEvents.size());

    return resolvedEvents;
  }

  private List<EventRecord> resolveEventConflicts(
      Event incomingEvent, List<EventRecord> conflictingEvents) {
    return resolveEventMeasurements(incomingEvent, conflictingEvents);
  }

  private Map<String, Double> determineMeasurementDeductions(List<EventRecord> conflictingEvents) {
    Map<String, Double> deductions = new HashMap<>();
    conflictingEvents.stream()
        .map(EventRecord::getEvent)
        .forEach(
            e ->
                e.getMeasurements()
                    .forEach(
                        m -> {
                          String metricId = getMetricId(m);
                          deductions.putIfAbsent(metricId, 0.0);
                          deductions.put(metricId, deductions.get(metricId) - m.getValue());
                        }));
    return deductions;
  }

  private List<EventRecord> resolveEventMeasurements(
      Event toResolve, List<EventRecord> conflictingEvents) {
    Map<String, Measurement> deductionEvents =
        getDeductionMeasurements(toResolve, determineMeasurementDeductions(conflictingEvents));

    // Create events for each measurement deduction.
    List<EventRecord> resolved = new ArrayList<>();
    deductionEvents.forEach(
        (metricId, measurement) -> {
          Event deductionEvent = createRecordFrom(toResolve);
          deductionEvent.setAmendmentType(AmendmentType.DEDUCTION);
          deductionEvent.setMeasurements(List.of(measurement));
          resolved.add(new EventRecord(deductionEvent));
        });
    return resolved;
  }

  private Map<String, Measurement> getDeductionMeasurements(
      final Event toResolve, Map<String, Double> measurementDeductions) {
    // Measurements need to be resolved individually since conflicting events
    // could potentially contain different measurement sets.
    Map<String, Measurement> deductions = new HashMap<>();
    for (Measurement measurement : toResolve.getMeasurements()) {
      String metricId = getMetricId(measurement);
      if (measurementDeductions.containsKey(metricId)) {
        Double deduction = measurementDeductions.get(metricId);
        // If we had a conflicting Event, but the measurement value was the same,
        // there's nothing to resolve as they are considered equal. This will
        // NOT result in a new EventRecord for the measurement.
        //
        // The deduction value is expected to be <= 0, so we compare
        // by adding the incoming value to it to see if the result is 0.
        if (deduction + measurement.getValue() == 0.0) {
          log.debug("Incoming event measurement is a duplicate. Nothing to resolve.");
          continue;
        }
        deductions.put(
            metricId,
            new Measurement().withMetricId(metricId).withUom(metricId).withValue(deduction));
      }
    }
    return deductions;
  }

  private String getMetricId(Measurement measurement) {
    return !StringUtils.isEmpty(measurement.getMetricId())
        ? measurement.getMetricId()
        : measurement.getUom();
  }

  private Map<EventKey, List<EventRecord>> getConflictingEvents(
      Map<EventKey, Event> incomingEvents) {
    return eventRecordRepository.findConflictingEvents(incomingEvents.keySet()).stream()
        .collect(Collectors.groupingBy(e -> EventKey.fromEvent(e.getEvent())));
  }

  private Event createRecordFrom(Event from) {
    Event target = new Event();
    resolvedEventMapper.toUnpersistedWithoutMeasurements(target, from);
    return target;
  }
}
