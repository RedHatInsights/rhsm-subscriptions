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
package org.candlepin.subscriptions.tally;

import static org.candlepin.subscriptions.task.queue.kafka.KafkaTaskProducerConfiguration.getProducerProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.inventory.db.InventoryDataSourceConfiguration;
import org.candlepin.subscriptions.jmx.JmxBeansConfiguration;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.product.ProductConfiguration;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.tally.billing.BillingProducerConfiguration;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.queue.TaskConsumer;
import org.candlepin.subscriptions.task.queue.TaskConsumerConfiguration;
import org.candlepin.subscriptions.task.queue.TaskConsumerFactory;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for the "worker" profile.
 *
 * <p>This profile acts as a worker node for Tally snapshot creation, as well as serving admin JMX
 * APIs.
 */
@EnableRetry
@Configuration
@EnableAsync
@Profile("worker")
@Import({
  TallyTaskQueueConfiguration.class,
  TaskConsumerConfiguration.class,
  BillingProducerConfiguration.class,
  InventoryDataSourceConfiguration.class,
  ProductConfiguration.class,
  JmxBeansConfiguration.class
})
@ComponentScan(
    basePackages = {
      "org.candlepin.subscriptions.event",
      "org.candlepin.subscriptions.inventory.db",
      "org.candlepin.subscriptions.jmx",
      "org.candlepin.subscriptions.tally",
      "org.candlepin.subscriptions.retention"
    })
public class TallyWorkerConfiguration {

  @Bean
  public FactNormalizer factNormalizer(
      ApplicationProperties applicationProperties, TagProfile tagProfile, ApplicationClock clock) {
    return new FactNormalizer(applicationProperties, tagProfile, clock);
  }

  @Bean(name = "collectorRetryTemplate")
  public RetryTemplate collectorRetryTemplate() {
    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
    retryPolicy.setMaxAttempts(4);

    FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
    backOffPolicy.setBackOffPeriod(2000L);

    RetryTemplate retryTemplate = new RetryTemplate();
    retryTemplate.setRetryPolicy(retryPolicy);
    retryTemplate.setBackOffPolicy(backOffPolicy);
    return retryTemplate;
  }

  @Bean
  @ConfigurationProperties(prefix = "rhsm-subscriptions.tally-summary-producer")
  public TallySummaryProperties tallySummaryProperties() {
    return new TallySummaryProperties();
  }

  @Bean(name = "tallySummaryKafkaRetryTemplate")
  public RetryTemplate tallySummaryKafkaRetryTemplate(TallySummaryProperties properties) {
    return new RetryTemplateBuilder()
        .maxAttempts(properties.getMaxAttempts())
        .exponentialBackoff(
            properties.getBackOffInitialInterval().toMillis(),
            properties.getBackOffMultiplier(),
            properties.getBackOffMaxInterval().toMillis())
        .build();
  }

  @Bean(name = "applicableProducts")
  public Set<String> applicableProducts(TagProfile tagProfile) {
    Set<String> products = new HashSet<>();
    Map<Integer, Set<String>> productToProductIds =
        tagProfile.getEngProductIdToSwatchProductIdsMap();
    productToProductIds.values().forEach(products::addAll);

    Map<String, Set<String>> roleToProducts = tagProfile.getRoleToTagLookup();
    roleToProducts.values().forEach(products::addAll);
    return products;
  }

  @Bean
  @Qualifier("tallyTaskConsumer")
  public TaskConsumer tallyTaskProcessor(
      @Qualifier("tallyTaskQueueProperties") TaskQueueProperties taskQueueProperties,
      TaskConsumerFactory<? extends TaskConsumer> taskConsumerFactory,
      TallyTaskFactory taskFactory) {

    return taskConsumerFactory.createTaskConsumer(taskFactory, taskQueueProperties);
  }

  @Bean
  public MetricUsageCollector metricUsageCollector(
      TagProfile tagProfile,
      AccountServiceInventoryRepository accountServiceInventoryRepository,
      EventController eventController,
      ApplicationClock clock) {
    return new MetricUsageCollector(
        tagProfile, accountServiceInventoryRepository, eventController, clock);
  }

  @Bean
  public ProducerFactory<String, TallySummary> tallySummaryProducerFactory(
      KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
    DefaultKafkaProducerFactory<String, TallySummary> factory =
        new DefaultKafkaProducerFactory<>(getProducerProperties(kafkaProperties));
    /*
    Use our customized ObjectMapper. Notably, the spring-kafka default ObjectMapper writes dates as
    timestamps, which produces messages not compatible with JSON-B deserialization.
     */
    factory.setValueSerializer(new JsonSerializer<>(objectMapper));
    return factory;
  }

  @Bean
  public KafkaTemplate<String, TallySummary> tallySummaryKafkaTemplate(
      ProducerFactory<String, TallySummary> tallySummaryProducerFactory) {
    return new KafkaTemplate<>(tallySummaryProducerFactory);
  }

  @Bean(name = "purgeTallySnapshotsJobExecutor")
  public Executor getPurgeSnapshotsJobExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("purge-tally-snapshots-");
    // Ensure that we can only have one task running.
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(0);
    executor.initialize();
    return executor;
  }

  @Bean(name = "purgeRemittancesJobExecutor")
  public Executor getPurgeRemittancesJobExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("purge-remittances-");
    // Ensure that we can only have one task running.
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(0);
    executor.initialize();
    return executor;
  }
}
