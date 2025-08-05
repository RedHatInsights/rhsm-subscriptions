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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 * conflict occurs when an incoming event shares the same {@link EventKey} as existing event records
 * in the DB. Since event records can share the same org_id, timestamp, and instance_id, all
 * existing matching records will be included during resolution.
 *
 * <p>Event conflict resolution is also done based on product tags since buckets are product_id
 * (tag) based and measurements apply to each bucket.
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
 *         the incoming event's Event Key has a measurement of 20 cores. An EventRecord would
 *         be created with a cores value of -10 and another EventRecord created to apply the
 *         new value of 20 cores.
 * Conflicting event with different product tags.
 *  - Amendments are created based on measurement coverage for each included tag.
 *  - e.g. An incoming event has a measurement of 6 cores for tags T1 and T2. An existing
 *         EventRecord, matching the incoming EventKey, has a measurement of 6 cores for
 *         tag T1. In this case, since the existing event already has a cores measurement
 *         for T1, a DEDUCTION event (cores: -6) will be created for T1's cores value, and
 *         the incoming Event will be persisted as is. This ensure that both T1 and T2's
 *         cores values will remain at 6.
 * </pre>
 */
@Slf4j
@Component
public class EventConflictResolver {

  private final EventRecordRepository eventRecordRepository;
  private final ResolvedEventMapper resolvedEventMapper;
  private final EventNormalizer normalizer;

  @Autowired
  public EventConflictResolver(
      EventRecordRepository eventRecordRepository, ResolvedEventMapper resolvedEventMapper) {
    this.eventRecordRepository = eventRecordRepository;
    this.resolvedEventMapper = resolvedEventMapper;
    this.normalizer = new EventNormalizer(resolvedEventMapper);
  }

  public List<EventRecord> resolveIncomingEvents(List<Event> incomingEvents) {
    log.info("Resolving existing events for incoming batch.");
    Map<EventKey, List<Event>> eventsToResolve =
        incomingEvents.stream()
            .flatMap(event -> normalizer.flattenEventUsage(event).stream())
            .collect(
                Collectors.groupingBy(
                    EventKey::fromEvent, LinkedHashMap::new, Collectors.toList()));
    Map<EventKey, List<Event>> allConflicting = getConflictingEvents(eventsToResolve.keySet());

    // Resolve any conflicting events.
    List<EventRecord> resolvedEvents = new ArrayList<>();
    eventsToResolve.forEach(
        (key, eventList) -> {
          UsageConflictTracker tracker =
              new UsageConflictTracker(allConflicting.getOrDefault(key, List.of()));

          // Step 1: Intra-batch deduplication - group events by conflict key and keep latest
          List<Event> deduplicatedEvents = deduplicateIntraBatchEvents(eventList);
          log.info(
              "Deduplicated {} events to {} for EventKey: {}",
              eventList.size(),
              deduplicatedEvents.size(),
              key);

          // Step 2: Resolve intra-batch conflicts and process against database conflicts
          resolveIntraBatchConflicts(deduplicatedEvents, tracker, allConflicting, resolvedEvents);
        });
    log.info(
        "Finishing resolving events for incoming batch. In={} Resolved={}",
        incomingEvents.size(),
        resolvedEvents.size());
    return resolvedEvents;
  }

  private Map<EventKey, List<Event>> getConflictingEvents(Set<EventKey> eventKeys) {
    return eventRecordRepository.findConflictingEvents(eventKeys).stream()
        .map(EventRecord::getEvent)
        .flatMap(event -> normalizer.flattenEventUsage(event).stream())
        .collect(
            Collectors.groupingBy(EventKey::fromEvent, LinkedHashMap::new, Collectors.toList()));
  }

  private Event createRecordFrom(Event from) {
    Event target = new Event();
    resolvedEventMapper.update(target, from);
    return target;
  }

