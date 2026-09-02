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
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpEntity;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.util.EntityUtils;

/**
 * Shared Apache HttpClient for RestAssured so HTTP keep-alive reuses localhost sockets. Fabric8
 * LocalPortForward opens a Kubernetes websocket per TCP connect; a new client per request leaks
 * native threads.
 *
 * <p>RestAssured 6 still requires {@code AbstractHttpClient} ({@code DefaultHttpClient}). That
 * client synchronizes {@code execute}, so Kafka Bridge keep-alive polling uses a second instance
 * and does not block test traffic.
 *
 * <p>RestAssured buffers the body without consuming the Apache {@code HttpEntity}, so pooled
 * connections stay leased and later calls fail with {@code ConnectionPoolTimeoutException}. A
 * response interceptor copies the entity to a {@code ByteArrayEntity} so the lease is released.
 *
 * <p>Kubernetes port-forwards close idle streams without Apache noticing. Infinite keep-alive then
 * reuses a dead socket and tests fail with {@code NoHttpResponseException}. Keep-alive is capped,
 * stale connections are checked, and {@code NoHttpResponseException} is retried on a new socket.
 */
public final class PooledRestAssured {

  private static final int MAX_TOTAL = 64;
  private static final int MAX_PER_ROUTE = 8;
  private static final int KEEP_ALIVE_MAX_TOTAL = 8;
  private static final int KEEP_ALIVE_MAX_PER_ROUTE = 2;
  private static final int SO_TIMEOUT_MS = 30_000;
  private static final int CONNECT_TIMEOUT_MS = 10_000;
  private static final long CONN_MANAGER_TIMEOUT_MS = 10_000L;
  private static final int KEEP_ALIVE_MS = 3_000;
  private static final int RETRY_COUNT = 3;
  private static final RestAssuredConfig CONFIG = buildConfig(MAX_TOTAL, MAX_PER_ROUTE);
  private static final RestAssuredConfig KEEP_ALIVE_CONFIG =
      buildConfig(KEEP_ALIVE_MAX_TOTAL, KEEP_ALIVE_MAX_PER_ROUTE);
  private static final Filter CONSUME_RESPONSE = new ConsumeResponseFilter();

  static {
    install();
  }

  private PooledRestAssured() {}

  public static void install() {
    RestAssured.config = CONFIG;
    if (!RestAssured.filters().contains(CONSUME_RESPONSE)) {
      RestAssured.filters(CONSUME_RESPONSE);
    }
  }

  public static RestAssuredConfig config() {
    return CONFIG;
  }

  public static RestAssuredConfig keepAliveConfig() {
    return KEEP_ALIVE_CONFIG;
  }

  @SuppressWarnings("deprecation")
  private static RestAssuredConfig buildConfig(int maxTotal, int maxPerRoute) {
    PoolingClientConnectionManager connections = new PoolingClientConnectionManager();
    connections.setMaxTotal(maxTotal);
    connections.setDefaultMaxPerRoute(maxPerRoute);
    DefaultHttpClient httpClient = new DefaultHttpClient(connections);
    httpClient.getParams().setIntParameter(CoreConnectionPNames.SO_TIMEOUT, SO_TIMEOUT_MS);
    httpClient
        .getParams()
        .setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, CONNECT_TIMEOUT_MS);
    httpClient
        .getParams()
        .setLongParameter(ClientPNames.CONN_MANAGER_TIMEOUT, CONN_MANAGER_TIMEOUT_MS);
    httpClient.getParams().setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, true);
    httpClient.setKeepAliveStrategy((response, context) -> KEEP_ALIVE_MS);
    httpClient.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(RETRY_COUNT, true));
    httpClient.addResponseInterceptor(
        (response, context) -> {
          HttpEntity entity = response.getEntity();
          if (entity == null) {
            connections.closeExpiredConnections();
            connections.closeIdleConnections(KEEP_ALIVE_MS, TimeUnit.MILLISECONDS);
            return;
          }
          byte[] data;
          try {
            data = EntityUtils.toByteArray(entity);
          } finally {
            EntityUtils.consumeQuietly(entity);
            connections.closeExpiredConnections();
            connections.closeIdleConnections(KEEP_ALIVE_MS, TimeUnit.MILLISECONDS);
          }
          ByteArrayEntity copy = new ByteArrayEntity(data);
          if (entity.getContentType() != null) {
            copy.setContentType(entity.getContentType());
          }
          if (entity.getContentEncoding() != null) {
            copy.setContentEncoding(entity.getContentEncoding());
          }
          response.setEntity(copy);
        });
    return RestAssuredConfig.config()
        .httpClient(
            HttpClientConfig.httpClientConfig()
                .httpClientFactory(() -> httpClient)
                .reuseHttpClientInstance());
  }

  // RestAssured skips EntityUtils.consume unless body matchers are present.
  private static final class ConsumeResponseFilter implements Filter {
    @Override
    public Response filter(
        FilterableRequestSpecification requestSpec,
        FilterableResponseSpecification responseSpec,
        FilterContext ctx) {
      Response response = ctx.next(requestSpec, responseSpec);
      response.asByteArray();
      return response;
    }
  }
}
