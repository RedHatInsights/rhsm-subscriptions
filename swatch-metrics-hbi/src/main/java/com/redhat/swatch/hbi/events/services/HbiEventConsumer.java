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
package com.redhat.swatch.hbi.events.services;

import static com.redhat.swatch.hbi.events.configuration.Channels.HBI_HOST_EVENTS_IN;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.faulttolerance.api.RetryWithExponentialBackoff;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;
import com.redhat.swatch.hbi.events.exception.UnrecoverableMessageProcessingException;
import com.redhat.swatch.hbi.events.processing.HbiEventProcessor;
import com.redhat.swatch.hbi.events.processing.UnsupportedHbiEventException;
import com.redhat.swatch.hbi.events.repository.HbiEventOutbox;
import com.redhat.swatch.hbi.events.repository.HbiEventOutboxRepository;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Event;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
public class HbiEventConsumer {

  public static final String EVENT_SERVICE_TYPE = "HBI_HOST";
  public static final String EVENT_SOURCE = "HBI_EVENT";
  public static final String EVENTS_METRIC = "rhsm-subscriptions.metrics-hbi.events";
  public static final String TIMED_EVENTS_METRIC = EVENTS_METRIC + ".timed";
  public static final String COUNTER_EVENTS_METRIC = EVENTS_METRIC + ".counter";

  private final HbiEventProcessor hbiEventProcessor;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;
  private final HbiEventOutboxRepository outboxRepository;

  public HbiEventConsumer(
      HbiEventProcessor hbiEventProcessor,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      HbiEventOutboxRepository outboxRepository) {
    this.hbiEventProcessor = hbiEventProcessor;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
    this.outboxRepository = outboxRepository;
  }

  @Timed(TIMED_EVENTS_METRIC)
  @Incoming(HBI_HOST_EVENTS_IN)
  @RetryWithExponentialBackoff(
      maxRetries = "${SWATCH_EVENT_PRODUCER_MAX_ATTEMPTS:1}",
      delay = "${SWATCH_EVENT_PRODUCER_BACK_OFF_INITIAL_INTERVAL:1s}",
      maxDelay = "${SWATCH_EVENT_PRODUCER_BACK_OFF_MAX_INTERVAL:60s}",
      factor = "${SWATCH_EVENT_PRODUCER_BACK_OFF_MULTIPLIER:2}")
  @Transactional
  public void consume(HbiEvent hbiEvent) {
    logHbiEvent(hbiEvent);
    try {
      List<Event> toPersist = hbiEventProcessor.process(hbiEvent);
      if (!toPersist.isEmpty()) {
        log.info("Persisting {} SWatch events into outbox.", toPersist.size());
        toPersist.forEach(this::persistOutboxRecord);
      } else {
        log.info("No SWatch events produced to persist.");
      }
      incrementCounter(hbiEvent.getType());
    } catch (UnsupportedHbiEventException unsupportedException) {
      log.warn("HBI Event not supported!", unsupportedException);
      incrementCounterWithError(hbiEvent.getType(), "unsupported");
    } catch (UnrecoverableMessageProcessingException e) {
      log.warn(
          "Unrecoverable message when processing incoming HBI event. Event will not be retried.",
          e);
      incrementCounterWithError(hbiEvent.getType(), e.getMessage());
    }
  }

  private void persistOutboxRecord(Event eventToPersist) {
    HbiEventOutbox entity = new HbiEventOutbox();
    entity.setOrgId(eventToPersist.getOrgId());
    entity.setSwatchEventJson(eventToPersist);
    outboxRepository.persist(entity);
  }

  private void logHbiEvent(HbiEvent hbiEvent) {
    // No sense in converting the message to a string if DEBUG is not enabled.
    if (log.isDebugEnabled()) {
      String eventString;
      try {
        eventString = objectMapper.writeValueAsString(hbiEvent);
      } catch (JsonProcessingException e) {
        // Just log the object via toString if serialization fails.
        eventString = hbiEvent.toString();
      }
      log.debug("Received host event from HBI - {}", eventString);
    }
  }

  private void incrementCounter(String type) {
    doIncrementCounter(type, "none");
  }

  private void incrementCounterWithError(String type, String errorMessage) {
    doIncrementCounter(type, errorMessage);
  }

  private void doIncrementCounter(String type, String errorMessage) {
    meterRegistry
        .counter(COUNTER_EVENTS_METRIC, Tags.of("type", type).and("error-message", errorMessage))
        .increment();
  }
}
