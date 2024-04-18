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
package org.candlepin.subscriptions.export;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Import({ExportClientConfiguration.class})
public class ExportSubscriptionConfiguration {

  public static final String SUBSCRIPTION_EXPORT_QUALIFIER = "subscriptionExport";
  public static final String EXPORT_CONSUMER_FACTORY_QUALIFIER = "exportConsumerFactory";

  @Bean(name = SUBSCRIPTION_EXPORT_QUALIFIER)
  @ConfigurationProperties(prefix = "rhsm-subscriptions.export.tasks")
  TaskQueueProperties subscriptionExportProperties() {
    return new TaskQueueProperties();
  }

  @Bean(EXPORT_CONSUMER_FACTORY_QUALIFIER)
  ConsumerFactory<String, String> exportConsumerFactory(KafkaProperties kafkaProperties) {
    return new DefaultKafkaConsumerFactory<>(
        kafkaProperties.buildConsumerProperties(null),
        new StringDeserializer(),
        new StringDeserializer());
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String> exportListenerContainerFactory(
      @Qualifier(EXPORT_CONSUMER_FACTORY_QUALIFIER) ConsumerFactory<String, String> consumerFactory,
      KafkaProperties kafkaProperties,
      KafkaConsumerRegistry registry,
      DefaultErrorHandler subscriptionExportKafkaErrorHandler) {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
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

    factory.setCommonErrorHandler(subscriptionExportKafkaErrorHandler);

    return factory;
  }

  @Bean
  DefaultErrorHandler subscriptionExportKafkaErrorHandler(
      @Qualifier(SUBSCRIPTION_EXPORT_QUALIFIER) TaskQueueProperties subscriptionExportProperties) {
    return new DefaultErrorHandler(
        new FixedBackOff(
            subscriptionExportProperties.getRetryBackOffMillis(),
            subscriptionExportProperties.getRetryAttempts()));
  }
}
