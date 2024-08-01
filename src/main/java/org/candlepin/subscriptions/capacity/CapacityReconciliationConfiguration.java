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
package org.candlepin.subscriptions.capacity;

import static org.candlepin.subscriptions.task.queue.kafka.KafkaTaskProducerConfiguration.getProducerProperties;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.candlepin.subscriptions.subscription.PruneSubscriptionsTask;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * Configuration that should be imported for any component that needs to be able to use capacity
 * reconciliation logic.
 */
@Configuration
@ComponentScan(
    basePackages = "org.candlepin.subscriptions.capacity",
    // Prevent TestConfiguration annotated classes from being picked up by ComponentScan
    excludeFilters = {
      @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
      @ComponentScan.Filter(
          type = FilterType.CUSTOM,
          classes = AutoConfigurationExcludeFilter.class)
    })
public class CapacityReconciliationConfiguration {

  @Bean
  public ProducerFactory<String, PruneSubscriptionsTask> pruneSubscriptionsProducerFactory(
      KafkaProperties kafkaProperties) {
    return new DefaultKafkaProducerFactory<>(getProducerProperties(kafkaProperties));
  }

  @Bean
  public KafkaTemplate<String, PruneSubscriptionsTask> pruneSubscriptionsKafkaTemplate(
      ProducerFactory<String, PruneSubscriptionsTask> pruneSubscriptionsProducerFactory) {
    return new KafkaTemplate<>(pruneSubscriptionsProducerFactory);
  }

  @Bean
  ConsumerFactory<String, PruneSubscriptionsTask> pruneSubscriptionsConsumerFactory(
      KafkaProperties kafkaProperties) {
    return new DefaultKafkaConsumerFactory<>(
        kafkaProperties.buildConsumerProperties(null),
        new StringDeserializer(),
        new JsonDeserializer<>(PruneSubscriptionsTask.class));
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
