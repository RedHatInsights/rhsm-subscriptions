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
package com.redhat.swatch.hbi.ingress;

import com.redhat.swatch.faulttolerance.api.RetryWithExponentialBackoff;
import com.redhat.swatch.hbi.config.Channels.In;
import com.redhat.swatch.hbi.config.FeatureFlags;
import com.redhat.swatch.hbi.dto.HbiEvent;
import com.redhat.swatch.hbi.dto.HbiHostCreateUpdateEventDTO;
import com.redhat.swatch.hbi.processing.HbiHostEventHandler;
import com.redhat.swatch.kafka.EmitterService;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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

  private final FeatureFlags flags;
  private final EmitterService<Event> emitter;
  private final HbiHostEventHandler handler;

  @Inject
  public HbiEventConsumer(
      @Channel("swatch-events-out") Emitter<Event> emitter,
      FeatureFlags flags,
      HbiHostEventHandler handler) {
    this.emitter = new EmitterService<>(emitter);
    this.flags = flags;
    this.handler = handler;
  }

  @Incoming(In.HBI_HOST_EVENTS)
  @RetryWithExponentialBackoff(
      maxRetries = "${SWATCH_EVENT_PRODUCER_MAX_ATTEMPTS:1}",
      delay = "${SWATCH_EVENT_PRODUCER_BACK_OFF_INITIAL_INTERVAL:1s}",
      maxDelay = "${SWATCH_EVENT_PRODUCER_BACK_OFF_MAX_INTERVAL:60s}",
      factor = "${SWATCH_EVENT_PRODUCER_BACK_OFF_MULTIPLIER:2}")
  @Transactional
  public void consume(HbiEvent hbiEvent, KafkaMessageMetadata<?> metadata) {
    if (!(hbiEvent instanceof HbiHostCreateUpdateEventDTO)) {
      log.info("Unsupported HBI event type: {}", hbiEvent.getType());
      return;
    }

    HbiHostCreateUpdateEventDTO hostEvent = (HbiHostCreateUpdateEventDTO) hbiEvent;
    log.info("Received HBI host event: {}", hostEvent);

    List<Event> swatchEvents = handler.handle(hostEvent);

    if (flags.emitEvents()) {
      log.info("Emitting {} events to SWATCH", swatchEvents.size());
      swatchEvents.forEach(e -> emitter.send(Message.of(e)));
    } else {
      log.info(
          "Event emission disabled. Logging {} generated events instead.", swatchEvents.size());
      swatchEvents.forEach(e -> log.debug("Generated SWATCH event: {}", e));
    }
  }
}
