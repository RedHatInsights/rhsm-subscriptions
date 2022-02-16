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
package org.candlepin.subscriptions.user;

import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.http.HttpClient;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.candlepin.subscriptions.user.api.resources.AccountApi;
import org.springframework.beans.factory.config.AbstractFactoryBean;

/** Factory bean for AccountApi. */
@Slf4j
public class AccountApiFactory extends AbstractFactoryBean<AccountApi> {

  private final HttpClientProperties properties;

  public AccountApiFactory(HttpClientProperties properties) {
    this.properties = properties;
  }

  @Nonnull
  @Override
  protected AccountApi createInstance() {
    log.info("Subscription client config: {}", properties);

    if (properties.isUseStub()) {
      return new StubAccountApi();
    }

    ApiClient client = Configuration.getDefaultApiClient();
    client.setHttpClient(
        HttpClient.buildHttpClient(properties, client.getJSON(), client.isDebugging()));
    client.setBasePath(properties.getUrl());
    return new AccountApi(client);
  }

  @Override
  public Class<?> getObjectType() {
    return AccountApi.class;
  }
}
