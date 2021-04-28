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
package org.candlepin.subscriptions.cloudigrade;

import org.candlepin.subscriptions.cloudigrade.api.resources.ConcurrentApi;
import org.candlepin.subscriptions.http.HttpClient;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;

/** Factory that produces inventory service clients using configuration. */
public class ConcurrentApiFactory implements FactoryBean<ConcurrentApi> {

  private static Logger log = LoggerFactory.getLogger(ConcurrentApiFactory.class);

  private final HttpClientProperties serviceProperties;

  public ConcurrentApiFactory(HttpClientProperties serviceProperties) {
    this.serviceProperties = serviceProperties;
  }

  @Override
  public ConcurrentApi getObject() throws Exception {
    if (serviceProperties.isUseStub()) {
      log.info("Using stub cloudigrade client");
      return new StubConcurrentApi();
    }
    ApiClient apiClient = Configuration.getDefaultApiClient();
    apiClient.setHttpClient(
        HttpClient.buildHttpClient(
            serviceProperties, apiClient.getJSON(), apiClient.isDebugging()));
    if (serviceProperties.getUrl() != null) {
      log.info("Cloudigrade service URL: {}", serviceProperties.getUrl());
      apiClient.setBasePath(serviceProperties.getUrl());
    } else {
      log.warn("Cloudigrade service URL not set...");
    }
    return new ConcurrentApi(apiClient);
  }

  @Override
  public Class<?> getObjectType() {
    return ConcurrentApi.class;
  }
}
