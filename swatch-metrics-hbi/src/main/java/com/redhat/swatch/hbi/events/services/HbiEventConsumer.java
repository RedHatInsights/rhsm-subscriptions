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
import static com.redhat.swatch.hbi.events.configuration.Channels.SWATCH_EVENTS_OUT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.faulttolerance.api.RetryWithExponentialBackoff;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;
import com.redhat.swatch.hbi.events.exception.UnrecoverableMessageProcessingException;
import com.redhat.swatch.hbi.events.processing.HbiEventProcessor;
import com.redhat.swatch.hbi.events.processing.UnsupportedHbiEventException;
import com.redhat.swatch.kafka.EmitterService;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Event;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

@Slf4j
@ApplicationScoped
public class HbiEventConsumer {

  public static final String EVENT_SERVICE_TYPE = "HBI_HOST";
  public static final String EVENT_SOURCE = "HBI_EVENT";
  private final FeatureFlags flags;

  @SuppressWarnings("java:S1068")
  private final EmitterService<Event> emitter;

  private final HbiEventProcessor hbiEventProcessor;
  private final ObjectMapper objectMapper;

  public HbiEventConsumer(
      @Channel(SWATCH_EVENTS_OUT) Emitter<Event> emitter,
      FeatureFlags flags,
      HbiEventProcessor hbiEventProcessor,
      ObjectMapper objectMapper) {
    this.emitter = new EmitterService<>(emitter);
    this.flags = flags;
    this.hbiEventProcessor = hbiEventProcessor;
    this.objectMapper = objectMapper;
  }

  @Incoming(HBI_HOST_EVENTS_IN)
  @RetryWithExponentialBackoff(
      maxRetries = "${SWATCH_EVENT_PRODUCER_MAX_ATTEMPTS:1}",
      delay = "${SWATCH_EVENT_PRODUCER_BACK_OFF_INITIAL_INTERVAL:1s}",
      maxDelay = "${SWATCH_EVENT_PRODUCER_BACK_OFF_MAX_INTERVAL:60s}",
      factor = "${SWATCH_EVENT_PRODUCER_BACK_OFF_MULTIPLIER:2}")
  public void consume(HbiEvent hbiEvent, KafkaMessageMetadata<?> metadata) {
    logHbiEvent(hbiEvent);

    try {
      List<Event> toSend = hbiEventProcessor.process(hbiEvent);
      if (flags.emitEvents()) {
        log.info("Emitting {} HBI events to swatch! {}", toSend.size(), toSend);
        toSend.forEach(
            eventToSend ->
                emitter.send(
                    Message.of(eventToSend)
                        .addMetadata(
                            OutgoingKafkaRecordMetadata.builder()
                                .withKey(eventToSend.getOrgId())
                                .build())));
      } else {
        log.info(
            "Emitting HBI events to swatch is disabled. Not sending {} events.", toSend.size());
        toSend.forEach(eventToSend -> log.info("EVENT: {}", eventToSend));
      }
    } catch (UnsupportedHbiEventException unsupportedException) {
      log.warn("HBI Event not supported!", unsupportedException);
    } catch (UnrecoverableMessageProcessingException e) {
      log.warn(
          "Unrecoverable message when processing incoming HBI event. Event will not be retried.",
          e);
    }
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
}
