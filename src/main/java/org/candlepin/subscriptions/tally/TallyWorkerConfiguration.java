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
import java.util.concurrent.Executor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.export.ExportSubscriptionConfiguration;
import org.candlepin.subscriptions.inventory.db.InventoryDataSourceConfiguration;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.EnabledOrgsRequest;
import org.candlepin.subscriptions.json.EnabledOrgsResponse;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.product.ProductConfiguration;
import org.candlepin.subscriptions.tally.billing.BillingProducerConfiguration;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.tally.facts.ProductNormalizer;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.queue.TaskConsumer;
import org.candlepin.subscriptions.task.queue.TaskConsumerConfiguration;
import org.candlepin.subscriptions.task.queue.inmemory.ExecutorTaskProcessor;
import org.candlepin.subscriptions.task.queue.inmemory.ExecutorTaskQueueConsumerFactory;
import org.candlepin.subscriptions.task.queue.kafka.KafkaTaskConsumerFactory;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Configuration for the "worker" profile.
 *
 * <p>This profile acts as a worker node for Tally snapshot creation, as well as serving internal
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
  ExportSubscriptionConfiguration.class
})
@ComponentScan(
    basePackages = {
      "org.candlepin.subscriptions.event",
      "org.candlepin.subscriptions.inventory.db",
      "org.candlepin.subscriptions.tally",
      "org.candlepin.subscriptions.retention"
    },
    // Prevent TestConfiguration annotated classes from being picked up by ComponentScan
    excludeFilters = {
      @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
      @ComponentScan.Filter(
          type = FilterType.CUSTOM,
          classes = AutoConfigurationExcludeFilter.class)
    })
public class TallyWorkerConfiguration {

  public static final String ENABLED_ORGS_TOPIC_PROPERTIES_BEAN = "enabledOrgsTopicProperties";
  public static final String ENABLED_ORGS_CONSUMER_FACTORY_BEAN = "enabledOrgsConsumerFactory";
  public static final String ENABLED_ORGS_KAFKA_LISTENER_CONTAINER_FACTORY_BEAN =
      "kafkaEnabledOrgsListenerContainerFactory";

  @Bean
  public FactNormalizer factNormalizer(
      ApplicationProperties applicationProperties,
      ApplicationClock clock,
      ProductNormalizer productNormalizer) {
    return new FactNormalizer(applicationProperties, clock, productNormalizer);
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

  @Bean
  @Profile("kafka-queue")
  public TaskConsumer tallyTaskProcessor(
      @Qualifier("tallyTaskQueueProperties") TaskQueueProperties taskQueueProperties,
      KafkaTaskConsumerFactory taskConsumerFactory,
      TallyTaskFactory taskFactory) {

    return taskConsumerFactory.createTaskConsumer(taskFactory, taskQueueProperties);
  }

  @Bean
  public ExecutorTaskProcessor syncTaskProcessorForTallyTaskProcessor(
      @Qualifier("tallyTaskQueueProperties") TaskQueueProperties taskQueueProperties,
      ExecutorTaskQueueConsumerFactory taskConsumerFactory,
      TallyTaskFactory taskFactory) {

    return taskConsumerFactory.createTaskConsumer(taskFactory, taskQueueProperties);
  }

  @Bean
  public MetricUsageCollector metricUsageCollector(
      AccountServiceInventoryRepository accountServiceInventoryRepository,
      ApplicationClock clock,
      HostRepository hostRepository,
      TallySnapshotRepository tallySnapshotRepository) {
    return new MetricUsageCollector(
        accountServiceInventoryRepository, clock, hostRepository, tallySnapshotRepository);
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

  @Bean
  public ProducerFactory<String, String> eventDeadLetterProducerFactory(
      KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
    DefaultKafkaProducerFactory<String, String> factory =
        new DefaultKafkaProducerFactory<>(getProducerProperties(kafkaProperties));
    /*
    Use our customized ObjectMapper. Notably, the spring-kafka default ObjectMapper writes dates as
    timestamps, which produces messages not compatible with JSON-B deserialization.
     */
    factory.setValueSerializer(new JsonSerializer<>(objectMapper));
    return factory;
  }

  @Bean
  public KafkaTemplate<String, String> eventDeadLetterKafkaTemplate(
      ProducerFactory<String, String> eventDeadLetterProducerFactory) {
    return new KafkaTemplate<>(eventDeadLetterProducerFactory);
  }

  @Bean
  @Qualifier("eventDeadLetterKafkaErrorHandler")
  public DefaultErrorHandler eventDeadLetterKafkaErrorHandler(
      KafkaTemplate<String, String> eventDeadLetterKafkaTemplate,
      @Qualifier("serviceInstanceDeadLetterTopicProperties")
          TaskQueueProperties taskQueueProperties) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            eventDeadLetterKafkaTemplate,
            (r, e) -> new TopicPartition(taskQueueProperties.getTopic(), r.partition()));
    return new DefaultErrorHandler(
        recoverer,
        new FixedBackOff(
            taskQueueProperties.getRetryBackOffMillis(), taskQueueProperties.getRetryAttempts()));
  }

