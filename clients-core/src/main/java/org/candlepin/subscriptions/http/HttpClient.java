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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.factory.ApacheHttpClient4EngineFactory;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

/** Utility class for customizing HTTP clients used by API clients. */
@Slf4j
public class HttpClient {

  private HttpClient() {
    throw new IllegalStateException("Utility class; should never be instantiated!");
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

    // Bump the max connections so that we don't block on multiple async requests
    // to the service.
    apacheBuilder.setMaxConnPerRoute(serviceProperties.getMaxConnections());
    apacheBuilder.setMaxConnTotal(serviceProperties.getMaxConnections());
    apacheBuilder.setSSLContext(getSslContext(serviceProperties));

    org.apache.http.client.HttpClient httpClient = apacheBuilder.build();

    ClientHttpEngine engine = ApacheHttpClient4EngineFactory.create(httpClient);

    ClientConfiguration clientConfig =
        new ClientConfiguration(ResteasyProviderFactory.getInstance());
    if (clientJson != null) {
      clientConfig.register(clientJson);
    }
    if (isDebugging) {
      clientConfig.register(org.jboss.logging.Logger.class);
    }
    ClientBuilder clientBuilder = ClientBuilder.newBuilder().withConfig(clientConfig);

    // setup cookie handling
    RequestConfig cookieConfig =
        RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).build();
    apacheBuilder.setDefaultRequestConfig(cookieConfig);

    return ((ResteasyClientBuilder) clientBuilder).httpEngine(engine).build();
  }

  private static SSLContext getSslContext(HttpClientProperties serviceProperties) {
    final File keyStoreFile = serviceProperties.getKeystore();
    final File trustStoreFile = serviceProperties.getTruststore();
    final char[] keyStorePassword;
    final char[] trustStorePassword;
    if (serviceProperties.getKeystorePassword() == null) {
      keyStorePassword = "".toCharArray();
    } else {
      keyStorePassword = serviceProperties.getKeystorePassword();
    }
    if (serviceProperties.getTruststorePassword() == null) {
      trustStorePassword = "".toCharArray();
    } else {
      trustStorePassword = serviceProperties.getTruststorePassword();
    }

    try {
      KeyManager[] keyManagers = null;
      TrustManager[] trustManagers = null;
      final KeyManagerFactory kmf =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      final TrustManagerFactory tmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      if (keyStoreFile != null && keyStoreFile.exists() && keyStoreFile.canRead()) {
        kmf.init(loadKeyStore(keyStoreFile, keyStorePassword), keyStorePassword);
        keyManagers = kmf.getKeyManagers();
      }
      if (trustStoreFile != null && trustStoreFile.exists() && trustStoreFile.canRead()) {
        tmf.init(loadKeyStore(trustStoreFile, trustStorePassword));
        trustManagers = tmf.getTrustManagers();
      }
      final SSLContext ctx = SSLContext.getInstance("TLSv1.2");
      ctx.init(keyManagers, trustManagers, null);
      return ctx;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to init SSLContext", e);
    }
  }

  private static KeyStore loadKeyStore(File keyStoreFile, char[] keyStorePassword) {
    try (final BufferedInputStream bufferedInputStream =
        new BufferedInputStream(new FileInputStream(keyStoreFile))) {
      final KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
      store.load(bufferedInputStream, keyStorePassword);
      return store;
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException(
          String.format(
              "Keystore file %s could not be accessed! %s",
              keyStoreFile.getAbsolutePath(), e.getMessage()));
    }
  }
}
