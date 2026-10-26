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
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

/**
 * Sticky-fallback invoker and client cache for a {@link ClientConfig}. Builds typed clients via a
 * service-specific factory; builds optional RESTEasy HTTP clients from {@link ClientConnection}
 * truststore settings.
 *
 * @param <CLIENT> generated (or hand-written) API client type
 */
@Slf4j
public class ClientProxy<CLIENT> {

  private final ClientConfig config;
  private final Function<ClientConnection, CLIENT> clientFactory;
  private final ConcurrentMap<ClientConnection, CLIENT> clients = new ConcurrentHashMap<>();

  public ClientProxy(ClientConfig config, Function<ClientConnection, CLIENT> clientFactory) {
    this.config = Objects.requireNonNull(config, "config");
    this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory");
  }

  public ClientConfig config() {
    return config;
  }

  /**
   * Runs {@code call} against the current endpoint. On connectivity or auth failure, falls back
   * once when possible and retries.
   */
  public void invoke(Call<? super CLIENT> call) throws Exception {
    ClientConnection selection = config.current();
    try {
      call.run(clientFor(selection));
    } catch (Exception ex) {
      if (!shouldFallback(ex)) {
        throw ex;
      }

      boolean switched = config.tryFallback();
      ClientConnection next = config.current();
      // Retry if this call switched, or another call already advanced sticky selection.
      if (!switched && selection.equals(next)) {
        throw ex;
      }

      log.warn(
          "{} {} unavailable, falling back to {}", config.getName(), selection.id(), next.id(), ex);
      call.run(clientFor(next));
    }
  }

  /** Returns a cached client for {@code connection}, creating one on first use. */
  public CLIENT clientFor(ClientConnection connection) {
    if (connection == null || !connection.hasUri()) {
      throw new IllegalStateException(
          "Client '%s' endpoint URI is not configured".formatted(config.getName()));
    }
    return clients.computeIfAbsent(connection, this::buildClient);
  }

  private CLIENT buildClient(ClientConnection connection) {
    log.info(
        "Building {} client id={} authenticated={}",
        config.getName(),
        connection.id(),
        connection.authenticated());
    return clientFactory.apply(connection);
  }

  /**
   * Builds a RESTEasy {@link Client}, applying the connection truststore when present so callers
   * can register request filters (e.g. Bearer auth) on a real client instance.
   */
  public static Client buildHttpClient(ClientConnection connection, Duration readTimeout) {
    var httpClientBuilder = ResteasyClientBuilder.newBuilder();
    if (readTimeout != null && !readTimeout.isZero() && !readTimeout.isNegative()) {
      httpClientBuilder =
          httpClientBuilder.readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }
    KeyStore trustStore = loadTrustStore(connection);
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

  private static KeyStore loadTrustStore(ClientConnection connection) {
    String path = connection.trustStorePath();
    if (path == null || path.isBlank()) {
      return null;
    }
    String type =
        connection.trustStoreType() == null || connection.trustStoreType().isBlank()
            ? KeyStore.getDefaultType()
            : connection.trustStoreType();
    try {
      KeyStore keyStore = KeyStore.getInstance(type);
      try (InputStream input = Files.newInputStream(toPath(path))) {
        char[] password = connection.trustStorePassword();
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

  private static boolean shouldFallback(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof ConnectException
          || current instanceof UnknownHostException
          || current instanceof NoRouteToHostException
          || current instanceof AuthFailureException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  /** Operation against a client built for the current {@link ClientConnection}. */
  @FunctionalInterface
  public interface Call<C> {
    void run(C client) throws Exception;
  }
}
