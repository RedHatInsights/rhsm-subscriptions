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

import org.candlepin.subscriptions.task.JsonTaskMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

/**
 * Kakfa consumer configuration for all kafka consumers.
 *
 * <p>Note that this class doesn't actually create any consumers - to do that you must create a bean
 * of type KafkaTaskProcessor.
 */
@Configuration
@Profile("kafka-queue")
@Import(KafkaConfiguration.class)
public class KafkaTaskConsumerConfiguration {
  KafkaConfigurator kafkaConfigurator;

  @Autowired
  public KafkaTaskConsumerConfiguration(KafkaConfigurator kafkaConfigurator) {
    this.kafkaConfigurator = kafkaConfigurator;
  }

  @Bean
  public KafkaApplicationListener gracefulShutdown() {
    return new KafkaApplicationListener();
  }

  @Bean
  public ConsumerFactory<String, JsonTaskMessage> consumerFactory(KafkaProperties kafkaProperties) {
    return kafkaConfigurator.defaultConsumerFactory(kafkaProperties);
  }

  @Bean
  KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, JsonTaskMessage>>
      kafkaListenerContainerFactory(
          ConsumerFactory<String, JsonTaskMessage> consumerFactory,
          KafkaProperties kafkaProperties) {

    return kafkaConfigurator.defaultListenerContainerFactory(consumerFactory, kafkaProperties);
  }
}
