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

import org.candlepin.subscriptions.metering.jmx.MeteringJmxBean;
import org.candlepin.subscriptions.metering.service.prometheus.MetricProperties;
import org.candlepin.subscriptions.metering.service.prometheus.config.PrometheusServiceConfiguration;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.metering.task.MeteringTasksConfiguration;
import org.candlepin.subscriptions.task.queue.TaskProducerConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Defines the beans for the metering-jmx profile.
 *
 * <p>NOTE: The openshift-metering-worker profile will also load the beans defined here since, by
 * default the JMX endpoint will be enabled with the worker.
 */
@Configuration
@Profile({"openshift-metering-worker", "metering-jmx"})
@Import({
  PrometheusServiceConfiguration.class,
  MeteringTasksConfiguration.class,
  TaskProducerConfiguration.class
})
public class MeteringJmxProfile {

  @Bean
  MeteringJmxBean meteringJmxBean(
      ApplicationClock clock,
      PrometheusMetricsTaskManager taskManager,
      MetricProperties metricProperties) {
    return new MeteringJmxBean(clock, taskManager, metricProperties);
  }
}
