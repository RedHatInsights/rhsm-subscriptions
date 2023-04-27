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
package org.candlepin.subscriptions.subscription;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Profile("capacity-ingress")
@ComponentScan(
    basePackages = {
      "org.candlepin.subscriptions.subscription",
      "org.candlepin.subscriptions.product"
    })
@Import(SubscriptionServiceConfiguration.class)
@Configuration
public class SubscriptionWorkerConfiguration {

  @Bean
  ConsumerFactory<String, SyncSubscriptionsTask> syncSubscriptionsConsumerFactory(
      KafkaProperties kafkaProperties) {
    return new DefaultKafkaConsumerFactory<>(
        kafkaProperties.buildConsumerProperties(),
        new StringDeserializer(),
        new JsonDeserializer<>(SyncSubscriptionsTask.class));
  }

  @Bean
  ConsumerFactory<String, PruneSubscriptionsTask> pruneSubscriptionsConsumerFactory(
      KafkaProperties kafkaProperties) {
    return new DefaultKafkaConsumerFactory<>(
        kafkaProperties.buildConsumerProperties(),
        new StringDeserializer(),
        new JsonDeserializer<>(PruneSubscriptionsTask.class));
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, SyncSubscriptionsTask>
      subscriptionSyncListenerContainerFactory(
          ConsumerFactory<String, SyncSubscriptionsTask> consumerFactory,
          KafkaProperties kafkaProperties,
          KafkaConsumerRegistry registry) {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, SyncSubscriptionsTask>();
    factory.setConsumerFactory(consumerFactory);
    // Concurrency should be set to the number of partitions for the target topic.
    factory.setConcurrency(kafkaProperties.getListener().getConcurrency());
    if (kafkaProperties.getListener().getIdleEventInterval() != null) {
      factory
          .getContainerProperties()
          .setIdleEventInterval(kafkaProperties.getListener().getIdleEventInterval().toMillis());
    }
    // hack to track the Kafka consumers, so SeekableKafkaConsumer can commit when needed
    factory.getContainerProperties().setConsumerRebalanceListener(registry);
    return factory;
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, PruneSubscriptionsTask>
      subscriptionPruneListenerContainerFactory(
          ConsumerFactory<String, PruneSubscriptionsTask> consumerFactory,
          KafkaProperties kafkaProperties,
          KafkaConsumerRegistry registry) {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, PruneSubscriptionsTask>();
    factory.setConsumerFactory(consumerFactory);
    // Concurrency should be set to the number of partitions for the target topic.
    factory.setConcurrency(kafkaProperties.getListener().getConcurrency());
    if (kafkaProperties.getListener().getIdleEventInterval() != null) {
      factory
          .getContainerProperties()
          .setIdleEventInterval(kafkaProperties.getListener().getIdleEventInterval().toMillis());
    }
    // hack to track the Kafka consumers, so SeekableKafkaConsumer can commit when needed
    factory.getContainerProperties().setConsumerRebalanceListener(registry);
    return factory;
  }
}
