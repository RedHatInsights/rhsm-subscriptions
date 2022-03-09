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
package org.candlepin.subscriptions.conduit.rhsm.client;

import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.conduit.rhsm.client.resources.RhsmApi;
import org.candlepin.subscriptions.http.HttpClient;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.util.StringUtils;

/**
 * Builds an RhsmApi, which may be a stub, or a normal client, with or without cert auth depending
 * on properties.
 */
@Slf4j
public class RhsmApiFactory implements FactoryBean<RhsmApi> {

  private final RhsmApiProperties properties;

  public RhsmApiFactory(RhsmApiProperties properties) {
    this.properties = properties;
  }

  @Override
  public RhsmApi getObject() throws Exception {
    if (properties.isUseStub()) {
      log.info("Using stub RHSM API client");
      return new StubRhsmApi();
    }

    ApiClient client = Configuration.getDefaultApiClient();
    client.setHttpClient(
        HttpClient.buildHttpClient(properties, client.getJSON(), client.isDebugging()));

    var url = properties.getUrl();
    if (StringUtils.hasText(url)) {
      log.info("RHSM service URL: {}", url);
      client.setBasePath(url);
    } else {
      log.warn("RHSM service URL not set...");
    }

    client.addDefaultHeader("cp-lookup-permissions", "false");
    return new RhsmApi(client);
  }

  @Override
  public Class<?> getObjectType() {
    return RhsmApi.class;
  }
}
