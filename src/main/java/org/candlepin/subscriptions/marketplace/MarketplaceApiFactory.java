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
package org.candlepin.subscriptions.marketplace;

import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.http.HttpClient;
import org.candlepin.subscriptions.marketplace.api.resources.MarketplaceApi;
import org.springframework.beans.factory.FactoryBean;

/** Factory that produces marketplace API clients. */
@Slf4j
public class MarketplaceApiFactory implements FactoryBean<MarketplaceApi> {

  private final MarketplaceProperties properties;

  public MarketplaceApiFactory(MarketplaceProperties properties) {
    this.properties = properties;
  }

  @Override
  public MarketplaceApi getObject() throws Exception {
    if (properties.isUseStub()) {
      throw new UnsupportedOperationException("Marketplace stub not implemented");
    }

    ApiClient client = Configuration.getDefaultApiClient();
    client.setHttpClient(
        HttpClient.buildHttpClient(properties, client.getJSON(), client.isDebugging()));
    client.setBasePath(properties.getUrl());
    return new MarketplaceApi(client);
  }

  @Override
  public Class<?> getObjectType() {
    return MarketplaceApi.class;
  }
}
