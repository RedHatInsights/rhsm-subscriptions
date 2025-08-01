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
  final EventNormalizer normalizer;

  @Autowired
  public EventConflictResolver(
      EventRecordRepository eventRecordRepository, ResolvedEventMapper resolvedEventMapper) {
    this.eventRecordRepository = eventRecordRepository;
    this.resolvedEventMapper = resolvedEventMapper;
    this.normalizer = new EventNormalizer(resolvedEventMapper);
  }

  public List<EventRecord> resolveIncomingEvents(List<Event> incomingEvents) {
    log.info("Resolving existing events for incoming batch.");

    // Step 1: Flatten and group incoming events by EventKey
    Map<EventKey, List<Event>> eventsToResolve = groupEventsByEventKey(incomingEvents);

    // Step 2: Get all existing conflicting events from database
    Map<EventKey, List<Event>> allConflicting = getConflictingEvents(eventsToResolve.keySet());

    List<EventRecord> resolvedEvents = new ArrayList<>();

    // Step 3: Process each EventKey group
    eventsToResolve.forEach(
        (key, incomingEventList) -> {
          // Step 3a: Create tracker with existing database events
          List<Event> existingEvents = allConflicting.getOrDefault(key, List.of());
          UsageConflictTracker tracker = new UsageConflictTracker(existingEvents);

          // Step 3b: Perform intra-batch deduplication for efficiency
          Map<UsageConflictKey, Event> deduplicatedEvents =
              performIntraBatchDeduplication(incomingEventList, tracker);

          // Step 3c: Process each deduplicated event for cross-batch conflicts
          deduplicatedEvents
              .values()
              .forEach(
                  event -> {
                    List<Event> newEvents = resolveEventConflicts(event, tracker);
                    resolvedEvents.addAll(newEvents.stream().map(EventRecord::new).toList());

                    // Track this event for subsequent conflicts within this batch
                    tracker.track(event);
                  });
        });

    log.info(
        "Finishing resolving events for incoming batch. In={} Resolved={}",
        incomingEvents.size(),
        resolvedEvents.size());
    return resolvedEvents;
  }

  /**
   * Groups incoming events by EventKey for conflict resolution processing. This method flattens
   * events (splits multi-tag events into single-tag events) and groups them.
   *
   * @param incomingEvents The list of incoming events to group
   * @return Map of EventKey to list of flattened events
   */
  public Map<EventKey, List<Event>> groupEventsByEventKey(List<Event> incomingEvents) {
    return incomingEvents.stream()
        .flatMap(event -> normalizer.flattenEventUsage(event).stream())
        .collect(
            Collectors.groupingBy(EventKey::fromEvent, LinkedHashMap::new, Collectors.toList()));
  }

  /**
   * Performs intra-batch deduplication by keeping only the latest event per UsageConflictKey. This
   * is used for efficiency in production to avoid processing multiple events with the same conflict
   * key within the same batch.
   *
   * @param events The list of events to deduplicate
   * @param tracker The tracker to use for generating conflict keys
   * @return Map of UsageConflictKey to the latest event for that key
   */
  public Map<UsageConflictKey, Event> performIntraBatchDeduplication(
      List<Event> events, UsageConflictTracker tracker) {
    Map<UsageConflictKey, Event> deduplicatedEvents = new LinkedHashMap<>();
    events.forEach(
        event -> {
          UsageConflictKey conflictKey = tracker.getConflictKeyForEvent(event);
          deduplicatedEvents.put(conflictKey, event);
        });
    return deduplicatedEvents;
  }

  /**
   * Processes events sequentially without deduplication, creating amendments for each conflict.
   * This provides detailed step-by-step conflict resolution useful for testing and troubleshooting.
   *
   * @param events The list of events to process sequentially
   * @param tracker The tracker to use for conflict detection and resolution
   * @return List of all resolved events including intermediate amendments
   */
  public List<Event> processEventsSequentially(List<Event> events, UsageConflictTracker tracker) {
    List<Event> resolvedEvents = new ArrayList<>();

    events.forEach(
        event -> {
          List<Event> newEvents = resolveEventConflicts(event, tracker);
          resolvedEvents.addAll(newEvents);

          // Track this event for subsequent conflicts within this batch
          tracker.track(event);
        });

    return resolvedEvents;
  }

  /**
   * Resolves conflicts for a single event using event sourcing principles. Returns a list of events
   * to persist (deduction events + the final event).
   */
  private List<Event> resolveEventConflicts(Event incomingEvent, UsageConflictTracker tracker) {
    List<Event> eventsToSave = new ArrayList<>();
    UsageConflictKey conflictKey = tracker.getConflictKeyForEvent(incomingEvent);

    // If there's a conflict, check if amendment is needed
    if (tracker.contains(conflictKey)) {
      Event conflictingEvent = tracker.getLatest(conflictKey);

      // Pure idempotency check: if events are identical, ignore completely
      if (!amendmentRequired(incomingEvent, conflictingEvent, conflictKey.getMetricId())) {
        log.debug("Incoming event is identical to existing event, ignoring: {}", incomingEvent);
        return eventsToSave; // Return empty list - no events to save
      }

      // Events differ, create deduction event for conflict resolution
      Measurement conflictingMeasurement =
          findMeasurement(conflictingEvent, conflictKey.getMetricId());
      Event deductionEvent = createRecordFrom(conflictingEvent);
      deductionEvent.setProductTag(Set.of(conflictKey.getProductTag()));
      deductionEvent.setAmendmentType(AmendmentType.DEDUCTION);
      Measurement deductionMeasurement =
          new Measurement()
              .withMetricId(conflictingMeasurement.getMetricId())
              .withValue(conflictingMeasurement.getValue() * -1);
      deductionEvent.setMeasurements(List.of(deductionMeasurement));

      eventsToSave.add(deductionEvent);

      log.debug(
          "Created deduction event for conflict key {}: value={}",
          conflictKey,
          conflictingMeasurement.getValue());
    }

    // Add the incoming event (unless it was identical and ignored above)
    eventsToSave.add(incomingEvent);

    return eventsToSave;
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

  private boolean amendmentRequired(Event incomingEvent, Event resolvedEvent, String metricId) {
    UsageDescriptor incomingEventDescriptor = new UsageDescriptor(incomingEvent);
    UsageDescriptor resolvedEventDescriptor = new UsageDescriptor(resolvedEvent);

    Measurement incomingMeasurement = findMeasurement(incomingEvent, metricId);
    Measurement conflictingMeasurement = findMeasurement(resolvedEvent, metricId);
    boolean measurementEqual =
        Objects.equals(incomingMeasurement.getValue(), conflictingMeasurement.getValue());

    return !measurementEqual || !incomingEventDescriptor.equals(resolvedEventDescriptor);
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
