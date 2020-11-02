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
package org.candlepin.subscriptions.task.queue.kafka;

import org.candlepin.subscriptions.task.TaskFactory;
import org.candlepin.subscriptions.task.queue.TaskQueue;
import org.candlepin.subscriptions.task.queue.kafka.message.TaskMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

/**
 * A spring configuration that configures the required Beans to set up a KafkaTaskQueue.
 *
 * To enable this queue, run the application with the kafka-queue profile.
 *
 * Use the worker profile on any instances that should process the tasks.
 */
@EnableKafka
@Configuration
@Profile({"kafka-queue", "worker"})
public class KafkaTaskQueueConfiguration {

    @Autowired
    private KafkaConfigurator kafkaConfigurator;

    @Bean
    @Primary
    public KafkaProperties taskQueueKafkaProperties() {
        return new KafkaProperties();
    }

    @Bean
    public KafkaConfigurator kafkaConfigurator() {
        return new KafkaConfigurator();
    }

    @Bean
    public KafkaApplicationListener gracefulShutdown() {
        return new KafkaApplicationListener();
    }

    //
    // KAFKA PRODUCER CONFIGURATION
    //

    @Bean
    @DependsOn("poolScheduler") // this ensures the producer can shut down cleanly when used in a job
    public ProducerFactory<String, TaskMessage> producerFactory(KafkaProperties kafkaProperties) {
        return kafkaConfigurator.defaultProducerFactory(kafkaProperties);
    }

    @Bean
    public KafkaTemplate<String, TaskMessage> kafkaProducerTemplate(
        ProducerFactory<String, TaskMessage> factory) {
        return kafkaConfigurator.taskMessageKafkaTemplate(factory);
    }

    //
    // KAFKA CONSUMER CONFIGURATION
    //

    @Bean
    @Profile("worker")
    public ConsumerFactory<String, TaskMessage> consumerFactory(KafkaProperties kafkaProperties) {
        return kafkaConfigurator.defaultConsumerFactory(kafkaProperties);
    }

    @Bean
    @Profile("worker")
    KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, TaskMessage>>
        kafkaListenerContainerFactory(ConsumerFactory<String, TaskMessage> consumerFactory,
        KafkaProperties kafkaProperties) {
        return kafkaConfigurator.defaultListenerContainerFactory(consumerFactory, kafkaProperties);
    }

    //
    // TASK QUEUE CONFIGURATION
    //

    @Bean
    public TaskQueue kafkaTaskQueue() {
        return new KafkaTaskQueue();
    }

    @Bean
    @Profile("worker")
    public KafkaTaskProcessor taskProcessor(TaskFactory taskFactory) {
        return new KafkaTaskProcessor(taskFactory);
    }
}
