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
package com.redhat.swatch.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HccAuthTokenProviderTest {
  private HttpServer server;
  private HccAuthTokenProvider provider;
  private KesselProperties properties;
  private final AtomicInteger discoveryRequests = new AtomicInteger();
  private final AtomicInteger tokenRequests = new AtomicInteger();
  private volatile int expiresIn = 3600;
  private volatile int tokenStatus = 200;
  private String issuer;

  @BeforeEach
  void setup() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    issuer = "http://127.0.0.1:" + server.getAddress().getPort();
    server.createContext("/", this::respond);
    server.start();
    properties = mock(KesselProperties.class);
    when(properties.authOidcIssuer()).thenReturn(Optional.of(issuer));
    when(properties.authClientId()).thenReturn(Optional.of("test-client"));
    when(properties.authClientSecret()).thenReturn(Optional.of("test-secret"));
    provider = new HccAuthTokenProvider();
    provider.properties = properties;
  }

  @AfterEach
  void close() {
    server.stop(0);
  }

  @Test
  void initializationIsLazyAndTheSdkCachesTokens() {
    assertTrue(provider.isConfigured());
    assertEquals(0, discoveryRequests.get());
    assertEquals("Bearer token-1", provider.authorizationHeader());
    assertEquals("Bearer token-1", provider.authorizationHeader());
    assertEquals(1, discoveryRequests.get());
    assertEquals(1, tokenRequests.get());
  }

  @Test
  void sdkRenewsNearExpiryTokensWithoutRepeatingDiscovery() {
    expiresIn = 1; // Already within the SDK's renewal window; no sleep needed.
    assertEquals("Bearer token-1", provider.authorizationHeader());
    assertEquals("Bearer token-2", provider.authorizationHeader());
    assertEquals(1, discoveryRequests.get());
    assertEquals(2, tokenRequests.get());
  }

  @Test
  void concurrentCallsShareOneTokenCache() throws Exception {
    try (var executor = Executors.newFixedThreadPool(8)) {
      var calls = new ArrayList<Callable<String>>();
      for (int i = 0; i < 16; i++) {
        calls.add(provider::authorizationHeader);
      }
      for (var result : executor.invokeAll(calls)) {
        assertEquals("Bearer token-1", result.get());
      }
    }
    assertEquals(1, discoveryRequests.get());
    assertEquals(1, tokenRequests.get());
  }

  @Test
  void missingCredentialsFailBeforeAnyNetworkRequest() {
    when(properties.authClientSecret()).thenReturn(Optional.empty());
    assertFalse(provider.isConfigured());
    assertThrows(IllegalStateException.class, provider::authorizationHeader);
    assertEquals(0, discoveryRequests.get());
    assertEquals(0, tokenRequests.get());
  }

  @Test
  void blankCredentialsAreNotConfigured() {
    when(properties.authClientId()).thenReturn(Optional.of(" "));
    assertFalse(provider.isConfigured());
    assertThrows(IllegalStateException.class, provider::authorizationHeader);
    assertEquals(0, discoveryRequests.get());
  }

  @Test
  void tokenFailureStopsTheRequestAndCanRecover() {
    tokenStatus = 503;
    assertThrows(IllegalStateException.class, provider::authorizationHeader);
    tokenStatus = 200;
    assertEquals("Bearer token-2", provider.authorizationHeader());
    assertEquals(1, discoveryRequests.get());
  }

  private void respond(HttpExchange exchange) throws IOException {
    String body;
    int status = 200;
    if (exchange.getRequestURI().getPath().equals("/token")) {
      int request = tokenRequests.incrementAndGet();
      status = tokenStatus;
      body =
          status == 200
              ? "{\"access_token\":\"token-"
                  + request
                  + "\",\"token_type\":\"Bearer\",\"expires_in\":"
                  + expiresIn
                  + "}"
              : "{\"error\":\"temporarily_unavailable\"}";
    } else {
      discoveryRequests.incrementAndGet();
      body = "{\"issuer\":\"" + issuer + "\",\"token_endpoint\":\"" + issuer + "/token\"}";
    }
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }
}
