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
package org.candlepin.subscriptions.prometheus.api;

import org.candlepin.subscriptions.http.HttpClient;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.candlepin.subscriptions.prometheus.ApiClient;
import org.candlepin.subscriptions.prometheus.auth.HttpBearerAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.util.StringUtils;

/** Factory that produces prometheus query API clients using configuration. */
public class ApiProviderFactory implements FactoryBean<ApiProvider> {

  private static Logger log = LoggerFactory.getLogger(ApiProviderFactory.class);

  private final HttpClientProperties clientProperties;

  public ApiProviderFactory(HttpClientProperties properties) {
    this.clientProperties = properties;
  }

  @Override
  public ApiProvider getObject() throws Exception {
    if (clientProperties.isUseStub()) {
      log.info("Using prometheus client stub.");
      return new StubApiProvider();
    }

    ApiClient apiClient = new ApiClient();
    apiClient.setHttpClient(
        HttpClient.buildHttpClient(clientProperties, apiClient.getJSON(), apiClient.isDebugging()));

    if (StringUtils.hasText(clientProperties.getToken())) {
      HttpBearerAuth auth = (HttpBearerAuth) apiClient.getAuthentication("bearerAuth");
      auth.setBearerToken(clientProperties.getToken());
    }

    if (StringUtils.hasText(clientProperties.getUrl())) {
      log.info("Prometheus API URL: {}", clientProperties.getUrl());
      apiClient.setBasePath(clientProperties.getUrl());
    } else {
      log.warn("Prometheus API URL not set...");
    }
    return new ApiProviderImpl(apiClient);
  }

  @Override
  public Class<?> getObjectType() {
    return ApiProvider.class;
  }
}
