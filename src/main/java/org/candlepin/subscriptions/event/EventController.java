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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.db.model.EventRecordConverter;
import org.candlepin.subscriptions.json.Event;
import org.springframework.stereotype.Service;

/** Encapsulates interaction with event store. */
@Service
@Slf4j
public class EventController {
  private final EventRecordRepository repo;
  private final EventRecordConverter eventRecordConverter;

  public EventController(EventRecordRepository repo, EventRecordConverter eventRecordConverter) {
    this.repo = repo;
    this.eventRecordConverter = eventRecordConverter;
  }

  /**
   * Note: calling method needs to use @Transactional
   *
   * @param orgId Red Hat orgId
   * @param begin beginning of the time range (inclusive)
   * @param end end of the time range (exclusive)
   * @return stream of Event
   */
  public Stream<Event> fetchEventsInTimeRange(
      String orgId, OffsetDateTime begin, OffsetDateTime end) {
    return repo.findByOrgIdAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestamp(
            orgId, begin, end)
        .map(EventRecord::getEvent);
  }

  public Stream<Event> fetchEventsInTimeRangeByServiceType(
      String orgId, String serviceType, OffsetDateTime begin, OffsetDateTime end) {
    return repo.findByOrgIdAndServiceTypeAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestamp(
            orgId, serviceType, begin, end)
        .map(EventRecord::getEvent);
  }

  @SuppressWarnings({"linelength", "indentation"})
  public Map<EventKey, Event> mapEventsInTimeRange(
      String orgId,
      String eventSource,
      String eventType,
      OffsetDateTime begin,
      OffsetDateTime end) {
    return repo.findByOrgIdAndEventSourceAndEventTypeAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestamp(
            orgId, eventSource, eventType, begin, end)
        .map(EventRecord::getEvent)
        .collect(Collectors.toMap(EventKey::fromEvent, Function.identity()));
  }

  /**
   * Validates and saves event JSON in the DB.
   *
   * @param event the event to save
   * @return the event ID
   */
  @Transactional
  public Event saveEvent(Event event) {
    EventRecord eventRecord = new EventRecord(event);
    return repo.save(eventRecord).getEvent();
  }

  /**
   * Validates and saves a list of event JSON objects in the DB.
   *
   * @param events the event JSON objects to save.
   */
  @Transactional
  public List<Event> saveAll(Collection<Event> events) {
    return repo.saveAll(events.stream().map(EventRecord::new).collect(Collectors.toList())).stream()
        .map(EventRecord::getEvent)
        .collect(Collectors.toList());
  }

  /**
   * Fetch a single Event by its ID.
   *
   * @param eventId Event id as a UUID
   * @return Event if present, otherwise Optional.empty()
   */
  @Transactional
  public Optional<Event> getEvent(UUID eventId) {
    try {
      return Optional.of(repo.getOne(eventId).getEvent());
    } catch (EntityNotFoundException e) {
      return Optional.empty();
    }
  }

  @Transactional
  public void deleteEvents(Collection<Event> toDelete) {
    repo.deleteInBatch(toDelete.stream().map(EventRecord::new).collect(Collectors.toList()));
  }

  @Transactional
  public void deleteEvent(UUID eventId) {
    repo.deleteById(eventId);
  }

  @Transactional
  public boolean hasEventsInTimeRange(
      String orgId, String serviceType, OffsetDateTime startDate, OffsetDateTime endDate) {
    return repo.existsByOrgIdAndServiceTypeAndTimestampGreaterThanEqualAndTimestampLessThan(
        orgId, serviceType, startDate, endDate);
  }

  @Transactional
  public void persistServiceInstances(List<String> eventJsonList) {
    Map<EventKey, Event> eventsMap = parseEventRecordsToEventsEntityMap(eventJsonList);
    saveAll(eventsMap.values());
  }

  public Map<EventKey, Event> parseEventRecordsToEventsEntityMap(List<String> eventJsonList) {
    Map<EventKey, Event> eventsMap = new HashMap<>();
    eventJsonList.forEach(
        eventJson -> {
          try {
            Event event = eventRecordConverter.convertToEntityAttribute(eventJson);
            eventsMap.putIfAbsent(EventKey.fromEvent(event), event);
          } catch (Exception e) {
            log.warn(
                "Issue found {} for the event json {} skipping to next", e.getMessage(), eventJson);
          }
        });
    return eventsMap;
  }
}
