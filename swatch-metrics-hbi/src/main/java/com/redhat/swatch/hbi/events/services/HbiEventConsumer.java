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

import com.redhat.swatch.faulttolerance.api.RetryWithExponentialBackoff;
import com.redhat.swatch.kafka.EmitterService;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Event;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
public class HbiEventConsumer {

  private final FeatureFlags flags;

  @SuppressWarnings("java:S1068")
  private final EmitterService<Event> emitter;

  public HbiEventConsumer(@Channel(SWATCH_EVENTS_OUT) Emitter<Event> emitter, FeatureFlags flags) {
    this.emitter = new EmitterService<>(emitter);
    this.flags = flags;
  }

  @Incoming(HBI_HOST_EVENTS_IN)
  @RetryWithExponentialBackoff(
      maxRetries = "${SWATCH_EVENT_PRODUCER_MAX_ATTEMPTS:1}",
      delay = "${SWATCH_EVENT_PRODUCER_BACK_OFF_INITIAL_INTERVAL:1s}",
      maxDelay = "${SWATCH_EVENT_PRODUCER_BACK_OFF_MAX_INTERVAL:60s}",
      factor = "${SWATCH_EVENT_PRODUCER_BACK_OFF_MULTIPLIER:2}")
  public void consume(String hbiHostEvent, KafkaMessageMetadata<?> metadata) {
    log.info(
        "Received host event from HBI - [{}|{}]: {}",
        metadata.getTimestamp(),
        metadata.getKey(),
        hbiHostEvent);
    if (flags.emitEvents()) {
      log.info("Emitting HBI events to swatch!");
    } else {
      log.info("Emitting HBI events to swatch is disabled. Not sending event.");
    }
  }
}
