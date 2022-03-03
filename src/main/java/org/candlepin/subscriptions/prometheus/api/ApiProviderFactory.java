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

import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.http.HttpClient;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.candlepin.subscriptions.prometheus.ApiClient;
import org.candlepin.subscriptions.prometheus.Configuration;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.util.StringUtils;

/** Factory that produces prometheus query API clients using configuration. */
@Slf4j
public class ApiProviderFactory implements FactoryBean<ApiProvider> {

  private final HttpClientProperties properties;

  public ApiProviderFactory(HttpClientProperties properties) {
    this.properties = properties;
  }

  @Override
  public ApiProvider getObject() throws Exception {
    if (properties.isUseStub()) {
      log.info("Using stub prometheus query API client");
      return new StubApiProvider();
    }

    ApiClient client = Configuration.getDefaultApiClient();
    client.setHttpClient(
        HttpClient.buildHttpClient(properties, client.getJSON(), client.isDebugging()));

    var url = properties.getUrl();
    if (StringUtils.hasText(url)) {
      log.info("Prometheus API service URL: {}", url);
      client.setBasePath(url);
    } else {
      log.warn("Prometheus API service URL not set...");
    }

    return new ApiProviderImpl(client);
  }

  @Override
  public Class<?> getObjectType() {
    return ApiProvider.class;
  }
}
