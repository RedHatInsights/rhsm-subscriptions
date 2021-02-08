/*
 * Copyright (c) 2019 Red Hat, Inc.
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

import org.candlepin.subscriptions.task.queue.kafka.message.TaskMessage;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;

import java.util.Map;

/**
 * Encapsulates the creation of all components required for producing and consuming Kafka
 * messages for the kafka task queue.
 */
public class KafkaConfigurator {

    public DefaultKafkaProducerFactory<String, TaskMessage> defaultProducerFactory(
        KafkaProperties kafkaProperties) {
        Map<String, Object> producerConfig = kafkaProperties.buildProducerProperties();
        boolean bypassRegistry = bypassSchemaRegistry(producerConfig);

        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            bypassRegistry ? AvroSerializer.class : KafkaAvroSerializer.class);
        return new DefaultKafkaProducerFactory<>(producerConfig);
    }

    public ConsumerFactory<String, TaskMessage> defaultConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> consumerConfig = kafkaProperties.buildConsumerProperties();
        // Task messages should be manually committed once they have been processed.
        consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        boolean bypassRegistry = bypassSchemaRegistry(consumerConfig);

        consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // Prevent the client from continuously replaying a message that fails to deserialize.
        consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);

        // Configure the error handling delegate deserializer classes based on whether the
        // schema registry is being bypassed.
        if (bypassRegistry) {
            consumerConfig.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, AvroDeserializer.class);
            consumerConfig.put(AvroDeserializer.TARGET_TYPE_CLASS, TaskMessage.class);
        }
        else {
            consumerConfig.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS,
                KafkaAvroDeserializer.class);
            consumerConfig.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        }
        return new DefaultKafkaConsumerFactory<>(consumerConfig);
    }

    public KafkaTemplate<String, TaskMessage> taskMessageKafkaTemplate(
        ProducerFactory<String, TaskMessage> factory) {
        return new KafkaTemplate<>(factory);
    }

    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, TaskMessage>>
        defaultListenerContainerFactory(ConsumerFactory<String, TaskMessage> consumerFactory,
        KafkaProperties kafkaProperties) {
        ConcurrentKafkaListenerContainerFactory<String, TaskMessage> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // Concurrency should be set to the number of partitions for the target topic.
        factory.setConcurrency(kafkaProperties.getListener().getConcurrency());

        // Task message offsets will be manually committed as soon as the message has been acked.
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    private boolean bypassSchemaRegistry(Map<String, Object> config) {
        return !config.containsKey(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG);
    }
}
