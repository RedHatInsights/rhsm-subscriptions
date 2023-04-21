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
package com.redhat.swatch.clients.internal.subscriptions.api.client;

import com.redhat.swatch.clients.internal.subscriptions.api.client.auth.ApiKeyAuth;
import com.redhat.swatch.clients.internal.subscriptions.api.resources.InternalSubscriptionsApi;
import javax.naming.ConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.http.HttpClient;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.util.StringUtils;

@Slf4j
public class InternalSubscriptionsApiClientFactory
    implements FactoryBean<InternalSubscriptionsApi> {

  private final HttpClientProperties properties;

  public InternalSubscriptionsApiClientFactory(HttpClientProperties properties) {
    this.properties = properties;
  }

  @Override
  public InternalSubscriptionsApi getObject() throws Exception {

    ApiClient client = Configuration.getDefaultApiClient();
    client.setHttpClient(
        HttpClient.buildHttpClient(properties, client.getJSON(), client.isDebugging()));

    var url = properties.getUrl();
    if (StringUtils.hasText(url)) {
      log.info("Internal subscriptions service URL: {}", url);
      client.setBasePath(url);
    } else {
      log.warn("Internal subscriptions service URL not set...");
    }
    setupPsk(client);

    return new InternalSubscriptionsApi(client);
  }

  private void setupPsk(ApiClient client) throws ConfigurationException {
    if (!StringUtils.hasText(properties.getPsk())) {
      throw new ConfigurationException("Internal subscriptions API client missing 'psk' property!");
    }
    ((ApiKeyAuth) client.getAuthentication("PskIdentity")).setApiKey(properties.getPsk());
  }

  @Override
  public Class<?> getObjectType() {
    return InternalSubscriptionsApi.class;
  }
}
