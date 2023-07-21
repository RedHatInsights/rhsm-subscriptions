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
package com.redhat.swatch.kafka;

import com.redhat.swatch.openapi.model.KafkaSeekPosition;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.reactive.messaging.kafka.KafkaClientService;
import io.smallrye.reactive.messaging.kafka.KafkaConsumerRebalanceListener;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Slf4j
@Default
@ApplicationScoped
@Identifier("swatch-producer-aws")
public class KafkaSeekHelper implements KafkaConsumerRebalanceListener {

  private final KafkaClientService kafkaClientService;
  private Optional<Boolean> kafkaSeekOverrideEnd;
  private Optional<OffsetDateTime> kafkaSeekOverrideTimestamp;
  private final Queue<Map<TopicPartition, OffsetAndMetadata>> offsetUpdateQueue =
      new ArrayDeque<>();

  @Inject
  public KafkaSeekHelper(
      KafkaClientService kafkaClientService,
      @ConfigProperty(name = "KAFKA_SEEK_OVERRIDE_END") Optional<Boolean> kafkaSeekOverrideEnd,
      @ConfigProperty(name = "KAFKA_SEEK_OVERRIDE_TIMESTAMP")
          Optional<OffsetDateTime> kafkaSeekOverrideTimestamp) {
    this.kafkaClientService = kafkaClientService;
    this.kafkaSeekOverrideEnd = kafkaSeekOverrideEnd;
    this.kafkaSeekOverrideTimestamp = kafkaSeekOverrideTimestamp;
  }

  @Override
  public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
    performUpdate(consumer);
    KafkaConsumerRebalanceListener.super.onPartitionsAssigned(consumer, partitions);
  }

  @Override
  public void onPartitionsRevoked(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
    performUpdate(consumer);
    KafkaConsumerRebalanceListener.super.onPartitionsRevoked(consumer, partitions);
  }

  @Override
  public void onPartitionsLost(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
    while (!offsetUpdateQueue.isEmpty()) {
      offsetUpdateQueue.poll().forEach(consumer::seek);
    }
    KafkaConsumerRebalanceListener.super.onPartitionsLost(consumer, partitions);
  }

  private void performUpdate(Consumer<?, ?> consumer) {
    if (kafkaSeekOverrideEnd.isPresent() && Boolean.TRUE.equals(kafkaSeekOverrideEnd.get())) {
      Map<TopicPartition, OffsetAndMetadata> endOffsets =
          offsetWithMetadata(consumer.endOffsets(consumer.assignment()));
      endOffsets.forEach(consumer::seek);
      consumer.commitSync(endOffsets);
      log.info("Overrode initial offset to end");
      kafkaSeekOverrideEnd = Optional.empty();
    }
    if (kafkaSeekOverrideTimestamp.isPresent()) {
      Map<TopicPartition, OffsetAndMetadata> offsets =
          getOffsetsForTimestamp(consumer, kafkaSeekOverrideTimestamp.get());
      offsets.forEach(consumer::seek);
      consumer.commitSync(offsets);
      log.info("Overrode initial offset to {}", kafkaSeekOverrideTimestamp);
      kafkaSeekOverrideTimestamp = Optional.empty();
    }
    while (!offsetUpdateQueue.isEmpty()) {
      Map<TopicPartition, OffsetAndMetadata> pendingUpdate = offsetUpdateQueue.poll();
      consumer.commitSync(pendingUpdate);
      pendingUpdate.forEach(consumer::seek);
    }
  }

  private static Map<TopicPartition, OffsetAndMetadata> getOffsetsForTimestamp(
      Consumer<?, ?> consumer, OffsetDateTime timestamp) {
    long unixTimestampMs = timestamp.toEpochSecond() * 1000;
    Set<TopicPartition> topicPartitions = consumer.assignment();
    Map<TopicPartition, OffsetAndMetadata> offsets =
        consumer
            .offsetsForTimes(
                topicPartitions.stream()
                    .collect(
                        Collectors.toMap(Function.identity(), topicPartition -> unixTimestampMs)))
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue() != null)
            .collect(
                Collectors.toMap(
                    Entry::getKey, entry -> new OffsetAndMetadata(entry.getValue().offset())));
    for (TopicPartition topicPartition : topicPartitions) {
      if (!offsets.containsKey(topicPartition)) {
        log.warn(
            "No message having a timestamp >= {} exists on Topic {} Partition {}",
            timestamp,
            topicPartition.topic(),
            topicPartition.partition());
      }
    }
    return offsets;
  }

  private void seekAndCommit(
      Consumer<Object, Object> consumer, Map<TopicPartition, OffsetAndMetadata> offsets) {
    // forcing a rebalance causes quarkus to flush its offset store
    consumer.enforceRebalance();
    offsetUpdateQueue.offer(offsets);
    // forcing a rebalance performs the queued updates
    consumer.enforceRebalance();
  }

  public void seekToPosition(KafkaSeekPosition position) {
    kafkaClientService.getConsumerChannels().stream()
        .map(kafkaClientService::getConsumer)
        .forEach(
            asyncConsumer ->
                asyncConsumer
                    .runOnPollingThread(
                        consumer -> {
                          if (position == KafkaSeekPosition.BEGINNING) {
                            Map<TopicPartition, OffsetAndMetadata> beginningOffsets =
                                offsetWithMetadata(
                                    consumer.beginningOffsets(consumer.assignment()));
                            seekAndCommit(consumer, beginningOffsets);
                            log.info("Kafka consumer seeked to beginning");
                          } else {
                            Map<TopicPartition, OffsetAndMetadata> endOffsets =
                                offsetWithMetadata(consumer.endOffsets(consumer.assignment()));
                            seekAndCommit(consumer, endOffsets);
                            log.info("Kafka consumer seeked to end");
                          }
                        })
                    .subscribe()
                    .asCompletionStage());
  }

  private static Map<TopicPartition, OffsetAndMetadata> offsetWithMetadata(
      Map<TopicPartition, Long> offsets) {
    return offsets.entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> new OffsetAndMetadata(entry.getValue())));
  }

  public void seekToTimestamp(OffsetDateTime timestamp) {
    kafkaClientService.getConsumerChannels().stream()
        .map(kafkaClientService::getConsumer)
        .forEach(
            asyncConsumer ->
                asyncConsumer
                    .runOnPollingThread(
                        consumer -> {
                          Map<TopicPartition, OffsetAndMetadata> desiredOffsets =
                              getOffsetsForTimestamp(consumer, timestamp);
                          seekAndCommit(consumer, desiredOffsets);
                          log.info("Kafka consumer seeked to {}", timestamp);
                        })
                    .subscribe()
                    .asCompletionStage());
  }
}
