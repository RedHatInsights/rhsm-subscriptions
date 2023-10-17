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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.json.BaseEvent;
import org.candlepin.subscriptions.json.CleanUpEvent;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.security.OptInController;
import org.candlepin.subscriptions.util.TransactionHandler;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Encapsulates interaction with event store. */
@Service
@Slf4j
public class EventController {

  private static final Set<String> EXCLUDE_LOG_FOR_EVENT_SOURCES =
      Set.of("prometheus", "rhelemeter");
  private final EventRecordRepository repo;
  private final ObjectMapper objectMapper;
  private final OptInController optInController;
  private final TransactionHandler transactionHandler;

  public EventController(
      EventRecordRepository repo,
      ObjectMapper objectMapper,
      OptInController optInController,
      TransactionHandler transactionHandler) {
    this.repo = repo;
    this.objectMapper = objectMapper;
    this.optInController = optInController;
    this.transactionHandler = transactionHandler;
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
      String orgId,
      String serviceType,
      OffsetDateTime begin,
      OffsetDateTime end,
      OffsetDateTime asOfDateTime) {
    // asOfDateTime is used to ignore events that are recorded outside a given hourly tally's
    // window. Those events will be processed during the next hourly tally, and filtering them out
    // here ensures they won't be processed twice hence we don't have to retally again.
    return repo.findByOrgIdAndServiceTypeAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestamp(
            orgId, serviceType, begin, end)
        .filter(
            eventRecord ->
                Objects.isNull(asOfDateTime) || eventRecord.getRecordDate().isBefore(asOfDateTime))
        .map(EventRecord::getEvent);
  }

  /**
   * Validates and saves a list of event JSON objects in the DB.
   *
   * @param events the event JSON objects to save.
   */
  public List<Event> saveAll(Collection<Event> events) {
    return repo.saveAll(events.stream().map(EventRecord::new).toList()).stream()
        .map(EventRecord::getEvent)
        .toList();
  }

  /**
   * Validates and saves an event JSON object in the DB.
   *
   * @param event the event JSON object to save.
   */
  public Event save(Event event) {
    var result = repo.save(new EventRecord(event));
    return result.getEvent();
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

  /**
   * Parses json list into event objects to be persisted into database. Saves events in a new
   * transaction so that exceptions can be caught, and we can re-attempt to save. If saveAll() fails
   * on events then we try one by one so that we know where to retry and the records that can be
   * saved are persisted. Throws BatchListenerFailedException which tells kafka to Retry or put
   * record on dead letter topic.
   * https://docs.spring.io/spring-kafka/docs/latest-ga/reference/html/#recovering-batch-eh
   *
   * @param eventJsonList
   * @throws BatchListenerFailedException tells kafka where in batch to retry or send failed record
   *     to dead letter topic. The index field of the exception is where kafka will retry.
   */
  public void persistServiceInstances(List<String> eventJsonList)
      throws BatchListenerFailedException {

    ServiceInstancesResult result = parseServiceInstancesResult(eventJsonList);

    try {
      if (!result.eventsMap.isEmpty()) {
        int updated =
            transactionHandler
                .runInNewTransaction(
                    () -> saveAll(result.eventsMap.values().stream().map(Pair::getKey).toList()))
                .size();
        log.debug("Adding/Updating {} metric events", updated);
      }
    } catch (Exception saveAllException) {
      log.warn("Failed to save events. Retrying individually {} events.", result.eventsMap.size());
      result
          .eventsMap
          .values()
          .forEach(
              eventIndexPair -> {
                try {
                  transactionHandler.runInNewTransaction(() -> save(eventIndexPair.getKey()));
                } catch (Exception individualSaveException) {
                  log.warn(
                      "Failed to save individual event record: {}.",
                      ExceptionUtils.getStackTrace(individualSaveException));
                  throw new BatchListenerFailedException(
                      individualSaveException.getMessage(), eventIndexPair.getValue());
                }
              });
    }

    result.cleanUpEvents.forEach(
        cleanUpEvent -> {
          int deleted =
              transactionHandler.runInNewTransaction(
                  () ->
                      repo.deleteStaleEvents(
                          cleanUpEvent.getOrgId(),
                          cleanUpEvent.getEventSource(),
                          cleanUpEvent.getEventType(),
                          cleanUpEvent.getMeteringBatchId(),
                          cleanUpEvent.getStart(),
                          cleanUpEvent.getEnd()));
          log.info(
              "Deleting {} stale metric events for orgId={} and {} metrics",
              deleted,
              cleanUpEvent.getOrgId(),
              cleanUpEvent.getEventType());
        });

    if (result
        .failedOnIndex
        .map(index -> index.compareTo(eventJsonList.size() - 1) < 0)
        .orElse(false)) {
      // We want to skip retrying the failed json parsing event so set index to plus 1.
      throw new BatchListenerFailedException(
          "Failed to parse event json. Skipping to next index in batch.",
          result.failedOnIndex.get() + 1);
    }
  }

  private ServiceInstancesResult parseServiceInstancesResult(List<String> eventJsonList) {
    ServiceInstancesResult result = new ServiceInstancesResult();
    LinkedHashMap<String, Integer> eventIndexMap = mapEventsToBatchIndex(eventJsonList);
    for (Entry<String, Integer> eventIndex : eventIndexMap.entrySet()) {
      try {
        BaseEvent baseEvent = objectMapper.readValue(eventIndex.getKey(), BaseEvent.class);
        if (!EXCLUDE_LOG_FOR_EVENT_SOURCES.contains(baseEvent.getEventSource())) {
          log.info("Event processing in batch: " + eventIndex.getKey());
        }
        if (StringUtils.hasText(baseEvent.getOrgId())) {
          log.debug(
              "Ensuring orgId={} has been set up for syncing/reporting.", baseEvent.getOrgId());
          ensureOptIn(baseEvent.getOrgId());
        }

        if (baseEvent instanceof Event eventToSave) {
          result.addEvent(eventToSave, eventIndex.getValue());
        } else if (baseEvent instanceof CleanUpEvent cleanUpEvent) {
          log.debug("Processing clean up event for: " + cleanUpEvent);
          result.addCleanUpEvent(cleanUpEvent);
        }

      } catch (Exception e) {
        log.warn(
            String.format(
                "Issue found %s for the service instance json skipping to next: {}",
                e.getMessage(), ExceptionUtils.getStackTrace(e)));
        if (result.failedOnIndex.isEmpty()) {
          result.setFailedOnIndex(eventIndex.getValue());
        }
        break;
      }
    }
    return result;
  }

  /**
   * Deduplicate eventJsonList while preserving indexes of events in batch Returns a map ordered by
   * the event record index.
   *
   * @param eventJsonList
   */
  private LinkedHashMap<String, Integer> mapEventsToBatchIndex(List<String> eventJsonList) {
    var result = new LinkedHashMap<String, Integer>();
    for (int index = 0; index < eventJsonList.size(); index++) {
      result.putIfAbsent(eventJsonList.get(index), index);
    }
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
    private final Map<EventKey, Pair<Event, Integer>> eventsMap = new HashMap<>();
    private Optional<Integer> failedOnIndex = Optional.empty();
    private final Set<CleanUpEvent> cleanUpEvents = new HashSet<>();

    private void addEvent(Event event, int index) {
      eventsMap.putIfAbsent(EventKey.fromEvent(event), Pair.of(event, index));
    }

    public void addCleanUpEvent(CleanUpEvent cleanUpEvent) {
      cleanUpEvents.add(cleanUpEvent);
    }

    public void setFailedOnIndex(int index) {
      this.failedOnIndex = Optional.of(index);
    }
  }
}
