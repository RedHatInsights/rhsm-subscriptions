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
package org.candlepin.subscriptions.util;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.listener.AbstractConsumerSeekAware;

/**
 * Abstract class that adds seeking functionality to a KafkaListener.
 *
 * <p>Specifically, exposes some Spring Kafka seeking methods and uses configuration to optionally
 * seek on startup to either the end of a kafka topic, or to a specific timestamp.
 */
public abstract class SeekableKafkaConsumer extends AbstractConsumerSeekAware {

  private static final Logger log = LoggerFactory.getLogger(SeekableKafkaConsumer.class);

  @Getter protected final String groupId;
  @Getter protected final String topic;
  @Getter protected final OffsetDateTime seekOverrideTimestamp;
  @Getter protected final boolean seekOverrideEnd;
  private final KafkaConsumerRegistry kafkaConsumerRegistry;
  private volatile boolean needsCommit = false;

  @Override
  public void onIdleContainer(
      Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
    if (needsCommit) {
      Set<? extends Consumer<?, ?>> consumers =
          assignments.keySet().stream()
              .map(kafkaConsumerRegistry::getConsumer)
              .collect(Collectors.toSet());
      log.info("Committing offset for {}", assignments.keySet());
      for (Consumer<?, ?> consumer : consumers) {
        consumer.commitSync();
      }
      needsCommit = false;
    }
  }

  @Autowired
  protected SeekableKafkaConsumer(
      TaskQueueProperties taskQueueProperties, KafkaConsumerRegistry kafkaConsumerRegistry) {
    this.groupId = taskQueueProperties.getKafkaGroupId();
    this.topic = taskQueueProperties.getTopic();
    this.seekOverrideTimestamp = taskQueueProperties.getSeekOverrideTimestamp();
    this.seekOverrideEnd = taskQueueProperties.isSeekOverrideEnd();
    this.kafkaConsumerRegistry = kafkaConsumerRegistry;
  }

  @Override
  public void onPartitionsAssigned(
      Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
    super.onPartitionsAssigned(assignments, callback);
    // NOTE: intentionally not calling methods from AbstractConsumerSeekAware as these queue up the
    // seek until the consumer is idle. Otherwise, the seek potentially happens after a single
    // message is processed.
    log.debug("Partitions/offsets: {}", assignments);
    if (seekOverrideTimestamp != null) {
      log.info("Seeking all consumers for {} to {}", topic, seekOverrideTimestamp);
      callback.seekToTimestamp(
          assignments.keySet(), seekOverrideTimestamp.toInstant().toEpochMilli());
      needsCommit = true;
    }
    if (seekOverrideEnd) {
      log.info("Seeking all consumers for {} to the end", topic);
      callback.seekToEnd(assignments.keySet());
      needsCommit = true;
    }
  }

  @Override
  public void seekToEnd() {
    log.info("Seeking all consumers for {} to the end", topic);
    super.seekToEnd();
    needsCommit = true;
  }

  @Override
  public void seekToBeginning() {
    log.info("Seeking all consumers for {} to the beginning", topic);
    super.seekToBeginning();
    needsCommit = true;
  }
}
