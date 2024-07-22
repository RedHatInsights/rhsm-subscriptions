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
import java.util.Set;
import java.util.stream.Collectors;
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
    Map<EventKey, List<Event>> allConflicting = getConflictingEvents(eventsToResolve.keySet());

    // Resolve any conflicting events.
    List<EventRecord> resolvedEvents = new ArrayList<>();
    eventsToResolve.forEach(
        (key, eventList) -> {
          UsageConflictTracker tracker =
              new UsageConflictTracker(allConflicting.getOrDefault(key, List.of()));
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

                        allConflicting.putIfAbsent(key, new ArrayList<>());
                        allConflicting.get(key).add(deductionEvent);
                        resolvedEvents.add(new EventRecord(deductionEvent));
                      }
                    });

                allConflicting.putIfAbsent(key, new ArrayList<>());
                allConflicting.get(key).add(event);
                resolvedEvents.add(new EventRecord(event));
                tracker.track(event);
              });
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
        .collect(
            Collectors.groupingBy(EventKey::fromEvent, LinkedHashMap::new, Collectors.toList()));
  }

  private Event createRecordFrom(Event from) {
    Event target = new Event();
    resolvedEventMapper.update(target, from);
    return target;
  }
}
