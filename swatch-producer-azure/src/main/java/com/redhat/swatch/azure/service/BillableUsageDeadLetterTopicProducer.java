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
package com.redhat.swatch.azure.service;

import com.redhat.swatch.azure.openapi.model.BillableUsage;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
public class BillableUsageDeadLetterTopicProducer {

  public static final String RETRY_AFTER_HEADER = "retryAfter";

  @Channel("tally-dlt")
  Emitter<BillableUsage> dlq;

  public void send(BillableUsage billableUsage) {
    dlq.send(
        Message.of(billableUsage)
            .addMetadata(
                OutgoingKafkaRecordMetadata.<String>builder()
                    .withKey(billableUsage.getOrgId())
                    .withHeaders(
                        new RecordHeaders()
                            .add(RETRY_AFTER_HEADER, nextTick().toString().getBytes()))
                    .build()));
  }

  private OffsetDateTime nextTick() {
    return OffsetDateTime.now(Clock.systemUTC()).plusHours(1);
  }
}
