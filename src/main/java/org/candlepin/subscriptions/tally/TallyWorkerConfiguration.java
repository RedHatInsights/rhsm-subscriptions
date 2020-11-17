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
package org.candlepin.subscriptions.tally;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.cloudigrade.ConcurrentApiFactory;
import org.candlepin.subscriptions.files.ProductIdMappingConfiguration;
import org.candlepin.subscriptions.files.ProductIdToProductsMapSource;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.candlepin.subscriptions.inventory.db.InventoryDataSourceConfiguration;
import org.candlepin.subscriptions.jmx.JmxBeansConfiguration;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.tally.files.RoleToProductsMapSource;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.queue.TaskConsumer;
import org.candlepin.subscriptions.task.queue.TaskConsumerConfiguration;
import org.candlepin.subscriptions.task.queue.TaskConsumerFactory;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for the "worker" profile.
 *
 * This profile acts as a worker node for Tally snapshot creation, as well as serving admin JMX APIs.
 */
@EnableRetry
@Configuration
@Profile("worker")
@Import({TallyTaskQueueConfiguration.class, TaskConsumerConfiguration.class,
    ProductIdMappingConfiguration.class, InventoryDataSourceConfiguration.class, JmxBeansConfiguration.class})
@ComponentScan(basePackages = {
    "org.candlepin.subscriptions.cloudigrade",
    "org.candlepin.subscriptions.event",
    "org.candlepin.subscriptions.inventory.db",
    "org.candlepin.subscriptions.jmx",
    "org.candlepin.subscriptions.tally"
})
public class TallyWorkerConfiguration {
    @Bean
    @Qualifier("cloudigrade")
    @ConfigurationProperties(prefix = "rhsm-subscriptions.cloudigrade")
    public HttpClientProperties cloudigradeServiceProperties() {
        return new HttpClientProperties();
    }

    @Bean
    public ConcurrentApiFactory concurrentApiFactory(@Qualifier("cloudigrade") HttpClientProperties props) {
        return new ConcurrentApiFactory(props);
    }

    @Bean
    public FactNormalizer factNormalizer(ApplicationProperties applicationProperties,
        ProductIdToProductsMapSource productIdToProductsMapSource,
        RoleToProductsMapSource productToRolesMapSource,
        ApplicationClock clock) throws IOException {

        return new FactNormalizer(applicationProperties, productIdToProductsMapSource,
                productToRolesMapSource, clock);
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

    @Bean(name = "cloudigradeRetryTemplate")
    public RetryTemplate cloudigradeRetryTemplate(ApplicationProperties applicationProperties) {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(applicationProperties.getCloudigradeMaxAttempts());

        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(2000L);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        return retryTemplate;
    }

    @Bean(name = "applicableProducts")
    public Set<String> applicableProducts(ProductIdToProductsMapSource productIdToProductsMapSource,
        RoleToProductsMapSource roleToProductsMapSource) throws IOException {

        Set<String> products = new HashSet<>();
        productIdToProductsMapSource.getValue().values().forEach(products::addAll);
        roleToProductsMapSource.getValue().values().forEach(products::addAll);
        return products;
    }

    @Bean
    TallyTaskFactory taskFactory() {
        return new TallyTaskFactory();
    }

    @Bean
    @Qualifier("tallyTaskConsumer")
    public TaskConsumer taskProcessor(
        @Qualifier("tallyTaskQueueProperties") TaskQueueProperties taskQueueProperties,
        TaskConsumerFactory<? extends TaskConsumer> taskConsumerFactory, TallyTaskFactory taskFactory) {

        return taskConsumerFactory.createTaskConsumer(taskFactory, taskQueueProperties);
    }
}
