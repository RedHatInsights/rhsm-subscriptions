/*
 * Copyright (c) 2020 Red Hat, Inc.
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
package org.candlepin.subscriptions.metering.worker;


import org.candlepin.subscriptions.db.AccountListSource;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.candlepin.subscriptions.metering.MeteringController;
import org.candlepin.subscriptions.metering.TaskQueueConfiguration;
import org.candlepin.subscriptions.metering.service.PrometheusService;
import org.candlepin.subscriptions.metering.service.PrometheusServicePropeties;
import org.candlepin.subscriptions.metering.tasks.MeteringTaskFactory;
import org.candlepin.subscriptions.metering.tasks.MetricsTaskManager;
import org.candlepin.subscriptions.prometheus.api.ApiProvider;
import org.candlepin.subscriptions.prometheus.api.ApiProviderFactory;
import org.candlepin.subscriptions.task.TaskFactory;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.queue.TaskConsumer;
import org.candlepin.subscriptions.task.queue.TaskConsumerConfiguration;
import org.candlepin.subscriptions.task.queue.TaskConsumerFactory;
import org.candlepin.subscriptions.task.queue.TaskProducerConfiguration;
import org.candlepin.subscriptions.task.queue.TaskQueue;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for the "worker" profile.
 *
 * This profile acts as a worker node for Tally snapshot creation, as well as serving admin JMX APIs.
 */
@Configuration
@Profile("metering-worker")
@Import({TaskQueueConfiguration.class, TaskProducerConfiguration.class, TaskConsumerConfiguration.class})
@ComponentScan(basePackages = {
    "org.candlepin.subscriptions.metering.jmx"
})
public class MeteringWorkerConfiguration {

    @Bean
    @Qualifier("prometheus")
    @ConfigurationProperties(prefix = "rhsm-subscriptions.metering.prometheus.client")
    public HttpClientProperties prometheusApiClientProperties() {
        return new HttpClientProperties();
    }

    @Bean
    public ApiProviderFactory apiProviderFactory(@Qualifier("prometheus") HttpClientProperties clientProps) {
        return new ApiProviderFactory(clientProps);
    }

    @Bean
    public PrometheusServicePropeties servicePropeties() {
        return new PrometheusServicePropeties();
    }

    @Bean
    public PrometheusService prometheusService(PrometheusServicePropeties props, ApiProvider provider) {
        return new PrometheusService(props, provider);
    }

    @Bean
    public MetricsTaskManager metricsTaskManager(TaskQueue queue,
        @Qualifier("meteringTaskQueueProperties") TaskQueueProperties queueProps,
        AccountListSource accounts) {
        return new MetricsTaskManager(queue, queueProps, accounts);
    }

    @Bean
    MeteringController getController(PrometheusService service) {
        return new MeteringController(service);
    }

    @Bean
    @Qualifier("meteringTaskFactory")
    TaskFactory meteringTaskFactory(MeteringController controller) {
        return new MeteringTaskFactory(controller);
    }

    @Bean
    @Qualifier("meteringTaskConsumer")
    public TaskConsumer meteringTaskProcessor(
        @Qualifier("meteringTaskQueueProperties") TaskQueueProperties taskQueueProperties,
        TaskConsumerFactory<? extends TaskConsumer> taskConsumerFactory,
        @Qualifier("meteringTaskFactory") TaskFactory taskFactory) {

        return taskConsumerFactory.createTaskConsumer(taskFactory, taskQueueProperties);
    }
}
