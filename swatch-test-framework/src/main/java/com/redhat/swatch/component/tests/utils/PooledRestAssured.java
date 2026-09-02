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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpEntity;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.CoreConnectionPNames;

/**
 * Shared Apache HttpClient for RestAssured so HTTP keep-alive reuses localhost sockets. Fabric8
 * LocalPortForward opens a Kubernetes websocket per TCP connect; a new client per request leaks
 * native threads.
 *
 * <p>RestAssured 6 still requires {@code AbstractHttpClient} ({@code DefaultHttpClient}). That
 * client synchronizes {@code execute}, so Kafka Bridge keep-alive polling uses a second instance
 * and does not block test traffic.
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
  private static final long IDLE_TIMEOUT_SECONDS = 30L;
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
    // Retry connect/stale-socket failures before bytes are sent. Do not retry
    // after a request has been written: POST/DELETE in CTs are not idempotent.
    httpClient.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(3, false));
    // RestAssured does not EntityUtils.consume unless a body matcher is set, so
    // the Apache connection stays leased. Buffering the entity consumes the
    // stream inside execute() and still leaves the body for RestAssured.
    httpClient.addResponseInterceptor(
        (response, context) -> {
          HttpEntity entity = response.getEntity();
          if (entity != null) {
            response.setEntity(new BufferedHttpEntity(entity));
          }
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
}
