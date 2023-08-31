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
package org.candlepin.subscriptions.rhmarketplace;

import com.redhat.swatch.clients.internal.subscriptions.api.client.InternalSubscriptionsApiClientFactory;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.subscription.SubscriptionServiceConfiguration;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;

/** Configuration for the Marketplace integration worker. */
@Profile("rh-marketplace")
@ComponentScan(
    basePackages = "org.candlepin.subscriptions.rhmarketplace",
    // Prevent TestConfiguration annotated classes from being picked up by ComponentScan
    excludeFilters = {
      @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
      @ComponentScan.Filter(
          type = FilterType.CUSTOM,
          classes = AutoConfigurationExcludeFilter.class)
    })
@Import(SubscriptionServiceConfiguration.class)
public class RhMarketplaceWorkerConfiguration {
  @Bean
  @Qualifier("rhMarketplaceRetryTemplate")
  public RetryTemplate marketplaceRetryTemplate(RhMarketplaceProperties properties) {
    return new RetryTemplateBuilder()
        .maxAttempts(properties.getMaxAttempts())
        .exponentialBackoff(
            properties.getBackOffInitialInterval().toMillis(),
            properties.getBackOffMultiplier(),
            properties.getBackOffMaxInterval().toMillis())
        .build();
  }

  @Bean
  @Qualifier("rhMarketplaceTallySummaryConsumerFactory")
  ConsumerFactory<String, TallySummary> tallySummaryConsumerFactory(
      KafkaProperties kafkaProperties) {
    return new DefaultKafkaConsumerFactory<>(
        kafkaProperties.buildConsumerProperties(),
        new StringDeserializer(),
        new JsonDeserializer<>(TallySummary.class));
  }

  @Bean
  @ConditionalOnMissingBean
  KafkaConsumerRegistry kafkaConsumerRegistry() {
    return new KafkaConsumerRegistry();
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, TallySummary>
      kafkaTallySummaryListenerContainerFactory(
          @Qualifier("rhMarketplaceTallySummaryConsumerFactory")
              ConsumerFactory<String, TallySummary> consumerFactory,
          KafkaProperties kafkaProperties,
          KafkaConsumerRegistry registry) {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, TallySummary>();
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
  @Qualifier("rhMarketplaceBillableUsageTopicProperties")
  @ConfigurationProperties(prefix = "rhsm-subscriptions.rh-marketplace.billable-usage.incoming")
  TaskQueueProperties rhmBillableUsageTopicProperties() {
    return new TaskQueueProperties();
  }

  @Bean
  @Qualifier("rhMarketplaceBillableUsageConsumerFactory")
  ConsumerFactory<String, BillableUsage> billableUsageConsumerFactory(
      KafkaProperties kafkaProperties) {
    return new DefaultKafkaConsumerFactory<>(
        kafkaProperties.buildConsumerProperties(),
        new StringDeserializer(),
        new JsonDeserializer<>(BillableUsage.class));
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, BillableUsage>
      kafkaBillableUsageListenerContainerFactory(
          @Qualifier("rhMarketplaceBillableUsageConsumerFactory")
              ConsumerFactory<String, BillableUsage> consumerFactory,
          KafkaProperties kafkaProperties,
          KafkaConsumerRegistry registry) {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, BillableUsage>();
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

  /**
   * Build the BeanFactory implementation ourselves since the docs say "Implementations are not
   * supposed to rely on annotation-driven injection or other reflective facilities."
   *
   * @param properties containing the RhMarketplaceProperties needed by the factory
   * @return a configured RhMarketplaceApiFactory
   */
  @Bean
  public RhMarketplaceApiFactory marketplaceApiFactory(RhMarketplaceProperties properties) {
    return new RhMarketplaceApiFactory(properties);
  }

  @Bean
  public InternalSubscriptionsApiClientFactory internalSubscriptionsClientFactory(
      RhmUsageContextLookupProperties props) {
    return new InternalSubscriptionsApiClientFactory(props);
  }

  @Bean
  @Qualifier("rhmUsageContextLookupRetryTemplate")
  public RetryTemplate rhmUsageContextLookupRetryTemplate(RhmUsageContextLookupProperties props) {
    return new RetryTemplateBuilder()
        .maxAttempts(props.getMaxAttempts())
        .exponentialBackoff(
            props.getBackOffInitialInterval().toMillis(),
            props.getBackOffMultiplier(),
            props.getBackOffMaxInterval().toMillis())
        .build();
  }
}
