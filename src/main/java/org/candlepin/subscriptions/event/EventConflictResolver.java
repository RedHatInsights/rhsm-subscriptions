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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
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
 *  - An incoming event may be broken down into 'derived' events in the case that a tag
 *    from an incoming event does not yet have measurements.
 *  - e.g. An incoming event has a measurement of 6 cores for tags T1 and T2. An existing
 *         EventRecord, matching the incoming EventKey, has a measurement of 6 cores for
 *         tag T1. Only a single EventRecord (AmendmentType.DERIVED) would be created for
 *         tag T2 with a cores measurement value of 6. In this case, the existing event
 *         already covers the 6 cores for tag T1, so we only need to include the incoming
 *         value for T2.
 * </pre>
 */
@Slf4j
@Component
public class EventConflictResolver {

  private final EventRecordRepository eventRecordRepository;
  private final ResolvedEventMapper resolvedEventMapper;
  private final ApplicationClock clock;

  @Autowired
  public EventConflictResolver(
      EventRecordRepository eventRecordRepository,
      ResolvedEventMapper resolvedEventMapper,
      ApplicationClock clock) {
    this.eventRecordRepository = eventRecordRepository;
    this.resolvedEventMapper = resolvedEventMapper;
    this.clock = clock;
  }

  public List<EventRecord> resolveIncomingEvents(List<Event> incomingEvents) {
    log.info("Resolving existing events for incoming batch.");
    Map<EventKey, List<Event>> eventsToResolve =
        incomingEvents.stream()
            .collect(
                Collectors.groupingBy(
                    EventKey::fromEvent, LinkedHashMap::new, Collectors.toList()));
    Map<EventKey, List<EventRecord>> allConflicting =
        getConflictingEvents(eventsToResolve.keySet());

    // Resolve any conflicting events.
    List<EventRecord> resolvedEvents = new ArrayList<>();
    eventsToResolve.forEach(
        (key, eventList) -> {
          // TODO Can we fix up the stream going into the conflict tracker? Maby just pass the
          // records?
          UsageConflictTracker tracker =
              new UsageConflictTracker(
                  allConflicting.getOrDefault(key, List.of()).stream()
                      .map(EventRecord::getEvent)
                      .toList());
          eventList.forEach(
              event -> {
                Set<UsageConflictKey> conflictKeys = UsageConflictKey.from(event);
                conflictKeys.forEach(
                    conflictKey -> {
                      if (tracker.contains(conflictKey)) {
                        Event conflictingEvent = tracker.getLatest(conflictKey);
                        Measurement conflictingMeasurement =
                            conflictingEvent.getMeasurements().stream()
                                .filter(m -> m.getMetricId().equals(conflictKey.getMetricId()))
                                .findFirst()
                                .orElseThrow(
                                    () ->
                                        new RuntimeException(
                                            "Expected conflicting measurement for " + conflictKey));

                        Event deductionEvent = createRecordFrom(conflictingEvent);
                        deductionEvent.setProductTag(Set.of(conflictKey.getProductTag()));
                        deductionEvent.setAmendmentType(AmendmentType.DEDUCTION);
                        Measurement measurement =
                            new Measurement()
                                .withMetricId(conflictingMeasurement.getMetricId())
                                .withUom(conflictingMeasurement.getUom())
                                .withValue(conflictingMeasurement.getValue() * -1);
                        deductionEvent.setMeasurements(List.of(measurement));

                        // TODO: Refactor to method
                        EventRecord deductionRecord = new EventRecord(deductionEvent);
                        allConflicting.putIfAbsent(key, new ArrayList<>());
                        allConflicting.get(key).add(deductionRecord);
                        resolvedEvents.add(deductionRecord);
                      }
                    });

                EventRecord newUsageEvent = new EventRecord(event);
                allConflicting.putIfAbsent(key, new ArrayList<>());
                allConflicting.get(key).add(newUsageEvent);
                resolvedEvents.add(newUsageEvent);
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
   * Updates the incoming event so that it contains the appropriate tags/measurements based on all
   * tags that have uncovered measurements.
   *
   * @param incoming the incoming Event
   * @param tagsByUncoveredMeasurements all tags by measurements that have uncovered usage.
   * @return a normalizes List of Event records.
   */
  private List<EventRecord> normalizeIncoming(
      Event incoming, Map<String, Set<String>> tagsByUncoveredMeasurements) {
    // If the event's measurements are already fully covered, there's nothing to normalize.
    if (tagsByUncoveredMeasurements.isEmpty()) {
      return List.of();
    }

    Map<String, List<Measurement>> incomingMeasurements =
        incoming.getMeasurements().stream()
            .collect(Collectors.groupingBy(UsageConflictKey::getMetricId));
    List<Measurement> measurementsMatchingIncomingTags = new ArrayList<>();
    List<Measurement> measurementsNotMatchingIncomingTags = new ArrayList<>();
    for (Entry<String, Set<String>> entry : tagsByUncoveredMeasurements.entrySet()) {
      if (entry.getValue().equals(incoming.getProductTag())
          && incomingMeasurements.containsKey(entry.getKey())) {
        measurementsMatchingIncomingTags.addAll(incomingMeasurements.get(entry.getKey()));
      } else {
        measurementsNotMatchingIncomingTags.addAll(incomingMeasurements.get(entry.getKey()));
      }
    }

    List<EventRecord> normalizedEvents = new ArrayList<>();
    // Update the incoming event to include only the measurements that match the
    // tags of the incoming event. If there are none, then do not include the incoming
    // event at all. Measurements will be covered by derived events.
    if (!measurementsMatchingIncomingTags.isEmpty()) {
      AmendmentType type =
          incoming.getMeasurements().size() == measurementsMatchingIncomingTags.size()
              ? null
              : AmendmentType.DERIVED;
      incoming.setAmendmentType(type);
      incoming.setMeasurements(measurementsMatchingIncomingTags);
      normalizedEvents.add(new EventRecord(incoming));
    }

    // Create derived events for each measurement that is not already covered,
    // and did not match the tags of the incoming event.
    for (Measurement measurement : measurementsNotMatchingIncomingTags) {
      Event derived = createRecordFrom(incoming);
      derived.setAmendmentType(AmendmentType.DERIVED);
      derived.setProductTag(tagsByUncoveredMeasurements.get(measurement.getMetricId()));
      derived.setMeasurements(List.of(measurement));
      normalizedEvents.add(new EventRecord(derived));
    }

    return normalizedEvents;
  }

  private Map<String, Double> getMeasurementTotals(String tag, Stream<Event> incomingEventStream) {
    return getMeasurementTotals(
        incomingEventStream.filter(toFilter -> toFilter.getProductTag().contains(tag)));
  }

  private Map<String, Double> getMeasurementTotals(Stream<Event> incomingEventStream) {
    Map<String, Double> totals = new HashMap<>();
    incomingEventStream.forEach(
        event ->
            event
                .getMeasurements()
                .forEach(
                    m -> {
                      double currentTotal =
                          totals.getOrDefault(UsageConflictKey.getMetricId(m), 0.0);
                      totals.put(UsageConflictKey.getMetricId(m), currentTotal + m.getValue());
                    }));
    return totals;
  }

  private Set<String> getUncoveredMeasurements(
      Map<String, Double> incoming, Map<String, Double> existing) {
    Set<String> uncovered = new HashSet<>();
    for (Map.Entry<String, Double> entry : incoming.entrySet()) {
      if (!existing.containsKey(entry.getKey())
          || !existing.get(entry.getKey()).equals(entry.getValue())) {
        uncovered.add(entry.getKey());
      }
    }
    return uncovered;
  }

  private List<EventRecord> createAmendments(
      Event incomingEvent,
      String applicableTag,
      Set<String> uncoveredMeasurements,
      Map<String, Double> existingMeasurementTotals) {
    List<EventRecord> amendments = new ArrayList<>();
    incomingEvent
        .getMeasurements()
        .forEach(
            incomingMeasurement -> {
              String metricId = UsageConflictKey.getMetricId(incomingMeasurement);
              if (uncoveredMeasurements.contains(metricId)
                  && existingMeasurementTotals.containsKey(metricId)) {
                // Only need to create a deduction amendment for measurements that are not
                // already covered and that already exist.
                Event deductionEvent = createRecordFrom(incomingEvent);
                deductionEvent.setProductTag(Set.of(applicableTag));
                deductionEvent.setAmendmentType(AmendmentType.DEDUCTION);
                Measurement measurement =
                    new Measurement()
                        .withMetricId(metricId)
                        .withUom(metricId)
                        .withValue(existingMeasurementTotals.get(metricId) * -1);
                deductionEvent.setMeasurements(List.of(measurement));
                amendments.add(new EventRecord(deductionEvent));
              }
            });
    return amendments;
  }

  private Map<EventKey, List<EventRecord>> getConflictingEvents(Set<EventKey> eventKeys) {
    return eventRecordRepository.findConflictingEvents(eventKeys).stream()
        .collect(
            Collectors.groupingBy(
                e -> EventKey.fromEvent(e.getEvent()), LinkedHashMap::new, Collectors.toList()));
  }

  private Event createRecordFrom(Event from) {
    Event target = new Event();
    resolvedEventMapper.update(target, from);
    return target;
  }
}
