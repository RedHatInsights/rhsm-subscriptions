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
package org.candlepin.subscriptions.metering.profile;

import static org.candlepin.subscriptions.task.queue.kafka.KafkaTaskProducerConfiguration.getProducerProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.metering.service.prometheus.MetricProperties;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusEventsProducer;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMeteringController;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusService;
import org.candlepin.subscriptions.metering.service.prometheus.config.PrometheusServiceConfiguration;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryBuilder;
import org.candlepin.subscriptions.metering.task.MeteringTasksConfiguration;
import org.candlepin.subscriptions.security.OptInController;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.queue.TaskConsumerConfiguration;
import org.candlepin.subscriptions.task.queue.TaskProducerConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.SpanGenerator;
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
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/** Defines the beans for the openshift-metering-worker profile. */
@EnableRetry
@Configuration
@Profile("openshift-metering-worker")
@Import({
  PrometheusServiceConfiguration.class,
  TaskConsumerConfiguration.class,
  TaskProducerConfiguration.class,
  MeteringTasksConfiguration.class
})
@ComponentScan(
    basePackages = {
      "org.candlepin.subscriptions.metering.api",
      "org.candlepin.subscriptions.metering.retention"
    },
    // Prevent TestConfiguration annotated classes from being picked up by ComponentScan
    excludeFilters = {
      @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
      @ComponentScan.Filter(
          type = FilterType.CUSTOM,
          classes = AutoConfigurationExcludeFilter.class)
    })
public class OpenShiftWorkerProfile {

  @Bean(name = "openshiftMetricRetryTemplate")
  public RetryTemplate openshiftRetryTemplate(MetricProperties metricProperties) {
    RetryTemplate retryTemplate = new RetryTemplate();

    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
    retryPolicy.setMaxAttempts(metricProperties.getMaxAttempts());
    retryTemplate.setRetryPolicy(retryPolicy);

    ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
    backOffPolicy.setMaxInterval(metricProperties.getBackOffMaxInterval());
    backOffPolicy.setInitialInterval(metricProperties.getBackOffInitialInterval());
    backOffPolicy.setMultiplier(metricProperties.getBackOffMultiplier());
    retryTemplate.setBackOffPolicy(backOffPolicy);

    return retryTemplate;
  }

  @Bean
  public ProducerFactory<String, Event> prometheusUsageProducerFactory(
      KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
    DefaultKafkaProducerFactory<String, Event> factory =
        new DefaultKafkaProducerFactory<>(getProducerProperties(kafkaProperties));
    /*
    Use our customized ObjectMapper. Notably, the spring-kafka default ObjectMapper writes dates as
    timestamps, which produces messages not compatible with JSON-B deserialization.
     */
    factory.setValueSerializer(new JsonSerializer<>(objectMapper));
    return factory;
  }

  @Bean
  public KafkaTemplate<String, Event> prometheusUsageKafkaTemplate(
      @Qualifier("prometheusUsageProducerFactory")
          ProducerFactory<String, Event> prometheusUsageProducerFactory) {
    return new KafkaTemplate<>(prometheusUsageProducerFactory);
  }

  @Bean
  @Qualifier("eventsTopicProperties")
  @ConfigurationProperties(prefix = "rhsm-subscriptions.metering.events")
  public TaskQueueProperties eventsTopicProperties() {
    return new TaskQueueProperties();
  }

  @Bean
  public PrometheusEventsProducer prometheusEventsProducer(
      @Qualifier("eventsTopicProperties") TaskQueueProperties eventsTopicProperties,
      KafkaTemplate<String, Event> prometheusUsageKafkaTemplate) {
    return new PrometheusEventsProducer(eventsTopicProperties, prometheusUsageKafkaTemplate);
  }

  @Bean(name = "meteringBatchIdGenerator")
  public SpanGenerator prometheusSpanGenerator() {
    return new SpanGenerator("metering-batch-id");
  }

  @SuppressWarnings("java:S107")
  @Bean
  PrometheusMeteringController getController(
      ApplicationClock clock,
      MetricProperties mProps,
      PrometheusService service,
      QueryBuilder queryBuilder,
      PrometheusEventsProducer prometheusEventsProducer,
      @Qualifier("openshiftMetricRetryTemplate") RetryTemplate openshiftRetryTemplate,
      OptInController optInController,
      @Qualifier("meteringBatchIdGenerator") SpanGenerator spanGenerator) {
    return new PrometheusMeteringController(
        clock,
        mProps,
        service,
        queryBuilder,
        prometheusEventsProducer,
        openshiftRetryTemplate,
        optInController,
        spanGenerator);
  }
}
