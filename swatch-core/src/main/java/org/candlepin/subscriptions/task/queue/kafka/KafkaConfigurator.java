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
package org.candlepin.subscriptions.task.queue.kafka;

import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.candlepin.subscriptions.task.JsonTaskMessage;
import org.candlepin.subscriptions.task.queue.kafka.message.TaskMessage;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Encapsulates the creation of all components required for producing and consuming Kafka messages
 * for the kafka task queue.
 */
public class KafkaConfigurator {

  private final KafkaConsumerRegistry consumerRegistry;

  @Autowired
  public KafkaConfigurator(KafkaConsumerRegistry consumerRegistry) {
    this.consumerRegistry = consumerRegistry;
  }

  public DefaultKafkaProducerFactory<String, TaskMessage> defaultProducerFactory(
      KafkaProperties kafkaProperties) {
    Map<String, Object> properties = kafkaProperties.buildProducerProperties();
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(properties);
  }

  public ConsumerFactory<String, JsonTaskMessage> defaultConsumerFactory(
      KafkaProperties kafkaProperties) {
    Map<String, Object> consumerConfig = kafkaProperties.buildConsumerProperties();

    // Task messages should be manually committed once they have been processed.
    consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

    consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    consumerConfig.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);


    //Delegate deserialization to TaskMessageDeserializer.class
    consumerConfig.put(
        ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, TaskMessageDeserializer.class);

    return new DefaultKafkaConsumerFactory<>(consumerConfig);
  }

  public KafkaTemplate<String, JsonTaskMessage> jsonTaskMessageKafkaTemplate(
      ProducerFactory<String, JsonTaskMessage> factory) {
    return new KafkaTemplate<>(factory);
  }

  public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, JsonTaskMessage>>
      defaultListenerContainerFactory(
          ConsumerFactory<String, JsonTaskMessage> consumerFactory,
          KafkaProperties kafkaProperties) {
    ConcurrentKafkaListenerContainerFactory<String, JsonTaskMessage> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    // Concurrency should be set to the number of partitions for the target topic.
    factory.setConcurrency(kafkaProperties.getListener().getConcurrency());

    // commit the offset automatically after the listener method finishes
    factory.getContainerProperties().setAckMode(AckMode.RECORD);
    if (kafkaProperties.getListener().getIdleEventInterval() != null) {
      factory
          .getContainerProperties()
          .setIdleEventInterval(kafkaProperties.getListener().getIdleEventInterval().toMillis());
    }
    // hack to track the Kafka consumers, so SeekableKafkaConsumer can commit when needed
    factory.getContainerProperties().setConsumerRebalanceListener(consumerRegistry);
    return factory;
  }
}
