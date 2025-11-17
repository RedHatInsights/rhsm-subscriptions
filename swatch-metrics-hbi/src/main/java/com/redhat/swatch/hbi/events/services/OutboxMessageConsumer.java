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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.hbi.events.configuration.Channels;
import com.redhat.swatch.hbi.events.dtos.outbox.OutboxMessage;
import com.redhat.swatch.kafka.EmitterService;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Event;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

@Slf4j
public class OutboxMessageConsumer {

  public static final String HBI_HOST_EVENTS_IN = "outbox";

  private ObjectMapper mapper;

  private final EmitterService<Event> emitter;

  @Inject
  public OutboxMessageConsumer(
      ObjectMapper mapper, @Channel(Channels.SWATCH_EVENTS_OUT) Emitter<Event> swatchEventEmitter) {
    this.mapper = mapper;
    this.emitter = new EmitterService<>(swatchEventEmitter);
  }

  @Incoming(HBI_HOST_EVENTS_IN)
  public void consumeOutboxMessage(OutboxMessage message) throws Exception {
    //    log.info("Received outbox message - {}", message);
    Event outgoingEvent =
        mapper.readValue(message.getChangeEvent().getAfter().getSwatchEventJson(), Event.class);
    log.info("Sending outgoing event - {}", outgoingEvent);
    sendSwatchEvent(outgoingEvent);
  }

  private void sendSwatchEvent(Event eventToSend) {
    emitter.send(
        Message.of(eventToSend)
            .addMetadata(
                OutgoingKafkaRecordMetadata.builder().withKey(eventToSend.getOrgId()).build()));
  }
}
