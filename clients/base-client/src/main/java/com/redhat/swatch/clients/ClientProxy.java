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
package com.redhat.swatch.clients;

import jakarta.ws.rs.client.Client;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

/**
 * Lazy client holder for a {@link ClientConfig}. Builds a typed client via a service-specific
 * factory; builds optional RESTEasy HTTP clients from truststore settings on the config.
 *
 * @param <CLIENT> generated (or hand-written) API client type
 */
@Slf4j
public class ClientProxy<CLIENT> {

  private final ClientConfig config;
  private final Function<ClientConfig, CLIENT> clientFactory;
  private volatile CLIENT client;

  public ClientProxy(ClientConfig config, Function<ClientConfig, CLIENT> clientFactory) {
    this.config = Objects.requireNonNull(config, "config");
    this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory");
  }

  public ClientConfig config() {
    return config;
  }

  /** Runs {@code call} against the configured endpoint. */
  public void invoke(Call<? super CLIENT> call) throws Exception {
    call.run(client());
  }

  /** Returns the cached client, creating one on first use. */
  public CLIENT client() {
    CLIENT existing = client;
    if (existing != null) {
      return existing;
    }
    synchronized (this) {
      if (client == null) {
        if (!config.hasUri()) {
          throw new IllegalStateException(
              "Client '%s' endpoint URI is not configured".formatted(config.getName()));
        }
        log.info(
            "Building {} client authenticated={}", config.getName(), config.getAuthenticated());
        client = clientFactory.apply(config);
      }
      return client;
    }
  }

  /**
   * Builds a RESTEasy {@link Client}, applying the config truststore when present so callers can
   * register request filters (e.g. Bearer auth) on a real client instance.
   */
  public static Client buildHttpClient(ClientConfig config) {
    var httpClientBuilder = ResteasyClientBuilder.newBuilder();
    Duration readTimeout = config.getReadTimeout();
    if (readTimeout != null && !readTimeout.isZero() && !readTimeout.isNegative()) {
      httpClientBuilder =
          httpClientBuilder.readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }
    KeyStore trustStore = loadTrustStore(config);
    if (trustStore != null) {
      try {
        TrustManagerFactory trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        httpClientBuilder = httpClientBuilder.sslContext(sslContext);
      } catch (Exception e) {
        throw new IllegalStateException("Failed to build RESTEasy client for truststore", e);
      }
    }

    return httpClientBuilder.build();
  }

  private static KeyStore loadTrustStore(ClientConfig config) {
    String path = config.getTrustStorePath();
    if (path == null || path.isBlank()) {
      return null;
    }
    String type =
        config.getTrustStoreType() == null || config.getTrustStoreType().isBlank()
            ? KeyStore.getDefaultType()
            : config.getTrustStoreType();
    try {
      KeyStore keyStore = KeyStore.getInstance(type);
      try (InputStream input = Files.newInputStream(toPath(path))) {
        char[] password = config.getTrustStorePassword();
        keyStore.load(input, password == null || password.length == 0 ? null : password);
      }
      return keyStore;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load truststore from " + path, e);
    }
  }

  private static Path toPath(String path) {
    if (path.startsWith("file:")) {
      return Path.of(URI.create(path));
    }
    return Path.of(path);
  }

  /** Operation against the configured client. */
  @FunctionalInterface
  public interface Call<C> {
    void run(C client) throws Exception;
  }
}
