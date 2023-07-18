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
package org.candlepin.subscriptions.http;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClientEngine;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.springframework.core.io.Resource;

/** Utility class for customizing HTTP clients used by API clients. */
@Slf4j
public class HttpClient {
  private HttpClient() {
    throw new IllegalStateException("Utility class; should never be instantiated!");
  }

  /**
   * Customize the creation of the HTTP client the API will be using. Delegates to {@link
   * HttpClient#buildHttpClient(HttpClientProperties, Object, boolean)}
   *
   * @param serviceProperties client configuration properties
   * @return Client with customized connection settings
   */
  public static Client buildHttpClient(HttpClientProperties serviceProperties) {
    return HttpClient.buildHttpClient(serviceProperties, null, false);
  }

  /**
   * Customize the creation of the HTTP client the API will be using.
   *
   * @param serviceProperties client configuration properties
   * @param clientJson API client configuration json
   * @param isDebugging whether the API client is debugging
   * @return Client with customized connection settings
   */
  public static Client buildHttpClient(
      HttpClientProperties serviceProperties, Object clientJson, boolean isDebugging) {
    HttpClientBuilder apacheBuilder = HttpClientBuilder.create();

    apacheBuilder.setSSLHostnameVerifier(serviceProperties.getHostnameVerifier());

    // Bump the max connections so that we don't block on multiple async requests to the service.
    apacheBuilder.setMaxConnPerRoute(serviceProperties.getMaxConnections());
    apacheBuilder.setMaxConnTotal(serviceProperties.getMaxConnections());
    apacheBuilder.setConnectionTimeToLive(
        serviceProperties.getConnectionTtl().getSeconds(), TimeUnit.SECONDS);
    apacheBuilder.setSSLContext(getSslContext(serviceProperties));

    // Ignore cookies. Not ignoring them results in error messages in the logs due to mismatches
    // in domains.
    RequestConfig cookieConfig =
        RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).build();
    apacheBuilder.setDefaultRequestConfig(cookieConfig);

    CloseableHttpClient httpClient = apacheBuilder.build();
    ClientHttpEngine engine = ApacheHttpClientEngine.create(httpClient);

    ClientConfiguration clientConfig =
        new ClientConfiguration(ResteasyProviderFactory.getInstance());
    if (clientJson != null) {
      clientConfig.register(clientJson);
    }
    if (isDebugging) {
      clientConfig.register(org.jboss.logging.Logger.class);
    }
    ClientBuilder clientBuilder = ClientBuilder.newBuilder().withConfig(clientConfig);

    return ((ResteasyClientBuilder) clientBuilder).httpEngine(engine).build();
  }

  private static SSLContext getSslContext(HttpClientProperties serviceProperties) {
    try {
      KeyManager[] keyManagers = null;
      TrustManager[] trustManagers = null;

      KeyManagerFactory kmf =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      TrustManagerFactory tmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

      char[] emptyPass = "".toCharArray();

      if (serviceProperties.usesClientAuth()) {
        var keystoreResource = serviceProperties.getKeystore();
        var keystorePass =
            Objects.requireNonNullElse(serviceProperties.getKeystorePassword(), emptyPass);
        kmf.init(loadKeyStore(keystoreResource, keystorePass), keystorePass);
        keyManagers = kmf.getKeyManagers();
      }

      if (serviceProperties.providesTruststore()) {
        var truststoreResource = serviceProperties.getTruststore();
        var truststorePass =
            Objects.requireNonNullElse(serviceProperties.getTruststorePassword(), emptyPass);
        tmf.init(loadKeyStore(truststoreResource, truststorePass));
        trustManagers = tmf.getTrustManagers();
      }

      final SSLContext ctx = SSLContext.getInstance("TLSv1.2");
      ctx.init(keyManagers, trustManagers, null);
      return ctx;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to init SSLContext", e);
    }
  }

  private static KeyStore loadKeyStore(Resource keyStoreResource, char[] keyStorePassword) {
    try {
      final KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
      store.load(keyStoreResource.getInputStream(), keyStorePassword);
      return store;
    } catch (IOException | GeneralSecurityException e) {
      var message = String.format("Error loading Keystore resource %s", keyStoreResource);
      throw new IllegalStateException(message, e);
    }
  }
}
