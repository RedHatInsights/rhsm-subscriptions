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

import static org.candlepin.subscriptions.metering.MeteringEventFactory.EVENT_SOURCE;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.json.BaseEvent;
import org.candlepin.subscriptions.json.CleanUpEvent;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.security.OptInController;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Encapsulates interaction with event store. */
@Service
@Slf4j
public class EventController {
  private final EventRecordRepository repo;
  private final ObjectMapper objectMapper;
  private final OptInController optInController;

  public EventController(
      EventRecordRepository repo, ObjectMapper objectMapper, OptInController optInController) {
    this.repo = repo;
    this.objectMapper = objectMapper;
    this.optInController = optInController;
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

  /**
   * Validates and saves a list of event JSON objects in the DB.
   *
   * @param events the event JSON objects to save.
   */
  @Transactional
  public List<Event> saveAll(Collection<Event> events) {
    return repo.saveAll(events.stream().map(EventRecord::new).toList()).stream()
        .map(EventRecord::getEvent)
        .toList();
  }

  @Transactional
  public void deleteEvent(UUID eventId) {
    repo.deleteByEventId(eventId);
  }

  @Transactional
  public boolean hasEventsInTimeRange(
      String orgId, String serviceType, OffsetDateTime startDate, OffsetDateTime endDate) {
    return repo.existsByOrgIdAndServiceTypeAndTimestampGreaterThanEqualAndTimestampLessThan(
        orgId, serviceType, startDate, endDate);
  }

  @Transactional
  public Optional<OffsetDateTime> findFirstEventTimestampInRange(
      String orgId, String serviceType, OffsetDateTime startDate, OffsetDateTime endDate) {
    return Optional.ofNullable(
            repo.findFirstEventTimestampInRange(orgId, serviceType, startDate, endDate))
        .map(dateTime -> dateTime.atOffset(ZoneOffset.UTC));
  }

  @Transactional
  public void persistServiceInstances(Set<String> eventJsonList) {
    ServiceInstancesResult result = parseServiceInstancesResult(eventJsonList);
    if (!result.eventsMap.isEmpty()) {
      int updated = saveAll(result.eventsMap.values()).size();
      log.debug("Adding/Updating {} metric events", updated);
    }

    result.cleanUpEvents.forEach(
        cleanUpEvent -> {
          int deleted =
              repo.deleteStaleEvents(
                  cleanUpEvent.getOrgId(),
                  cleanUpEvent.getEventSource(),
                  cleanUpEvent.getEventType(),
                  cleanUpEvent.getMeteringBatchId(),
                  cleanUpEvent.getStart(),
                  cleanUpEvent.getEnd());
          log.info(
              "Deleting {} stale metric events for orgId={} and {} metrics",
              deleted,
              cleanUpEvent.getOrgId(),
              cleanUpEvent.getEventType());
        });
  }

  private ServiceInstancesResult parseServiceInstancesResult(Set<String> eventJsonList) {
    ServiceInstancesResult result = new ServiceInstancesResult();
    eventJsonList.forEach(
        eventJson -> {
          try {
            BaseEvent baseEvent = objectMapper.readValue(eventJson, BaseEvent.class);
            if (!EVENT_SOURCE.equals(baseEvent.getEventSource())) {
              log.info("Event processing in batch: " + baseEvent);
            }

            if (StringUtils.hasText(baseEvent.getOrgId())) {
              log.debug(
                  "Ensuring orgId={} has been set up for syncing/reporting.", baseEvent.getOrgId());
              ensureOptIn(baseEvent.getOrgId());
            }

            if (baseEvent instanceof Event) {
              result.addEvent((Event) baseEvent);
            } else if (baseEvent instanceof CleanUpEvent) {
              CleanUpEvent cleanUpEvent = (CleanUpEvent) baseEvent;
              log.debug("Processing clean up event for: " + cleanUpEvent);
              result.addCleanUpEvent(cleanUpEvent);
            }

          } catch (Exception e) {
            log.warn(
                String.format(
                    "Issue found %s for the service instance json skipping to next ",
                    e.getCause()));
          }
        });
    return result;
  }

  private void ensureOptIn(String orgId) {
    try {
      optInController.optInByOrgId(orgId, OptInType.PROMETHEUS);
    } catch (Exception e) {
      log.error("Error while attempting to automatically opt-in for orgId={} ", orgId, e);
    }
  }

  private static class ServiceInstancesResult {
    private final Map<EventKey, Event> eventsMap = new HashMap<>();
    private final Set<CleanUpEvent> cleanUpEvents = new HashSet<>();

    private void addEvent(Event event) {
      eventsMap.putIfAbsent(EventKey.fromEvent(event), event);
    }

    public void addCleanUpEvent(CleanUpEvent cleanUpEvent) {
      cleanUpEvents.add(cleanUpEvent);
    }
  }
}
