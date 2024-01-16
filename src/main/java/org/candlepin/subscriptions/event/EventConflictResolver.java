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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.json.Event;
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
  private final ResolvedEventMapper resolvedEventMapper;

  @Autowired
  public EventConflictResolver(
      EventRecordRepository eventRecordRepository, ResolvedEventMapper resolvedEventMapper) {
    this.eventRecordRepository = eventRecordRepository;
    this.resolvedEventMapper = resolvedEventMapper;
  }

  public List<EventRecord> resolveIncomingEvents(List<Event> eventsToResolve) {
    log.info("Resolving existing events for incoming batch.");
    Map<EventKey, List<EventRecord>> allConflicting = getConflictingEvents(eventsToResolve);
    // Nothing to resolve
    if (allConflicting.isEmpty()) {
      log.info("No conflicting incoming events in batch. Nothing to resolve.");
      return eventsToResolve.stream().map(EventRecord::new).toList();
    }

    List<EventRecord> resolvedEvents = new LinkedList<>();
    eventsToResolve.forEach(
        toResolve -> {
          EventKey key = EventKey.fromEvent(toResolve);
          if (!allConflicting.containsKey(key)) {
            // No conflict, include the incoming event.
            resolvedEvents.add(new EventRecord(toResolve));
          } else {
            resolvedEvents.addAll(resolveEventConflicts(toResolve, allConflicting.get(key)));
          }
        });
    return resolvedEvents;
  }

  private List<EventRecord> resolveEventConflicts(
      Event incomingEvent, List<EventRecord> conflictingEvents) {
    var deductedMeasurements = determineMeasurementDeductions(conflictingEvents);
    return resolveEventMeasurements(incomingEvent, deductedMeasurements);
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
                          deductions.putIfAbsent(m.getUom(), 0.0);
                          deductions.put(m.getUom(), deductions.get(m.getUom()) - m.getValue());
                        }));
    return deductions;
  }

  private List<EventRecord> resolveEventMeasurements(
      Event toResolve, Map<String, Double> measurementDeductions) {
    List<EventRecord> resolved = new ArrayList<>();

    // If there is nothing to deduct from the event to resolve,
    // simply return the event as is.
    if (measurementDeductions.isEmpty()) {
      log.debug("Nothing to resolve for event {}", toResolve);
      resolved.add(new EventRecord(toResolve));
      return resolved;
    }

    // Measurements need to be resolved individually since conflicting events
    // could potentially contain different uom measurement sets.
    Set<String> measurementUomsToSkip = new HashSet<>();
    for (Measurement measurement : toResolve.getMeasurements()) {
      Optional<EventRecord> deductionEvent =
          buildDeductionEvent(
              toResolve,
              measurement,
              Optional.ofNullable(measurementDeductions.get(measurement.getUom())));
      deductionEvent.ifPresentOrElse(
          resolved::add, () -> measurementUomsToSkip.add(measurement.getUom()));
    }

    rebuildEventWithUpdatedMeasurements(toResolve, measurementUomsToSkip).ifPresent(resolved::add);
    return resolved;
  }

  private Map<EventKey, List<EventRecord>> getConflictingEvents(List<Event> incomingEvents) {
    List<EventKey> eventKeys = incomingEvents.stream().map(EventKey::fromEvent).toList();
    return eventRecordRepository.findConflictingEvents(eventKeys).stream()
        .collect(Collectors.groupingBy(e -> EventKey.fromEvent(e.getEvent())));
  }

  private Optional<EventRecord> buildDeductionEvent(
      Event toResolve, Measurement measurement, Optional<Double> deduction) {
    if (deduction.isPresent()) {
      Double deductedValue = deduction.get();
      // If we had a conflicting Event, but the measurement value was the same
      // there's nothing to resolve as they are considered equal. This will
      // NOT result in a new EventRecord for the measurement.
      //
      // The deducted value is expected to be <= 0, so we compare
      // by adding the incoming value to it to see if the result is 0.
      if (deductedValue + measurement.getValue() == 0.0) {
        log.debug("Incoming event measurement is a duplicate. Nothing to resolve.");
        return Optional.empty();
      }

      // Create an EventRecord with the -ve value
      Event deductionEvent = createRecordFrom(toResolve);
      deductionEvent.setMeasurements(
          List.of(new Measurement().withUom(measurement.getUom()).withValue(deductedValue)));
      return Optional.of(new EventRecord(deductionEvent));
    }
    return Optional.empty();
  }

  private Optional<EventRecord> rebuildEventWithUpdatedMeasurements(
      Event toResolve, Set<String> measurementUomsToSkip) {
    // Rebuild the Event if there are event measurements to skip, otherwise, create an
    // EventRecord from the Event we attempted to resolve.
    EventRecord rebuiltEventRecord = null;
    if (!measurementUomsToSkip.isEmpty()) {
      // Remove any measurements that would result in no change, and create an
      // EventRecord based on the updated incoming event if any measurements need
      // to be applied.
      List<Measurement> applicableMeasurements =
          toResolve.getMeasurements().stream()
              .filter(m -> !measurementUomsToSkip.contains(m.getUom()))
              .toList();

      if (log.isDebugEnabled() && !applicableMeasurements.isEmpty()) {
        log.debug(
            "Skipping measurements in event since they would result in no change: {}",
            String.join(",", measurementUomsToSkip));
      }

      // If there are still applicable measurements, rebuild the event we are resolving to
      // include only the applicable measurements. Because the event measurement
      // collection is immutable, we need to copy the Event in order to make the updates.
      if (!applicableMeasurements.isEmpty()) {
        Event rebuiltEvent = createRecordFrom(toResolve);
        rebuiltEvent.setMeasurements(applicableMeasurements);
        rebuiltEventRecord = new EventRecord(rebuiltEvent);
      } else {
        log.debug("Did not find any applicable measurements while resolving the event.");
      }
    } else {
      rebuiltEventRecord = new EventRecord(toResolve);
    }
    return Optional.ofNullable(rebuiltEventRecord);
  }

  private Event createRecordFrom(Event from) {
    Event target = new Event();
    resolvedEventMapper.update(target, from);
    return target;
  }
}