  /**
   * Performs intra-batch deduplication by removing exact duplicates (same conflict key, same
   * descriptor, same measurement value) while preserving events that differ by measurement value
   * so that proper deductions can be generated later in the pipeline.
   *
   * @param eventList List of events in the current batch
   * @return List of deduplicated events with exact duplicates removed
   */
  private List<Event> deduplicateIntraBatchEvents(List<Event> eventList) {
    // We only eliminate EXACT duplicates (same conflict key, same descriptor, same measurement
    // value) but preserve events that differ by measurement value so that proper deductions can
    // be generated later in the pipeline.

    Set<String> seen = new java.util.LinkedHashSet<>();
    List<Event> uniqueEvents = new java.util.ArrayList<>();

    UsageConflictTracker tempTracker = new UsageConflictTracker(List.of());

    for (Event event : eventList) {
      UsageConflictKey conflictKey = tempTracker.getConflictKeyForEvent(event);
      UsageDescriptor descriptor = new UsageDescriptor(event);
      Measurement m =
          event.getMeasurements().get(0); // flattened events contain a single measurement
      String dedupKey = String.format("%s|%s|%s", conflictKey, descriptor.hashCode(), m.getValue());

      if (seen.add(dedupKey)) {
        uniqueEvents.add(event);
      } else {
        log.debug("Intra-batch deduplication: dropped exact duplicate event for key {}", dedupKey);
      }
    }

    return uniqueEvents;
  }

  /**
   * Resolves intra-batch conflicts by grouping events by conflict key and processing them together.
   * For events with the same conflict key, only the final result is produced.
   *
   * @param events List of events to resolve
   * @param tracker Conflict tracker for database conflicts
   * @param allConflicting Map to track all conflicting events
   * @param resolvedEvents List to collect resolved events
   */
  private void resolveIntraBatchConflicts(
      List<Event> events,
      UsageConflictTracker tracker,
      Map<EventKey, List<Event>> allConflicting,
      List<EventRecord> resolvedEvents) {

    // Group events by conflict key to handle intra-batch conflicts
    Map<UsageConflictKey, List<Event>> eventsByConflictKey = new LinkedHashMap<>();
    UsageConflictTracker tempTracker = new UsageConflictTracker(List.of());

    for (Event event : events) {
      UsageConflictKey conflictKey = tempTracker.getConflictKeyForEvent(event);
      eventsByConflictKey.computeIfAbsent(conflictKey, k -> new ArrayList<>()).add(event);
    }

    // Process each conflict key group
    eventsByConflictKey.forEach(
        (conflictKey, eventList) -> {
          if (eventList.size() == 1) {
            // Single event - process normally against database
            Event event = eventList.get(0);
            processEventAgainstDatabase(event, conflictKey, tracker, allConflicting, resolvedEvents);
          } else {
            // Multiple events with same conflict key - resolve to final result
            Event finalEvent = resolveToFinalEvent(eventList);
            processEventAgainstDatabase(finalEvent, conflictKey, tracker, allConflicting, resolvedEvents);
          }
        });
  }

  /**
   * Resolves multiple events with the same conflict key to produce only the final result.
   * The final event is determined by the latest recordDate, or the highest measurement value
   * if record dates are the same.
   *
   * @param events List of events with the same conflict key
   * @return The final event to be processed
   */
  private Event resolveToFinalEvent(List<Event> events) {
    Event finalEvent = events.get(0);
    
    // First, try to find the event with the latest recordDate
    for (Event event : events) {
      if (isNewerByRecordDate(event, finalEvent)) {
        finalEvent = event;
      }
    }
    
    // If all events have the same recordDate (or all are null), select the one with highest value
    final Event selectedEvent = finalEvent;
    boolean allSameRecordDate = events.stream().allMatch(e -> 
        (e.getRecordDate() == null && selectedEvent.getRecordDate() == null) ||
        (e.getRecordDate() != null && selectedEvent.getRecordDate() != null && 
         e.getRecordDate().equals(selectedEvent.getRecordDate())));
    
    if (events.size() > 1 && allSameRecordDate) {
      // Find the event with the highest measurement value
      Event highestValueEvent = events.get(0);
      for (Event event : events) {
        double currentValue = event.getMeasurements().get(0).getValue();
        double highestValue = highestValueEvent.getMeasurements().get(0).getValue();
        if (currentValue > highestValue) {
          highestValueEvent = event;
        }
      }
      finalEvent = highestValueEvent;
    }
    
    log.debug(
        "Resolved {} events with same conflict key to final event with value={}",
        events.size(),
        finalEvent.getMeasurements().get(0).getValue());
    
    return finalEvent;
  }

