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
package com.redhat.swatch.contracts.client;

import com.redhat.swatch.contracts.api.resources.CapacityApi;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.http.HttpClient;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.util.StringUtils;

@Slf4j
public class CapacityApiFactory implements FactoryBean<CapacityApi> {

  private final HttpClientProperties properties;
  private final IdentityHeaderProvider idHeaderProvider;

  public CapacityApiFactory(
      HttpClientProperties properties, IdentityHeaderProvider idHeaderProvider) {
    this.properties = properties;
    this.idHeaderProvider = idHeaderProvider;
  }

  @Override
  public CapacityApi getObject() throws Exception {
    ApiClient client = new IdentityHeaderApiClient(idHeaderProvider);
    client.setHttpClient(
        HttpClient.buildHttpClient(properties, client.getJSON(), client.isDebugging()));

    var url = properties.getUrl();
    if (StringUtils.hasText(url)) {
      log.info("Capacity API URL: {}", url);
      client.setBasePath(url);
    } else {
      log.warn("Capacity API URL not set...");
    }

    return new CapacityApi(client);
  }

  @Override
  public Class<?> getObjectType() {
    return CapacityApi.class;
  }
}
