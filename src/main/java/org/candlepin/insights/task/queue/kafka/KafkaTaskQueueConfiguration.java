/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.insights.task.queue.kafka;

import org.candlepin.insights.task.TaskFactory;
import org.candlepin.insights.task.queue.TaskQueue;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer2;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;


/**
 * A spring configuration that configures the required Beans to set up a KafkaTaskQueue.
 *
 * To enable this queue, set the following in the rhsm-conduit.properties file:
 * <pre>
 *     rhsm-conduit.tasks.queue=kafka
 * </pre>
 */
@EnableKafka
@Configuration
@PropertySource("classpath:/rhsm-conduit.properties")
public class KafkaTaskQueueConfiguration {

    private static final String  TYPE_MAPPINGS = "task-message:" + TaskMessage.class.getCanonicalName();

    @Autowired
    private KafkaProperties kafkaProperties;

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.tasks", name = "queue", havingValue = "kafka")
    public ProducerFactory<String, TaskMessage> producerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfig());
    }

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.tasks", name = "queue", havingValue = "kafka")
    public Map<String, Object> producerConfig() {
        Map<String, Object> config = kafkaProperties.buildProducerProperties();
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(JsonSerializer.TYPE_MAPPINGS, TYPE_MAPPINGS);
        return config;
    }

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.tasks", name = "queue", havingValue = "kafka")
    public KafkaTemplate<String, TaskMessage> kafkaProducerTemplate() {
        return new KafkaTemplate<String, TaskMessage>(producerFactory());
    }

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.tasks", name = "queue", havingValue = "kafka")
    KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, TaskMessage>>
        kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TaskMessage> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // Concurrency should be set to the number of partitions for the target topic.
        factory.setConcurrency(kafkaProperties.getListener().getConcurrency());
        return factory;
    }

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.tasks", name = "queue", havingValue = "kafka")
    public ConsumerFactory<String, TaskMessage> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerConfigs());
    }

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.tasks", name = "queue", havingValue = "kafka")
    public Map<String, Object> consumerConfigs() {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // Prevent the client from continuously replaying a message that fails to deserialize.
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer2.class);
        props.put(ErrorHandlingDeserializer2.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, KafkaTaskQueue.class.getPackage().getName());
        props.put(JsonDeserializer.TYPE_MAPPINGS, TYPE_MAPPINGS);

        return props;
    }


    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.tasks", name = "queue", havingValue = "kafka")
    public TaskQueue kafkaTaskQueue() {
        return new KafkaTaskQueue();
    }

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.tasks", name = "queue", havingValue = "kafka")
    public KafkaTaskProcessor taskProcessor(TaskFactory taskFactory) {
        return new KafkaTaskProcessor(taskFactory);
    }

}