  /**
   * Processes a single event against database conflicts.
   *
   * @param event Event to process
   * @param conflictKey Conflict key for the event
   * @param tracker Conflict tracker
   * @param allConflicting Map to track all conflicting events
   * @param resolvedEvents List to collect resolved events
   */
  private void processEventAgainstDatabase(
      Event event,
      UsageConflictKey conflictKey,
      UsageConflictTracker tracker,
      Map<EventKey, List<Event>> allConflicting,
      List<EventRecord> resolvedEvents) {

    List<Event> newlyResolvedEvents = new ArrayList<>();

    // Determine the type of event conflict (only against database events, not intra-batch)
    EventConflictType conflictType = determineConflictType(event, conflictKey, tracker);
    log.debug("Processing {} event for conflict key: {}", conflictType, conflictKey);

    // Handle based on conflict type
    if (conflictType.requiresDeduction()) {
      Event conflictingEvent = tracker.getLatest(conflictKey);
      Measurement conflictingMeasurement =
          findMeasurement(conflictingEvent, conflictKey.getMetricId());

      Event deductionEvent = createRecordFrom(conflictingEvent);
      deductionEvent.setProductTag(Set.of(conflictKey.getProductTag()));
      deductionEvent.setAmendmentType(AmendmentType.DEDUCTION);
      Measurement measurement =
          new Measurement()
              .withMetricId(conflictingMeasurement.getMetricId())
              .withValue(conflictingMeasurement.getValue() * -1);
      deductionEvent.setMeasurements(List.of(measurement));
      newlyResolvedEvents.add(deductionEvent);

      log.debug(
          "Created deduction event for {} conflict key {}: value={}",
          conflictType,
          conflictKey,
          conflictingMeasurement.getValue());
    }

    // Add the incoming event if it should be saved
    if (conflictType.saveIncomingEvent()) {
      newlyResolvedEvents.add(event);
    }

    // Add to tracking collections
    EventKey eventKey = EventKey.fromEvent(event);
    allConflicting.putIfAbsent(eventKey, new ArrayList<>());
    allConflicting.get(eventKey).addAll(newlyResolvedEvents);
    resolvedEvents.addAll(newlyResolvedEvents.stream().map(EventRecord::new).toList());
    tracker.track(event);
  }

  /**
   * Returns true if {@code event} has a later (newer) recordDate than {@code existingEvent}. Null
   * recordDates are treated as less than any non-null value; if both are null, returns false.
   *
   * @param event the event being evaluated
   * @param existingEvent the existing event to compare against
   * @return true if event is newer by recordDate, false otherwise
   */
  private boolean isNewerByRecordDate(Event event, Event existingEvent) {
    OffsetDateTime a = event.getRecordDate();
    OffsetDateTime b = existingEvent.getRecordDate();
    if (a == null) {
      return false;
    }
    if (b == null) {
      return true;
    }
    return a.isAfter(b);
  }

  /**
   * Determines the type of conflict for an incoming event.
   *
   * @param incomingEvent The event being processed
   * @param conflictKey The usage conflict key for the event
   * @param tracker The conflict tracker containing existing events
   * @return The type of conflict encountered
   */
  private EventConflictType determineConflictType(
      Event incomingEvent, UsageConflictKey conflictKey, UsageConflictTracker tracker) {
    // If no existing event with this conflict key, it's original
    if (!tracker.contains(conflictKey)) {
      return EventConflictType.ORIGINAL;
    }

    Event existingEvent = tracker.getLatest(conflictKey);
    String metricId = conflictKey.getMetricId();

    // Compare measurements
    Measurement incomingMeasurement = findMeasurement(incomingEvent, metricId);
    Measurement existingMeasurement = findMeasurement(existingEvent, metricId);
    boolean measurementEqual =
        Objects.equals(incomingMeasurement.getValue(), existingMeasurement.getValue());

    // Compare usage descriptors
    UsageDescriptor incomingDescriptor = new UsageDescriptor(incomingEvent);
    UsageDescriptor existingDescriptor = new UsageDescriptor(existingEvent);
    boolean descriptorEqual = incomingDescriptor.equals(existingDescriptor);

    // Determine conflict type based on what differs
    if (measurementEqual && descriptorEqual) {
      return EventConflictType.IDENTICAL;
    } else if (measurementEqual && !descriptorEqual) {
      return EventConflictType.CONTEXTUAL;
    } else if (!measurementEqual && descriptorEqual) {
      return EventConflictType.CORRECTIVE;
    } else {
      return EventConflictType.COMPREHENSIVE;
    }
  }

  private Measurement findMeasurement(Event event, String metricId) {
    return event.getMeasurements().stream()
        .filter(m -> StringUtils.equals(m.getMetricId(), metricId))
        .findFirst()
        .orElseThrow(
            () ->
                new RuntimeException(
                    String.format(
                        "Could not find measurement metricId=%s for event=%s", metricId, event)));
  }
}
