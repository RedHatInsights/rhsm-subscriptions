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
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.configuration.util.ProductTagLookupParams;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.security.OptInController;
import org.candlepin.subscriptions.util.TransactionHandler;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Encapsulates interaction with event store. */
@Service
@Slf4j
public class EventController {

  protected static final String INGESTED_USAGE_METRIC = "swatch_metrics_ingested_usage_total";
  private static final Set<String> EXCLUDE_LOG_FOR_EVENT_SOURCES =
      Set.of("prometheus", "rhelemeter");
  private final EventRecordRepository repo;
  private final ObjectMapper objectMapper;
  private final OptInController optInController;
  private final TransactionHandler transactionHandler;
  private final EventConflictResolver eventConflictResolver;
  private final EventNormalizer eventNormalizer;
  private final MeterRegistry meterRegistry;

  public EventController(
      EventRecordRepository repo,
      ObjectMapper objectMapper,
      OptInController optInController,
      TransactionHandler transactionHandler,
      EventConflictResolver eventConflictResolver,
      EventNormalizer eventNormalizer,
      MeterRegistry meterRegistry) {
    this.repo = repo;
    this.objectMapper = objectMapper;
    this.optInController = optInController;
    this.transactionHandler = transactionHandler;
    this.eventConflictResolver = eventConflictResolver;
    this.eventNormalizer = eventNormalizer;
    this.meterRegistry = meterRegistry;
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

  @Transactional(readOnly = true)
  public void processEventsInBatches(
      String orgId,
      String serviceType,
      OffsetDateTime begin,
      int batchSize,
      Consumer<List<Event>> eventConsumer) {
    List<Event> toProcess = new ArrayList<>(batchSize);
    try (Stream<EventRecord> eventStream =
        repo.fetchOrderedEventStream(orgId, serviceType, begin)) {
      eventStream.forEach(
          eventRecord -> {
            toProcess.add(eventRecord.getEvent());
            repo.getEntityManager().detach(eventRecord);
            if (toProcess.size() == batchSize) {
              log.debug(
                  "Processing batch of {} Events for orgId={} serviceType={}",
                  batchSize,
                  orgId,
                  serviceType);
              eventConsumer.accept(toProcess);
              toProcess.clear();
            }
          });

      // Process any that were outside the batch boundary.
      if (!toProcess.isEmpty()) {
        log.debug("Processing the remaining batch {}", toProcess.size());
        eventConsumer.accept(toProcess);
        toProcess.clear();
      }
    }
  }

  /**
   * Save the collection of EventRecord to the DB.
   *
   * @param events the event records to save.
   * @return the persisted {@link EventRecord}s
   */
  public List<EventRecord> saveAllEventRecords(Collection<EventRecord> events) {
    return repo.saveAll(events);
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

  /**
   * Parses json list into event objects to be persisted into database. Saves events in a new
   * transaction so that exceptions can be caught, and we can re-attempt to save. If saveAll() fails
   * on events then we try one by one so that we know where to retry and the records that can be
   * saved are persisted. Throws BatchListenerFailedException which tells kafka to Retry or put
   * record on dead letter topic. <a
   * href="https://docs.spring.io/spring-kafka/docs/latest-ga/reference/html/#recovering-batch-eh">
   * See more in the documentation</a>
   *
   * @param eventJsonList a List of Events in JSON form
   * @throws BatchListenerFailedException tells kafka where in batch to retry or send failed record
   *     to dead letter topic. The index field of the exception is where kafka will retry.
   */
  public void persistServiceInstances(List<String> eventJsonList)
      throws BatchListenerFailedException {
    ServiceInstancesResult result = parseServiceInstancesResult(eventJsonList);
    List<EventRecord> savedEvents = new ArrayList<>();
    try {
      if (!result.indexedEvents.isEmpty()) {
        // Check to see if any of the incoming Events are in conflict and if so, resolve them.
        savedEvents.addAll(
            transactionHandler.runInNewTransaction(
                () -> {
                  List<EventRecord> resolved =
                      resolveEventConflicts(
                          result.indexedEvents.stream().map(Pair::getKey).toList());
                  return repo.saveAll(resolved);
                }));
        log.debug("Adding/Updating {} metric events", savedEvents.size());
      }
    } catch (Exception saveAllException) {
      log.warn(
          "Failed to save events. Retrying individually {} events.", result.indexedEvents.size());
      result.indexedEvents.forEach(
          indexedPair -> {
            try {
              savedEvents.addAll(
                  transactionHandler.runInNewTransaction(
                      () ->
                          repo.saveAll(
                              eventConflictResolver.resolveIncomingEvents(
                                  List.of(indexedPair.getKey())))));
            } catch (Exception individualSaveException) {
              log.warn(
                  "Failed to save individual event record: {} with error {}.",
                  indexedPair.getKey(),
                  ExceptionUtils.getStackTrace(individualSaveException));
              throw new BatchListenerFailedException(
                  individualSaveException.getMessage(), indexedPair.getValue());
            }
          });
    }

    // create the ingested usage metrics for created events
    updateIngestedUsage(savedEvents);

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

  public List<EventRecord> resolveEventConflicts(List<Event> toResolve) {
    return eventConflictResolver.resolveIncomingEvents(toResolve);
  }

  private ServiceInstancesResult parseServiceInstancesResult(List<String> eventJsonList) {
    LinkedHashMap<String, Integer> indexedEventJson = mapEventsToBatchIndex(eventJsonList);
    ServiceInstancesResult result = new ServiceInstancesResult(indexedEventJson.size());
    for (Entry<String, Integer> entry : indexedEventJson.entrySet()) {
      try {
        Event eventToProcess =
            eventNormalizer.normalizeEvent(objectMapper.readValue(entry.getKey(), Event.class));
        if (!EXCLUDE_LOG_FOR_EVENT_SOURCES.contains(eventToProcess.getEventSource())) {
          log.info("Event processing in batch: " + entry.getKey());
        }
        processEvent(eventToProcess).ifPresent(e -> result.addEvent(e, entry.getValue()));
      } catch (Exception e) {
        log.warn(
            "Issue found {} for the service instance json {} skipping to next: {}",
            e.getMessage(),
            entry.getKey(),
            ExceptionUtils.getStackTrace(e));
        if (result.failedOnIndex.isEmpty()) {
          result.setFailedOnIndex(entry.getValue());
        }
        break;
      }
    }
    return result;
  }

  private Optional<Event> processEvent(Event eventToProcess) {
    if (!validateServiceInstanceEvent(eventToProcess)) {
      log.warn(ErrorCode.INVALID_EVENT_CONSUMER_ERROR.toString(), eventToProcess);
      return Optional.empty();
    }

    if (StringUtils.hasText(eventToProcess.getOrgId())) {
      log.debug(
          "Ensuring orgId={} has been set up for syncing/reporting.", eventToProcess.getOrgId());
      ensureOptIn(eventToProcess.getOrgId());
    }

    Optional<String> azureSubscriptionId = eventToProcess.getAzureSubscriptionId();
    if (azureSubscriptionId != null && azureSubscriptionId.isPresent()) { // NOSONAR
      eventToProcess.setBillingAccountId(eventToProcess.getAzureSubscriptionId());
    }
    return Optional.of(eventToProcess);
  }

  public boolean validateServiceInstanceEvent(Event event) {
    return isValidInstanceId(event) && isValidMeasurement(event) && isValidProductTag(event);
  }

  private boolean isValidInstanceId(Event event) {
    boolean isValid = true;

    if (Objects.isNull(event.getInstanceId())) {
      log.warn("Event.instanceId must not be null. event={}", event);
      isValid = false;
    }

    return isValid;
  }

  private boolean isValidMeasurement(Event event) {
    boolean isValid = true;

    List<Measurement> invalidMeasurements =
        Optional.ofNullable(event.getMeasurements()).orElse(Collections.emptyList()).stream()
            .filter(m -> Objects.nonNull(m.getValue()) && m.getValue() < 0)
            .toList();

    if (!invalidMeasurements.isEmpty()) {
      log.warn("Event measurement value(s) must be >= 0. event={}", event);
      isValid = false;
    }

    return isValid;
  }

  private boolean isValidProductTag(Event event) {
    boolean isValid = true;

    // If product tag is already present in the event then verify whether it is present in our
    // config
    if (Objects.nonNull(event.getProductTag()) && !event.getProductTag().isEmpty()) {
      isValid =
          event.getProductTag().stream().findFirst().map(Variant::isValidProductTag).orElse(false);
      if (!isValid) {
        log.warn("Product tag {} is invalid.", event.getProductTag().stream().findFirst());
      }
    } else {
      String role = event.getRole() != null ? event.getRole().toString() : null;

      var lookupParams =
          ProductTagLookupParams.builder()
              .engIds(event.getProductIds())
              .role(role)
              .metricIds(
                  event.getMeasurements().stream()
                      .map(Measurement::getMetricId)
                      .map(Object::toString)
                      .collect(Collectors.toSet()))
              // Swatch currently considers all Events as being for metered/payg
              .isPaygEligibleProduct(true)
              .is3rdPartyMigration(event.getConversion())
              .build();

      var matchingProductTags = SubscriptionDefinition.getAllProductTags(lookupParams);

      if (matchingProductTags.isEmpty()) {
        log.warn("Event data doesn't match configured product tags in swatch. event={}", event);
        isValid = false;
      } else {
        log.debug("matching payg product tags for event={}: {}", event, matchingProductTags);
        event.setProductTag(matchingProductTags);
        log.info("event.product_tags={}", event.getProductTag());
      }
    }

    return isValid;
  }

  /**
   * Deduplicate eventJsonList while preserving indexes of events in batch Returns a map ordered by
   * the event record index.
   *
   * @param eventJsonList a List of Events in JSON format
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

  private void updateIngestedUsage(List<EventRecord> eventRecords) {
    for (EventRecord eventRecord : eventRecords) {
      Event event = eventRecord.getEvent();
      if (event.getProductTag() == null || event.getMeasurements() == null) {
        continue;
      }
      if ("prometheus".equalsIgnoreCase(event.getEventSource())) {
        for (String tag : event.getProductTag()) {
          for (Measurement measurement : event.getMeasurements()) {
            updateIngestedUsageCounterFor(event, tag, measurement);
          }
        }
      }
    }
  }

  private void updateIngestedUsageCounterFor(Event event, String tag, Measurement measurement) {
    var counter = Counter.builder(INGESTED_USAGE_METRIC);
    if (event.getBillingProvider() != null) {
      counter.tag("billing_provider", event.getBillingProvider().value());
    }
    try {
      counter
          .tag("metric_id", MetricId.fromString(measurement.getMetricId()).toUpperCaseFormatted())
          .withRegistry(meterRegistry)
          .withTags("product", tag)
          .increment(measurement.getValue());
    } catch (Exception e) {
      log.error(
          "Error to increment counter '{}'. Event was '{}' and measurement '{}'",
          INGESTED_USAGE_METRIC,
          event,
          measurement);
    }
  }

  private static class ServiceInstancesResult {
    private final List<Pair<Event, Integer>> indexedEvents;
    private Optional<Integer> failedOnIndex = Optional.empty();

    public ServiceInstancesResult(int potentialSize) {
      this.indexedEvents = new ArrayList<>(potentialSize);
    }

    private void addEvent(Event event, int index) {
      indexedEvents.add(Pair.of(event, index));
    }

    public void setFailedOnIndex(int index) {
      this.failedOnIndex = Optional.of(index);
    }
  }
}
