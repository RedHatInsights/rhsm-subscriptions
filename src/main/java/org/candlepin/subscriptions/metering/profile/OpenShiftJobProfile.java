/*
 * Copyright (c) 2021 Red Hat, Inc.
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
import org.candlepin.subscriptions.metering.job.OpenShiftMeteringJob;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMetricsProperties;
import org.candlepin.subscriptions.metering.service.prometheus.config.PrometheusServiceConfiguration;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.metering.task.OpenShiftTasksConfiguration;
import org.candlepin.subscriptions.spring.JobRunner;
import org.candlepin.subscriptions.task.queue.TaskProducerConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Defines the beans for the openshift-metering-job profile.
 */
@Configuration
@Profile("openshift-metering-job")
@Import({
    PrometheusServiceConfiguration.class,
    OpenShiftTasksConfiguration.class,
    TaskProducerConfiguration.class
})
public class OpenShiftJobProfile {

    @Bean
    JobProperties jobProperties() {
        return new JobProperties();
    }

    @Bean
    OpenShiftMeteringJob openshiftMeteringJob(PrometheusMetricsTaskManager tasks, ApplicationClock clock,
        PrometheusMetricsProperties metricProperties) {
        return new OpenShiftMeteringJob(tasks, clock, metricProperties);
    }

    @Bean
    JobRunner jobRunner(OpenShiftMeteringJob job, ApplicationContext applicationContext) {
        return new JobRunner(job, applicationContext);
    }

}
