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
package com.redhat.swatch.component.tests.api;

import com.redhat.swatch.component.tests.core.BaseService;
import com.redhat.swatch.component.tests.kafka.KafkaJsonDeserializer;
import com.redhat.swatch.component.tests.kafka.KafkaJsonSerializer;
import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class KafkaClientService extends BaseService<KafkaClientService> {
  private static final String CONSUMER_GROUP = "component-tests";

  // Consumers by topic
  private final Map<String, KafkaConsumer<String, ?>> consumers = new HashMap<>();
  private final Map<String, Class<?>> consumerTypes = new HashMap<>();

  public KafkaClientService subscribeToTopic(String topic) {
    return subscribeToTopic(topic, Object.class);
  }

  public KafkaClientService subscribeToTopic(String topic, Class<?> clazz) {
    consumers.put(topic, null);
    consumerTypes.put(topic, clazz);
    return this;
  }

  @Override
  public void start() {
    super.start();

    for (var consumer : consumers.entrySet()) {
      String consumerTopic = consumer.getKey();
      consumer.setValue(createConsumerForTopic(consumerTopic, consumerTypes.get(consumerTopic)));
    }
  }

  private Properties kafkaConsumerProperties(String groupId, Class<?> clazz) {
    Properties config = new Properties();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getHost() + ":" + getMappedPort(9092));
    config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    config.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonDeserializer.class.getName());
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
    config.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
    config.put(KafkaJsonDeserializer.VALUE_DEFAULT_TYPE, clazz);
    return config;
  }

  private Properties kafkaProducerProperties() {
    Properties config = new Properties();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getHost() + ":" + getMappedPort(9092));
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSerializer.class.getName());
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    config.put(ProducerConfig.RETRIES_CONFIG, 3);
    config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
    config.put(ProducerConfig.LINGER_MS_CONFIG, 1);
    config.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
    config.put(KafkaJsonSerializer.ADD_TYPE_INFO_HEADERS, true);
    return config;
  }

  @Override
  public void stop() {
    for (var consumer : consumers.values()) {
      deleteConsumer(consumer);
    }

    super.stop();
  }

  public <T> void produceKafkaMessage(String topic, T value) {
    try (KafkaProducer<String, T> producer = new KafkaProducer<>(kafkaProducerProperties())) {
      ProducerRecord<String, T> producerRecord = new ProducerRecord<>(topic, value);

      try {
        // Send the record and get a Future for the send result
        Future<RecordMetadata> future =
            producer.send(
                producerRecord,
                (metadata, exception) -> {
                  if (exception != null) {
                    Log.error("Error sending message to %s: %s", topic, exception.getMessage());
                  } else {
                    Log.info(
                        "Sent to %s: partition=%d, offset=%d",
                        metadata.topic(), metadata.partition(), metadata.offset());
                  }
                });

        // Blocking IO
        AwaitilityUtils.untilIsTrue(future::isDone);
      } catch (Exception e) {
        Log.error("Message production failed: %s", e);
      }
    }
  }

  public <V> List<V> waitForKafkaMessage(
      String topic, MessageValidator<V> messageValidator, int expectedCount) {
    var consumer = consumers.get(topic);
    if (consumer == null) {
      throw new IllegalArgumentException("No consumer for topic " + topic);
    }

    List<V> matchedMessages = new ArrayList<>();

    AwaitilityUtils.untilIsTrue(
        () -> {
          // Safe cast: all consumers are created with Object as the value type
          ConsumerRecords<String, V> records =
              (ConsumerRecords<String, V>) consumer.poll(Duration.ofSeconds(1));
          if (records.isEmpty()) {
            Log.debug(this, "No messages found for topic %s", topic);
            return expectedCount == 0;
          }
          if (records.count() >= expectedCount) {
            matchedMessages.addAll(
                StreamSupport.stream(records.spliterator(), false)
                    .map(ConsumerRecord::value)
                    .filter(messageValidator::test)
                    .toList());
            Log.debug("Found %d valid messages", matchedMessages);
            return matchedMessages.size() >= expectedCount;
          } else {
            return false;
          }
        },
        AwaitilitySettings.defaults().withService(this));
    return matchedMessages;
  }

  @SuppressWarnings("unchecked")
  public void waitForKafkaMessage(
      String topic, Predicate<ConsumerRecord<String, ?>> messageValidator, int expectedCount) {
    var consumer = consumers.get(topic);
    if (consumer == null) {
      throw new IllegalArgumentException("No consumer for topic " + topic);
    }

    AwaitilityUtils.untilIsTrue(
        () -> {
          // Safe cast: all consumers are created with Object as the value type
          ConsumerRecords<String, Object> records =
              (ConsumerRecords<String, Object>) consumer.poll(Duration.ofSeconds(1));
          if (records.count() >= expectedCount) {
            if (expectedCount == 0) {
              return true;
            }
            var result =
                StreamSupport.stream(records.spliterator(), false).anyMatch(messageValidator);
            Log.info("Results is " + result);
            return result;
          }
          return false;
        },
        AwaitilitySettings.defaults().withService(this));
  }

  private void deleteConsumer(KafkaConsumer<String, ?> consumerInstance) {
    try (consumerInstance) {
      Log.debug(this, "Closing consumer: %s", consumerInstance);
    }
  }

  @SuppressWarnings("java:S2095")
  private <T> KafkaConsumer<String, T> createConsumerForTopic(String topic, Class<T> clazz) {
    Log.debug(this, "Creating consumer for topic '%s' with value type %s", topic, clazz.getName());
    var config = this.kafkaConsumerProperties(CONSUMER_GROUP, clazz);
    var consumer = new KafkaConsumer<String, T>(config);
    consumer.subscribe(List.of(topic));
    return consumer;
  }
}
