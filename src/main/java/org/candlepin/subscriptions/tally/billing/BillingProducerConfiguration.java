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
package org.candlepin.subscriptions.tally.billing;

import static org.candlepin.subscriptions.task.queue.kafka.KafkaTaskProducerConfiguration.getProducerProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.contracts.client.ContractsApiFactory;
import java.util.Map;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.KafkaTransientDataAccessErrorHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonDelegatingErrorHandler;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
public class BillingProducerConfiguration {

  @Bean
  @ConfigurationProperties(prefix = "rhsm-subscriptions.billing-producer")
  public BillingProducerProperties billingProducerProperties() {
    return new BillingProducerProperties();
  }

  @Bean
  @Qualifier("billingProducerTallySummaryTopicProperties")
  @ConfigurationProperties(prefix = "rhsm-subscriptions.billing-producer.incoming")
  public TaskQueueProperties billingProducerTallySummaryTopicProperties() {
    return new TaskQueueProperties();
  }

  @Bean
  @Qualifier("billingProducerTallySummaryConsumerFactory")
  ConsumerFactory<String, TallySummary> billingProducerTallySummaryConsumerFactory(
      KafkaProperties kafkaProperties) {
    return new DefaultKafkaConsumerFactory<>(
        kafkaProperties.buildConsumerProperties(null),
        new StringDeserializer(),
        new JsonDeserializer<>(TallySummary.class));
  }

  @Bean
  CommonErrorHandler billingProducerErrorHandler(BillingProducerProperties properties) {
    var backOff = new ExponentialBackOffWithMaxRetries(properties.getMaxAttempts());
    backOff.setInitialInterval(properties.getBackOffInitialInterval().toMillis());
    backOff.setMultiplier(properties.getBackOffMultiplier());
    backOff.setMaxInterval(properties.getBackOffMaxInterval().toMillis());

    CommonDelegatingErrorHandler errorHandler =
        new CommonDelegatingErrorHandler(new DefaultErrorHandler(backOff));
    errorHandler.setErrorHandlers(
        Map.of(TransientDataAccessException.class, new KafkaTransientDataAccessErrorHandler()));
    return errorHandler;
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, TallySummary>
      billingProducerKafkaTallySummaryListenerContainerFactory(
          @Qualifier("billingProducerTallySummaryConsumerFactory")
              ConsumerFactory<String, TallySummary> consumerFactory,
          CommonErrorHandler billingProducerErrorHandler,
          KafkaProperties kafkaProperties,
          KafkaConsumerRegistry registry) {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, TallySummary>();
    factory.setConsumerFactory(consumerFactory);
    // Concurrency should be set to the number of partitions for the target topic.
    factory.setConcurrency(kafkaProperties.getListener().getConcurrency());
    factory.setCommonErrorHandler(billingProducerErrorHandler);
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
  @ConfigurationProperties(prefix = "rhsm-subscriptions.billing-producer.outgoing")
  public TaskQueueProperties billableUsageTopicProperties() {
    return new TaskQueueProperties();
  }

  @Bean
  public ProducerFactory<String, BillableUsage> billableUsageProducerFactory(
      KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
    DefaultKafkaProducerFactory<String, BillableUsage> factory =
        new DefaultKafkaProducerFactory<>(getProducerProperties(kafkaProperties));
    /*
    Use our customized ObjectMapper. Notably, the spring-kafka default ObjectMapper writes dates as
    timestamps, which produces messages not compatible with JSON-B deserialization.
     */
    factory.setValueSerializer(new JsonSerializer<>(objectMapper));
    return factory;
  }

  @Bean
  public KafkaTemplate<String, BillableUsage> billableUsageKafkaTemplate(
      @Qualifier("billableUsageProducerFactory")
          ProducerFactory<String, BillableUsage> billableUsageProducerFactory) {
    return new KafkaTemplate<>(billableUsageProducerFactory);
  }

  @Qualifier("contractsClientProperties")
  @ConfigurationProperties(prefix = "rhsm-subscriptions.billing-producer.contracts")
  @Bean
  public ContractsClientProperties contractsClientProperties() {
    return new ContractsClientProperties();
  }

  @Bean
  public ContractsApiFactory contractsApiFactory(
      @Qualifier("contractsClientProperties") HttpClientProperties clientProperties) {
    return new ContractsApiFactory(clientProperties);
  }
}
