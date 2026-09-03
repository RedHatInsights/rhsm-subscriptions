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
package com.redhat.swatch.component.tests.utils;

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.protocol.HttpContext;

/**
 * Shared Apache HttpClient for RestAssured so HTTP keep-alive reuses localhost sockets. Fabric8
 * LocalPortForward opens a Kubernetes websocket per TCP connect; a new client per request leaks
 * native threads.
 *
 * <p>RestAssured 6 still requires {@code AbstractHttpClient} ({@code DefaultHttpClient}). That
 * client synchronizes {@code execute}, so Kafka Bridge keep-alive polling uses a second instance
 * and does not block test traffic.
 *
 * <p>RestAssured does not consume the Apache entity unless a body matcher is set. Buffering the
 * entity inside {@code execute()} returns the lease to the pool.
 *
 * <p>Fabric8 port-forwards close idle TCP without Apache noticing. Unbounded keep-alive then reuses
 * a dead socket ({@code NoHttpResponseException}). Keep-alive is capped and idle connections are
 * closed on that same interval.
 */
@SuppressWarnings("deprecation")
public final class PooledRestAssured {

  private static final int MAX_TOTAL = 64;
  private static final int MAX_PER_ROUTE = 8;
  private static final int KEEP_ALIVE_MAX_TOTAL = 8;
  private static final int KEEP_ALIVE_MAX_PER_ROUTE = 2;
  private static final int SO_TIMEOUT_MS = 30_000;
  private static final int CONNECT_TIMEOUT_MS = 10_000;
  private static final long CONN_MANAGER_TIMEOUT_MS = 10_000L;
  static final int KEEP_ALIVE_MS = 3_000;
  private static final long IDLE_TIMEOUT_SECONDS = 3L;
  private static final List<PoolingClientConnectionManager> MANAGERS = new CopyOnWriteArrayList<>();
  private static final ScheduledExecutorService EVICTOR =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread thread = new Thread(r, "pooled-restassured-evictor");
            thread.setDaemon(true);
            return thread;
          });
  private static final RestAssuredConfig CONFIG = buildConfig(MAX_TOTAL, MAX_PER_ROUTE);
  private static final RestAssuredConfig KEEP_ALIVE_CONFIG =
      buildConfig(KEEP_ALIVE_MAX_TOTAL, KEEP_ALIVE_MAX_PER_ROUTE);

  static {
    startEvictor();
    install();
  }

  private PooledRestAssured() {}

  public static void install() {
    RestAssured.config = CONFIG;
  }

  public static RestAssuredConfig config() {
    return CONFIG;
  }

  public static RestAssuredConfig keepAliveConfig() {
    return KEEP_ALIVE_CONFIG;
  }

  static int leasedCount() {
    int leased = 0;
    for (PoolingClientConnectionManager manager : MANAGERS) {
      leased += manager.getTotalStats().getLeased();
    }
    return leased;
  }

  private static RestAssuredConfig buildConfig(int maxTotal, int maxPerRoute) {
    PoolingClientConnectionManager connections = new PoolingClientConnectionManager();
    connections.setMaxTotal(maxTotal);
    connections.setDefaultMaxPerRoute(maxPerRoute);
    MANAGERS.add(connections);
    DefaultHttpClient httpClient = new DefaultHttpClient(connections);
    httpClient.getParams().setIntParameter(CoreConnectionPNames.SO_TIMEOUT, SO_TIMEOUT_MS);
    httpClient
        .getParams()
        .setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, CONNECT_TIMEOUT_MS);
    httpClient.getParams().setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, true);
    httpClient
        .getParams()
        .setLongParameter(ClientPNames.CONN_MANAGER_TIMEOUT, CONN_MANAGER_TIMEOUT_MS);
    httpClient.setKeepAliveStrategy((response, context) -> KEEP_ALIVE_MS);
    // Retry connect/stale-socket failures before bytes are sent. Bodyless DELETE
    // is treated as idempotent by DefaultHttpRequestRetryHandler, so sent
    // mutations are rejected here before delegating.
    httpClient.setHttpRequestRetryHandler(new NoSentMutationRetryHandler());
    // RestAssured does not EntityUtils.consume unless a body matcher is set, so
    // the Apache connection stays leased. Buffering the entity consumes the
    // stream inside execute() and still leaves the body for RestAssured.
    httpClient.addResponseInterceptor(
        (response, context) -> {
          HttpEntity entity = response.getEntity();
          if (entity != null) {
            response.setEntity(new BufferedHttpEntity(entity));
          }
          connections.closeExpiredConnections();
          connections.closeIdleConnections(KEEP_ALIVE_MS, TimeUnit.MILLISECONDS);
        });
    return RestAssuredConfig.config()
        .httpClient(
            HttpClientConfig.httpClientConfig()
                .setParam(CoreConnectionPNames.STALE_CONNECTION_CHECK, true)
                .httpClientFactory(() -> httpClient)
                .reuseHttpClientInstance());
  }

  private static void startEvictor() {
    EVICTOR.scheduleAtFixedRate(
        PooledRestAssured::evictIdleConnections,
        IDLE_TIMEOUT_SECONDS,
        IDLE_TIMEOUT_SECONDS,
        TimeUnit.SECONDS);
    // JVM exit is suite teardown for Surefire. RestService.stop() must not
    // shut down these managers: other services still use the shared pool.
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(PooledRestAssured::shutdownManagers, "pooled-restassured-shutdown"));
  }

  private static void evictIdleConnections() {
    for (PoolingClientConnectionManager manager : MANAGERS) {
      manager.closeExpiredConnections();
      manager.closeIdleConnections(IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
  }

  private static void shutdownManagers() {
    EVICTOR.shutdownNow();
    for (PoolingClientConnectionManager manager : MANAGERS) {
      manager.shutdown();
    }
  }

  /**
   * Apache retries bodyless DELETE after send. Kafka Bridge consumer teardown and Wiremock DELETE
   * must not be replayed.
   */
  static final class NoSentMutationRetryHandler extends DefaultHttpRequestRetryHandler {

    private static final Set<String> MUTATIONS = Set.of("POST", "PUT", "PATCH", "DELETE");

    NoSentMutationRetryHandler() {
      super(3, false);
    }

    @Override
    public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
      HttpClientContext clientContext = HttpClientContext.adapt(context);
      HttpRequest request = clientContext.getRequest();
      if (request != null && isMutation(request) && clientContext.isRequestSent()) {
        return false;
      }
      return super.retryRequest(exception, executionCount, context);
    }

    private static boolean isMutation(HttpRequest request) {
      return MUTATIONS.contains(request.getRequestLine().getMethod());
    }
  }
}
