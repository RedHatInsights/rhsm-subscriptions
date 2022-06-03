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
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

@Slf4j
@Default
@ApplicationScoped
@Identifier("swatch-producer-aws")
public class KafkaSeekHelper implements KafkaConsumerRebalanceListener {

  private final KafkaClientService kafkaClientService;
  private final Queue<Map<TopicPartition, OffsetAndMetadata>> offsetUpdateQueue =
      new ArrayDeque<>();

  @Inject
  public KafkaSeekHelper(KafkaClientService kafkaClientService) {
    this.kafkaClientService = kafkaClientService;
  }

  @Override
  public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
    performUpdate(consumer);
    KafkaConsumerRebalanceListener.super.onPartitionsAssigned(consumer, partitions);
  }

  @Override
  public void onPartitionsRevoked(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
    performUpdate(consumer);
    KafkaConsumerRebalanceListener.super.onPartitionsAssigned(consumer, partitions);
  }

  @Override
  public void onPartitionsLost(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
    while (!offsetUpdateQueue.isEmpty()) {
      offsetUpdateQueue.poll().forEach(consumer::seek);
    }
    KafkaConsumerRebalanceListener.super.onPartitionsLost(consumer, partitions);
  }

  private void performUpdate(Consumer<?, ?> consumer) {
    while (!offsetUpdateQueue.isEmpty()) {
      Map<TopicPartition, OffsetAndMetadata> pendingUpdate = offsetUpdateQueue.poll();
      consumer.commitSync(pendingUpdate);
      pendingUpdate.forEach(consumer::seek);
    }
  }

  private void seekAndCommit(Consumer<Object, Object> consumer, Map<TopicPartition, Long> offsets) {
    // forcing a rebalance causes quarkus to flush its offset store
    consumer.enforceRebalance();
    Map<TopicPartition, OffsetAndMetadata> offsetUpdates =
        offsets.entrySet().stream()
            .collect(
                Collectors.toMap(Entry::getKey, entry -> new OffsetAndMetadata(entry.getValue())));
    offsetUpdateQueue.offer(offsetUpdates);
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
                            Map<TopicPartition, Long> beginningOffsets =
                                consumer.beginningOffsets(consumer.assignment());
                            seekAndCommit(consumer, beginningOffsets);
                            log.info("Kafka consumer seeked to beginning");
                          } else {
                            Map<TopicPartition, Long> endOffsets =
                                consumer.endOffsets(consumer.assignment());
                            seekAndCommit(consumer, endOffsets);
                            log.info("Kafka consumer seeked to end");
                          }
                        })
                    .subscribe()
                    .asCompletionStage());
  }

  public void seekToTimestamp(OffsetDateTime timestamp) {
    long unixTimestamp = timestamp.toEpochSecond();
    kafkaClientService.getConsumerChannels().stream()
        .map(kafkaClientService::getConsumer)
        .forEach(
            asyncConsumer ->
                asyncConsumer
                    .runOnPollingThread(
                        consumer -> {
                          Map<TopicPartition, Long> desiredOffsets =
                              consumer.assignment().stream()
                                  .collect(
                                      Collectors.toMap(
                                          Function.identity(), topicPartition -> unixTimestamp));
                          seekAndCommit(consumer, desiredOffsets);
                          log.info("Kafka consumer seeked to {}", timestamp);
                        })
                    .subscribe()
                    .asCompletionStage());
  }
}
