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
import org.candlepin.insights.task.queue.kafka.message.TaskMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;


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

    @Autowired
    private KafkaProperties kafkaProperties;

    // Since the bean is only registered when the kafka task queue is configured, it isn't always
    // required (i.e When conduit is running with the in-memory task queue configured).
    @Autowired(required = false)
    private KafkaConfigurator kafkaConfigurator;

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.tasks", name = "queue", havingValue = "kafka")
    public KafkaConfigurator kafkaConfigurator() {
        return new KafkaConfigurator();
    }

    //
    // KAFKA PRODUCER CONFIGURATION
    //

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.tasks", name = "queue", havingValue = "kafka")
    public ProducerFactory<String, TaskMessage> producerFactory() {
        return kafkaConfigurator.defaultProducerFactory(kafkaProperties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.tasks", name = "queue", havingValue = "kafka")
    public KafkaTemplate<String, TaskMessage> kafkaProducerTemplate(
        ProducerFactory<String, TaskMessage> factory) {
        return kafkaConfigurator.taskMessageKafkaTemplate(factory);
    }

    //
    // KAFKA CONSUMER CONFIGURATION
    //

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.tasks", name = "queue", havingValue = "kafka")
    public ConsumerFactory<String, TaskMessage> consumerFactory() {
        return kafkaConfigurator.defaultConsumerFactory(kafkaProperties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.tasks", name = "queue", havingValue = "kafka")
    KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, TaskMessage>>
        kafkaListenerContainerFactory(ConsumerFactory<String, TaskMessage> consumerFactory) {
        return kafkaConfigurator.defaultListenerContainerFactory(consumerFactory, kafkaProperties);
    }

    //
    // TASK QUEUE CONFIGURATION
    //

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
