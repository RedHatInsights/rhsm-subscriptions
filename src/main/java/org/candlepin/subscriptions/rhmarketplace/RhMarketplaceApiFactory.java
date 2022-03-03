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
package org.candlepin.subscriptions.rhmarketplace;

import org.candlepin.subscriptions.http.HttpClient;
import org.candlepin.subscriptions.rhmarketplace.api.resources.RhMarketplaceApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;

/** Factory that produces Red Hat marketplace API clients. */
public class RhMarketplaceApiFactory implements FactoryBean<RhMarketplaceApi> {

  private static Logger log = LoggerFactory.getLogger(RhMarketplaceApiFactory.class);

  private final RhMarketplaceProperties serviceProperties;

  public RhMarketplaceApiFactory(RhMarketplaceProperties serviceProperties) {
    this.serviceProperties = serviceProperties;
  }

  @Override
  public RhMarketplaceApi getObject() throws Exception {
    ApiClient apiClient = new ApiClient();
    apiClient.setHttpClient(
        HttpClient.buildHttpClient(
            serviceProperties, apiClient.getJSON(), apiClient.isDebugging()));
    if (serviceProperties.getUrl() != null) {
      log.info("RH Marketplace service URL: {}", serviceProperties.getUrl());
      apiClient.setBasePath(serviceProperties.getUrl());
    } else {
      log.warn("RH Marketplace service URL not set...");
    }

    return new RhMarketplaceApi(apiClient);
  }

  @Override
  public Class<?> getObjectType() {
    return RhMarketplaceApi.class;
  }
}
