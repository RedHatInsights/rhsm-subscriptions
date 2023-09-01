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
package org.candlepin.subscriptions.metering.service.prometheus.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.candlepin.subscriptions.metering.service.prometheus.MetricProperties;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusService;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryBuilder;
import org.candlepin.subscriptions.prometheus.api.ApiProvider;
import org.candlepin.subscriptions.prometheus.api.ApiProviderFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Defines all of the beans required to use the Prometheus metrics service. */
public class PrometheusServiceConfiguration {

  @Bean
  @Qualifier("prometheus")
  @ConfigurationProperties(prefix = "rhsm-subscriptions.metering.prometheus.client")
  public HttpClientProperties prometheusApiClientProperties() {
    return new HttpClientProperties();
  }

  @Bean
  public ApiProviderFactory apiProviderFactory(
      @Qualifier("prometheus") HttpClientProperties clientProps) {
    return new ApiProviderFactory(clientProps);
  }

  @Bean
  public MetricProperties metricProperties() {
    return new MetricProperties();
  }

  @Bean
  public PrometheusService prometheusService(ApiProvider provider, ObjectMapper objectMapper) {
    return new PrometheusService(provider, objectMapper);
  }

  @Bean
  QueryBuilder queryBuilder(MetricProperties props) {
    return new QueryBuilder(props);
  }
}
