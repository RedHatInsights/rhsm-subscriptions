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

import org.candlepin.subscriptions.jobs.JobProperties;
import org.candlepin.subscriptions.metering.job.MeteringJob;
import org.candlepin.subscriptions.metering.service.prometheus.MetricProperties;
import org.candlepin.subscriptions.metering.service.prometheus.config.PrometheusServiceConfiguration;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.metering.task.MeteringTasksConfiguration;
import org.candlepin.subscriptions.spring.JobRunner;
import org.candlepin.subscriptions.task.queue.TaskProducerConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;

/** Defines the beans for the metering-job profile. */
@Configuration
@Profile("metering-job")
@Import({
  PrometheusServiceConfiguration.class,
  MeteringTasksConfiguration.class,
  TaskProducerConfiguration.class
})
public class MeteringJobProfile {

  @Bean
  JobProperties jobProperties() {
    return new JobProperties();
  }

  @Bean
  @Qualifier("meteringJobRetryTemplate")
  public RetryTemplate meteringJobRetryTemplate(MetricProperties properties) {
    return new RetryTemplateBuilder()
        .maxAttempts(properties.getJobMaxAttempts())
        .exponentialBackoff(
            properties.getJobBackOffInitialInterval().toMillis(),
            properties.getBackOffMultiplier(),
            properties.getJobBackOffMaxInterval().toMillis())
        .build();
  }

  @Bean
  MeteringJob meteringJob(
      PrometheusMetricsTaskManager tasks,
      MetricProperties metricProperties,
      @Qualifier("meteringJobRetryTemplate") RetryTemplate retryTemplate) {
    return new MeteringJob(tasks, metricProperties, retryTemplate);
  }

  @Bean
  JobRunner jobRunner(MeteringJob job, ApplicationContext applicationContext) {
    return new JobRunner(job, applicationContext);
  }
}
