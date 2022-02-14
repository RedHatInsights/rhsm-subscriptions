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

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.candlepin.subscriptions.x509.X509ClientConfiguration;
import org.candlepin.subscriptions.x509.X509HttpClientBuilder;
import org.jboss.logging.Logger;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.factory.ApacheHttpClient4EngineFactory;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.springframework.beans.factory.FactoryBean;

public class RhsmX509ApiFactory implements FactoryBean<ApiClient> {

  private final RhsmApiProperties apiProperties;
  private X509ClientConfiguration x509Config;

  public RhsmX509ApiFactory(RhsmApiProperties apiProperties) {
    this.x509Config = apiProperties.getX509Config();
    this.apiProperties = apiProperties;
  }

  @Override
  public ApiClient getObject() throws Exception {
    ApiClient apiClient = Configuration.getDefaultApiClient();
    HttpClientBuilder httpClientBuilder =
        new X509HttpClientBuilder(x509Config).createHttpClientBuilderForTls();

    // Bump the max connections so that our task processors do not
    // block waiting to connect to RHSM.

    // note that these are essentially the same, since we're only hitting a single hostname
    httpClientBuilder.setMaxConnPerRoute(apiProperties.getMaxConnections());
    httpClientBuilder.setMaxConnTotal(apiProperties.getMaxConnections());

    // Necessary to prevent error messages about cookie domain differences
    RequestConfig cookieConfig =
        RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).build();
    httpClientBuilder.setDefaultRequestConfig(cookieConfig);

    HttpClient httpClient = httpClientBuilder.build();

    // We've now constructed a basic Apache HttpClient.  Now we wire that in to RestEasy.  There is
    // a lot of overlap in the names and in the classes across Apache's http-components, Resteasy,
    // and the JAX-RS API.

    ClientHttpEngine engine = ApacheHttpClient4EngineFactory.create(httpClient);

    ClientConfiguration clientConfig =
        new ClientConfiguration(ResteasyProviderFactory.getInstance());

    // These config registrations are copied from the generated ApiClient's buildHttpClient method
    clientConfig.register(apiClient.getJSON());
    if (apiClient.isDebugging()) {
      clientConfig.register(Logger.class);
    }

    ClientBuilder clientBuilder = ClientBuilder.newBuilder().withConfig(clientConfig);

    Client client = ((ResteasyClientBuilder) clientBuilder).httpEngine(engine).build();

    apiClient.setHttpClient(client);
    return apiClient;
  }

  @Override
  public Class<?> getObjectType() {
    return ApiClient.class;
  }
}
