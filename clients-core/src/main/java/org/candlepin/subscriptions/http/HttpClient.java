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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
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

    String proxyHost = System.getProperty("http.proxyHost");
    String proxyPort = System.getProperty("http.proxyPort");

    ClientConfiguration clientConfig =
        new ClientConfiguration(ResteasyProviderFactory.getInstance());
    if (clientJson != null) {
      clientConfig.register(clientJson);
    }
    if (isDebugging) {
      clientConfig.register(org.jboss.logging.Logger.class);
    }
    ResteasyClientBuilder clientBuilder =
        ((ResteasyClientBuilder) ClientBuilder.newBuilder().withConfig(clientConfig))
            .hostnameVerifier(serviceProperties.getHostnameVerifier())
            .sslContext(getSslContext(serviceProperties))
            // Bump the max connections so that we don't block on multiple async requests.
            .connectionPoolSize(serviceProperties.getMaxConnections())
            .maxPooledPerRoute(serviceProperties.getMaxConnections())
            .connectionTTL(serviceProperties.getConnectionTtl().getSeconds(), TimeUnit.SECONDS);

    if (proxyHost != null && proxyPort != null) {
      clientBuilder.defaultProxy(proxyHost, Integer.parseInt(proxyPort));
    }

    // Cookie management is disabled by default. Enabling it causes domain mismatch warnings.
    return clientBuilder.build();
  }

  public static SSLContext getSslContext(HttpClientProperties serviceProperties) {
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
        kmf.init(
            loadKeyStore(keystoreResource, keystorePass, KeyStore.getDefaultType()), keystorePass);
        keyManagers = kmf.getKeyManagers();
      }

      if (serviceProperties.getTruststore() != null) {
        var truststoreResource = serviceProperties.getTruststore();
        var truststorePass =
            Objects.requireNonNullElse(serviceProperties.getTruststorePassword(), emptyPass);
        tmf.init(
            loadKeyStore(
                truststoreResource, truststorePass, serviceProperties.getTruststoreType()));
        trustManagers = tmf.getTrustManagers();
      }

      final SSLContext ctx = SSLContext.getInstance(SSLConnectionSocketFactory.TLS);
      ctx.init(keyManagers, trustManagers, null);
      return ctx;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to init SSLContext", e);
    }
  }

  private static KeyStore loadKeyStore(
      Resource keyStoreResource, char[] keyStorePassword, String type) {
    try {
      final KeyStore store =
          KeyStore.getInstance(Optional.ofNullable(type).orElse(KeyStore.getDefaultType()));
      try (var input = keyStoreResource.getInputStream()) {
        store.load(input, keyStorePassword);
      }
      return store;
    } catch (IOException | GeneralSecurityException e) {
      var message = String.format("Error loading Keystore resource %s", keyStoreResource);
      throw new IllegalStateException(message, e);
    }
  }
}
