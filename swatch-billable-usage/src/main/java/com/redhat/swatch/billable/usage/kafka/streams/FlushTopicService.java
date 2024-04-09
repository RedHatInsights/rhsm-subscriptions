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
package com.redhat.swatch.billable.usage.kafka.streams;

import com.redhat.swatch.billable.usage.model.BillableUsage;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * Kafka Streams suppress functionality uses event timestamps in order to advance the internal
 * clock. If no messages come through for some time then the aggregates will remain suppressed. This
 * service publishes a message to every partition of the topic in order to publish the suppressed
 * aggregates.
 */
@ApplicationScoped
public class FlushTopicService {

  Emitter<BillableUsage> emitter;

  @ConfigProperty(name = "KAFKA_BILLABLE_USAGE_PARTITIONS")
  int billableUsagePartitionSize;

  private final String FLUSH_ORG = "flush";
  private final BillableUsage flushUsage;
  private final BillableUsageAggregateKey flushKey;

  public FlushTopicService(
      @Channel("billable-usage-aggregation-repartition") Emitter<BillableUsage> emitter) {
    this.emitter = emitter;
    this.flushKey = new BillableUsageAggregateKey();
    flushKey.setOrgId(FLUSH_ORG);
    this.flushUsage = new BillableUsage();
    flushUsage.setOrgId(FLUSH_ORG);
  }

  public void sendFlushToBillableUsageRepartitionTopic() {
    for (int partition = 0; partition < billableUsagePartitionSize; partition++) {
      emitter.send(
          Message.of(flushUsage)
              .addMetadata(
                  OutgoingKafkaRecordMetadata.<BillableUsageAggregateKey>builder()
                      .withKey(flushKey)
                      .withPartition(partition)
                      .build()));
    }
  }
}
