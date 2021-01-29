/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.metering.service.prometheus.config;

import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMeteringController;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMetricPropeties;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusService;
import org.candlepin.subscriptions.prometheus.api.ApiProvider;
import org.candlepin.subscriptions.prometheus.api.ApiProviderFactory;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * Defines all of the beans required to use the Prometheus metrics service.
 */
public class PrometheusServiceConfiguration {

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
    public PrometheusMetricPropeties metricProperties() {
        return new PrometheusMetricPropeties();
    }

    @Bean
    public PrometheusService prometheusService(ApiProvider provider) {
        return new PrometheusService(provider);
    }

    @Bean
    PrometheusMeteringController getController(ApplicationClock clock, PrometheusMetricPropeties mProps,
        PrometheusService service, EventController eventController,
        @Qualifier("prometheusMeteringRetryTemplate") RetryTemplate openshiftRetryTemplate) {
        return new PrometheusMeteringController(clock, mProps, service, eventController,
            openshiftRetryTemplate);
    }

    @Bean
    EventController eventController(EventRecordRepository repo) {
        return new EventController(repo);
    }

    @Bean(name = "prometheusMeteringRetryTemplate")
    public RetryTemplate openshiftRetryTemplate(PrometheusMetricPropeties metricProperties) {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(metricProperties.getMaxAttempts());

        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(2000L);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        return retryTemplate;
    }

}
