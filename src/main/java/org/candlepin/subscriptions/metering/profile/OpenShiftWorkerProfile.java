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

import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.files.ProductMappingConfiguration;
import org.candlepin.subscriptions.files.TagProfile;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMeteringController;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMetricsProperties;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusService;
import org.candlepin.subscriptions.metering.service.prometheus.config.PrometheusServiceConfiguration;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryBuilder;
import org.candlepin.subscriptions.metering.task.MeteringTasksConfiguration;
import org.candlepin.subscriptions.security.OptInController;
import org.candlepin.subscriptions.task.queue.TaskConsumerConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * Defines the beans for the openshift-metering-worker profile. By default, the worker will also
 * load the openshift metering JMX endpoints.
 *
 * @see MeteringJmxProfile
 */
@EnableRetry
@Configuration
@Profile("openshift-metering-worker")
@Import({
  ProductMappingConfiguration.class,
  PrometheusServiceConfiguration.class,
  TaskConsumerConfiguration.class,
  MeteringTasksConfiguration.class
})
public class OpenShiftWorkerProfile {

  @Bean(name = "openshiftMetricRetryTemplate")
  public RetryTemplate openshiftRetryTemplate(PrometheusMetricsProperties metricProperties) {
    RetryTemplate retryTemplate = new RetryTemplate();

    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
    retryPolicy.setMaxAttempts(metricProperties.getOpenshift().getMaxAttempts());
    retryTemplate.setRetryPolicy(retryPolicy);

    ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
    backOffPolicy.setMaxInterval(metricProperties.getOpenshift().getBackOffMaxInterval());
    backOffPolicy.setInitialInterval(metricProperties.getOpenshift().getBackOffInitialInterval());
    backOffPolicy.setMultiplier(metricProperties.getOpenshift().getBackOffMultiplier());
    retryTemplate.setBackOffPolicy(backOffPolicy);

    return retryTemplate;
  }

  @Bean
  PrometheusMeteringController getController(
      ApplicationClock clock,
      PrometheusMetricsProperties mProps,
      PrometheusService service,
      QueryBuilder queryBuilder,
      EventController eventController,
      @Qualifier("openshiftMetricRetryTemplate") RetryTemplate openshiftRetryTemplate,
      OptInController optInController,
      TagProfile tagProfile) {
    return new PrometheusMeteringController(
        clock,
        mProps,
        service,
        queryBuilder,
        eventController,
        openshiftRetryTemplate,
        optInController,
        tagProfile);
  }
}
