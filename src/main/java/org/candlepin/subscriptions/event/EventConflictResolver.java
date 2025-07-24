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
  private final EventNormalizer normalizer;

  @Autowired
  public EventConflictResolver(
      EventRecordRepository eventRecordRepository, ResolvedEventMapper resolvedEventMapper) {
    this.eventRecordRepository = eventRecordRepository;
    this.resolvedEventMapper = resolvedEventMapper;
    this.normalizer = new EventNormalizer(resolvedEventMapper);
  }

  public List<EventRecord> resolveIncomingEvents(List<Event> incomingEvents) {
    log.info("Resolving existing events for incoming batch of {} events.", incomingEvents.size());
    Map<EventKey, List<Event>> eventsToResolve =
        incomingEvents.stream()
            .flatMap(event -> normalizer.flattenEventUsage(event).stream())
            .collect(
                Collectors.groupingBy(
                    EventKey::fromEvent, LinkedHashMap::new, Collectors.toList()));
    Map<EventKey, List<Event>> allConflicting = getConflictingEvents(eventsToResolve.keySet());

    log.debug(
        "Found {} event keys to resolve, {} have existing conflicts",
        eventsToResolve.size(),
        allConflicting.size());

    // Resolve any conflicting events.
    List<EventRecord> resolvedEvents = new ArrayList<>();
    eventsToResolve.forEach(
        (key, eventList) -> {
          UsageConflictTracker tracker =
              new UsageConflictTracker(allConflicting.getOrDefault(key, List.of()));
          eventList.forEach(
              event -> {
                // Each event should only have a single conflict key since events
                // were normalized by (tag,measurement).
                UsageConflictKey conflictKey = tracker.getConflictKeyForEvent(event);
                List<Event> newlyResolvedEvents = new ArrayList<>();
                if (tracker.contains(conflictKey)) {
                  Event conflictingEvent = tracker.getLatest(conflictKey);
                  Measurement conflictingMeasurement =
                      findMeasurement(conflictingEvent, conflictKey.getMetricId());

                  if (!amendmentRequired(event, conflictingEvent, conflictKey.getMetricId())) {
                    log.debug(
                        "Amendment not required for event {} with conflict key {}: {}",
                        event.getInstanceId(),
                        conflictKey.getMetricId(),
                        event);
                    // Still add the event, just don't create a deduction
                    newlyResolvedEvents.add(event);
                    allConflicting.putIfAbsent(key, new ArrayList<>());
                    allConflicting.get(key).addAll(newlyResolvedEvents);
                    resolvedEvents.addAll(
                        newlyResolvedEvents.stream().map(EventRecord::new).toList());
                    tracker.track(event);
                    return;
                  }

                  log.debug(
                      "Creating deduction for event {} with conflict key {}: {}",
                      event.getInstanceId(),
                      conflictKey.getMetricId(),
                      event);

                  Event deductionEvent = createRecordFrom(conflictingEvent);
                  deductionEvent.setProductTag(Set.of(conflictKey.getProductTag()));
                  deductionEvent.setAmendmentType(AmendmentType.DEDUCTION);
                  Measurement measurement =
                      new Measurement()
                          .withMetricId(conflictingMeasurement.getMetricId())
                          .withValue(conflictingMeasurement.getValue() * -1);
                  deductionEvent.setMeasurements(List.of(measurement));
                  newlyResolvedEvents.add(deductionEvent);
                } else {
                  log.debug(
                      "No conflicts found for event {} with conflict key {}",
                      event.getInstanceId(),
                      conflictKey.getMetricId());
                }

                newlyResolvedEvents.add(event);
                allConflicting.putIfAbsent(key, new ArrayList<>());
                allConflicting.get(key).addAll(newlyResolvedEvents);
                resolvedEvents.addAll(newlyResolvedEvents.stream().map(EventRecord::new).toList());
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

    // If measurements are equal and descriptors are equal, no amendment needed
    if (measurementEqual && incomingEventDescriptor.equals(resolvedEventDescriptor)) {
      return false;
    }

    // Check if this is likely a reprocessed event (same event being processed multiple times)
    // This prevents the "stuck" event cascade issue
    if (isLikelyReprocessedEvent(incomingEvent, resolvedEvent)) {
      log.debug(
          "Detected likely reprocessed event, skipping amendment to prevent deduction cascade. "
              + "Incoming: {}, Resolved: {}",
          incomingEvent.getInstanceId(),
          resolvedEvent.getInstanceId());
      return false;
    }

    // Check if the measurement difference is significant enough to warrant a deduction
    // This prevents inappropriate deductions for minor measurement differences
    if (measurementEqual && !incomingEventDescriptor.equals(resolvedEventDescriptor)) {
      // Only different descriptors, but same measurements - this might be a legitimate update
      log.debug(
          "Same measurements but different descriptors, allowing amendment for legitimate updates");
      return true;
    }

    if (!measurementEqual) {
      double difference =
          Math.abs(incomingMeasurement.getValue() - conflictingMeasurement.getValue());
      double relativeDifference =
          difference
              / Math.max(
                  Math.abs(incomingMeasurement.getValue()),
                  Math.abs(conflictingMeasurement.getValue()));

      // If the relative difference is very small (< 1%), it might be a minor fluctuation
      // that shouldn't trigger a deduction
      if (relativeDifference < 0.01) {
        log.debug(
            "Measurement difference is very small ({}%), skipping amendment to prevent "
                + "inappropriate deductions. Incoming: {}, Resolved: {}",
            relativeDifference * 100,
            incomingMeasurement.getValue(),
            conflictingMeasurement.getValue());
        return false;
      }

      log.debug(
          "Significant measurement difference detected ({}%), amendment required. "
              + "Incoming: {}, Resolved: {}",
          relativeDifference * 100,
          incomingMeasurement.getValue(),
          conflictingMeasurement.getValue());
    }

    return true;
  }

  /**
   * Check if this is likely a reprocessed event to prevent deduction cascades. This helps prevent
   * the "stuck" event issue where the same event gets processed multiple times.
   */
  private boolean isLikelyReprocessedEvent(Event incomingEvent, Event resolvedEvent) {
    // Check if events have the same core identifying characteristics
    boolean sameCore =
        Objects.equals(incomingEvent.getOrgId(), resolvedEvent.getOrgId())
            && Objects.equals(incomingEvent.getInstanceId(), resolvedEvent.getInstanceId())
            && Objects.equals(incomingEvent.getTimestamp(), resolvedEvent.getTimestamp())
            && Objects.equals(incomingEvent.getEventSource(), resolvedEvent.getEventSource())
            && Objects.equals(incomingEvent.getEventType(), resolvedEvent.getEventType());

    if (!sameCore) {
      return false;
    }

    // Check if measurements are very similar (within 0.1% tolerance)
    if (incomingEvent.getMeasurements().size() == 1
        && resolvedEvent.getMeasurements().size() == 1) {
      Measurement incomingMeasurement = incomingEvent.getMeasurements().get(0);
      Measurement resolvedMeasurement = resolvedEvent.getMeasurements().get(0);

      if (Objects.equals(incomingMeasurement.getMetricId(), resolvedMeasurement.getMetricId())) {
        double difference =
            Math.abs(incomingMeasurement.getValue() - resolvedMeasurement.getValue());
        double relativeDifference =
            difference
                / Math.max(
                    Math.abs(incomingMeasurement.getValue()),
                    Math.abs(resolvedMeasurement.getValue()));

        // If measurements are nearly identical, this is likely a reprocessed event
        // BUT only if all other metadata is also identical
        if (relativeDifference < 0.001) { // 0.1% tolerance
          // Additional check: ensure all metadata is identical
          boolean sameMetadata =
              Objects.equals(incomingEvent.getHardwareType(), resolvedEvent.getHardwareType())
                  && Objects.equals(incomingEvent.getSla(), resolvedEvent.getSla())
                  && Objects.equals(incomingEvent.getUsage(), resolvedEvent.getUsage())
                  && Objects.equals(
                      incomingEvent.getBillingProvider(), resolvedEvent.getBillingProvider())
                  && Objects.equals(
                      incomingEvent.getBillingAccountId(), resolvedEvent.getBillingAccountId())
                  && Objects.equals(incomingEvent.getProductTag(), resolvedEvent.getProductTag());

          return sameMetadata;
        }
      }
    }

    return false;
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
