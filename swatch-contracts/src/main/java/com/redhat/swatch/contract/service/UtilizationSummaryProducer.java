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
package com.redhat.swatch.contract.service;

import static com.redhat.swatch.contract.config.Channels.UTILIZATION_OUT;

import com.redhat.swatch.contract.model.UtilizationSummary;
import com.redhat.swatch.kafka.EmitterService;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

@Slf4j
@ApplicationScoped
public class UtilizationSummaryProducer {
  private final EmitterService<UtilizationSummary> emitter;

  @Inject
  public UtilizationSummaryProducer(
      @Channel(UTILIZATION_OUT) Emitter<UtilizationSummary> producer) {
    this.emitter = new EmitterService<>(producer);
  }

  public Uni<Void> send(List<UtilizationSummary> utilizationSummaries) {
    utilizationSummaries.forEach(this::sendUtilizationSummary);
    log.debug("Sent {} utilization summaries", utilizationSummaries.size());
    return Uni.createFrom().voidItem();
  }

  private void sendUtilizationSummary(UtilizationSummary utilization) {
    OutgoingKafkaRecordMetadata<?> metadata =
        OutgoingKafkaRecordMetadata.builder().withKey(utilization.getOrgId()).build();
    emitter.send(Message.of(utilization).addMetadata(metadata));
  }
}