  @Bean
  @Qualifier("serviceInstanceConsumerFactory")
  ConsumerFactory<String, String> serviceInstanceConsumerFactory(
      KafkaProperties kafkaProperties,
      @Qualifier("serviceInstanceTopicProperties") TaskQueueProperties taskQueueProperties) {
    var props = kafkaProperties.buildConsumerProperties(null);
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, taskQueueProperties.getMaxPollRecords());
    return new DefaultKafkaConsumerFactory<>(
        props, new StringDeserializer(), new StringDeserializer());
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String>
      kafkaServiceInstanceListenerContainerFactory(
          @Qualifier("serviceInstanceConsumerFactory")
              ConsumerFactory<String, String> consumerFactory,
          KafkaProperties kafkaProperties,
          KafkaConsumerRegistry registry,
          @Qualifier("eventDeadLetterKafkaErrorHandler")
              DefaultErrorHandler deadLetterErrorHandler) {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(consumerFactory);
    factory.setBatchListener(true);
    // Concurrency should be set to the number of partitions for the target topic.
    factory.setConcurrency(kafkaProperties.getListener().getConcurrency());
    if (kafkaProperties.getListener().getIdleEventInterval() != null) {
      factory
          .getContainerProperties()
          .setIdleEventInterval(kafkaProperties.getListener().getIdleEventInterval().toMillis());
    }
    // hack to track the Kafka consumers, so SeekableKafkaConsumer can commit when needed
    factory.getContainerProperties().setConsumerRebalanceListener(registry);
    factory.setCommonErrorHandler(deadLetterErrorHandler);
    return factory;
  }

  @Bean
  @Qualifier("serviceInstanceTopicProperties")
  @ConfigurationProperties(prefix = "rhsm-subscriptions.service-instance-ingress.incoming")
  public TaskQueueProperties serviceInstanceTopicProperties() {
    return new TaskQueueProperties();
  }

  @Bean
  @Qualifier("serviceInstanceDeadLetterTopicProperties")
  @ConfigurationProperties(
      prefix = "rhsm-subscriptions.service-instance-ingress-dead-letter.outgoing")
  public TaskQueueProperties serviceInstanceDeadLetterTopicProperties() {
    return new TaskQueueProperties();
  }

  @Bean
  @Qualifier("billableUsageDeadLetterTopicProperties")
  @ConfigurationProperties(prefix = "rhsm-subscriptions.billable-usage-dead-letter.incoming")
  public TaskQueueProperties billableUsageDeadLetterTopicProperties() {
    return new TaskQueueProperties();
  }

  @Bean
  @Qualifier(ENABLED_ORGS_TOPIC_PROPERTIES_BEAN)
  @ConfigurationProperties(prefix = "rhsm-subscriptions.enabled-orgs.incoming")
  public TaskQueueProperties enabledOrgsTopicProperties() {
    return new TaskQueueProperties();
  }

  @Bean
  @Qualifier(ENABLED_ORGS_CONSUMER_FACTORY_BEAN)
  ConsumerFactory<String, EnabledOrgsRequest> enabledOrgsConsumerFactory(
      ObjectMapper objectMapper,
      KafkaProperties kafkaProperties,
      @Qualifier(ENABLED_ORGS_TOPIC_PROPERTIES_BEAN)
          TaskQueueProperties enabledOrgsTopicProperties) {
    var props = kafkaProperties.buildConsumerProperties(null);
    props.put(
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG, enabledOrgsTopicProperties.getMaxPollRecords());
    var jsonDeserializer = new JsonDeserializer<>(EnabledOrgsRequest.class, objectMapper, false);
    var factory =
        new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), jsonDeserializer);
    factory.setValueDeserializer(jsonDeserializer);
    return factory;
  }

  @Bean
  public KafkaTemplate<String, EnabledOrgsResponse> enabledOrgsKafkaTemplate(
      KafkaProperties kafkaProperties) {
    return new KafkaTemplate<>(
        new DefaultKafkaProducerFactory<>(getProducerProperties(kafkaProperties)));
  }

  @Bean
  @Qualifier(ENABLED_ORGS_KAFKA_LISTENER_CONTAINER_FACTORY_BEAN)
  ConcurrentKafkaListenerContainerFactory<String, EnabledOrgsRequest>
      kafkaEnabledOrgsListenerContainerFactory(
          @Qualifier(ENABLED_ORGS_CONSUMER_FACTORY_BEAN)
              ConsumerFactory<String, EnabledOrgsRequest> consumerFactory,
          KafkaProperties kafkaProperties,
          KafkaConsumerRegistry registry) {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, EnabledOrgsRequest>();
    factory.setConsumerFactory(consumerFactory);
    factory.setBatchListener(true);
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
  public JsonDeserializer<BillableUsage> billableUsageJsonDeserializer(ObjectMapper objectMapper) {
    return new JsonDeserializer<>(BillableUsage.class, objectMapper, false);
  }

  @Bean
  @Qualifier("billableUsageDeadLetterConsumerFactory")
  ConsumerFactory<String, BillableUsage> billableUsageDeadLetterConsumerFactory(
      JsonDeserializer<BillableUsage> jsonDeserializer,
      KafkaProperties kafkaProperties,
      @Qualifier("billableUsageDeadLetterTopicProperties")
          TaskQueueProperties taskQueueProperties) {
    var props = kafkaProperties.buildConsumerProperties(null);
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, taskQueueProperties.getMaxPollRecords());
    var factory =
        new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), jsonDeserializer);
    factory.setValueDeserializer(jsonDeserializer);
    return factory;
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, BillableUsage>
      kafkaBillableUsageDeadLetterListenerContainerFactory(
          @Qualifier("billableUsageDeadLetterConsumerFactory")
              ConsumerFactory<String, BillableUsage> consumerFactory,
          KafkaProperties kafkaProperties,
          KafkaConsumerRegistry registry) {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, BillableUsage>();
    factory.setConsumerFactory(consumerFactory);
    factory.setBatchListener(true);
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
