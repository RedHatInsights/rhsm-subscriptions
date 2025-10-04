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

import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.configuration.Channels;
import com.redhat.swatch.hbi.events.model.OutboxRecordMapper;
import com.redhat.swatch.hbi.events.repository.HbiEventOutbox;
import com.redhat.swatch.hbi.events.repository.HbiEventOutboxRepository;
import com.redhat.swatch.hbi.model.OutboxRecord;
import com.redhat.swatch.kafka.EmitterService;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Event;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

@Slf4j
@AllArgsConstructor
@ApplicationScoped
public class HbiEventOutboxService {

  @Inject ApplicationConfiguration config;
  @Inject HbiEventOutboxRepository repository;
  @Inject OutboxRecordMapper mapper;
  @Inject FeatureFlags featureFlags;

  private final EmitterService<Event> emitter;

  @Inject
  public HbiEventOutboxService(
      @Channel(Channels.SWATCH_EVENTS_OUT) Emitter<Event> swatchEventEmitter) {
    this.emitter = new EmitterService<>(swatchEventEmitter);
  }

  @Transactional
  public OutboxRecord createOutboxRecord(Event event) {
    HbiEventOutbox entity = new HbiEventOutbox();
    entity.setOrgId(event.getOrgId());
    entity.setSwatchEventJson(event);
    repository.persistAndFlush(entity);
    return mapper.entityToDto(entity);
  }

  @Synchronized
  public long flushOutboxRecords() {
    log.info("Flushing outbox records in batches of {}", config.getOutboxFlushBatchSize());
    long flushCount = 0;
    // Process all existing outbox records in batches.
    while (true) {
      log.debug("Flushing next batch of {} records", config.getOutboxFlushBatchSize());
      long batchCount = flushNextBatch();
      flushCount += batchCount;

      // No more records to flush, so we are done.
      if (batchCount == 0) {
        break;
      }
    }
    log.info("Flushed {} outbox record(s)", flushCount);
    return flushCount;
  }

  @Transactional
  long flushNextBatch() {
    List<HbiEventOutbox> next = repository.findAllWithLock(config.getOutboxFlushBatchSize());
    for (HbiEventOutbox entity : next) {
      if (featureFlags.emitEvents()) {
        log.debug("Sending swatch event: {}", entity.getSwatchEventJson());
        sendSwatchEvent(entity.getSwatchEventJson());
      } else {
        log.debug(
            "Swatch event sending disabled. Would have sent: {}", entity.getSwatchEventJson());
      }
      repository.delete(entity);
    }
    return next.size();
  }

  private void sendSwatchEvent(Event eventToSend) {
    emitter.send(
        Message.of(eventToSend)
            .addMetadata(
                OutgoingKafkaRecordMetadata.builder().withKey(eventToSend.getOrgId()).build()));
  }
}
