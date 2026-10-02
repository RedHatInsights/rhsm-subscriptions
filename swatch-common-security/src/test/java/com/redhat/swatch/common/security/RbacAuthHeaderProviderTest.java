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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.rbac.api.resources.AccessApi;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.jboss.resteasy.microprofile.client.RestClientBuilderImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RbacAuthHeaderProviderTest {
  private RbacAuthHeaderProvider provider;
  private ClientRequestContext context;
  private MultivaluedHashMap<String, Object> headers;

  @BeforeEach
  void setup() {
    provider = new RbacAuthHeaderProvider();
    provider.tokenProvider = mock(HccAuthTokenProvider.class);
    context = mock(ClientRequestContext.class);
    headers = new MultivaluedHashMap<>();
    headers.putSingle("x-rh-identity", "identity-from-user-or-export-event");
    when(context.getHeaders()).thenReturn(headers);
  }

  @Test
  void bearerIsAddedWithoutReplacingIdentityOrDuplicatingAuthorization() {
    provider.authenticated = true;
    when(provider.tokenProvider.authorizationHeader()).thenReturn("Bearer workload-token");
    headers.putSingle(HttpHeaders.AUTHORIZATION, "old-token");
    provider.filter(context);
    provider.filter(context);
    assertEquals("Bearer workload-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
    assertEquals(1, headers.get(HttpHeaders.AUTHORIZATION).size());
    assertEquals("identity-from-user-or-export-event", headers.getFirst("x-rh-identity"));
  }

  @Test
  void legacyRbacDoesNotFetchAToken() {
    provider.filter(context);
    verifyNoInteractions(provider.tokenProvider);
    assertFalse(headers.containsKey(HttpHeaders.AUTHORIZATION));
    assertEquals("identity-from-user-or-export-event", headers.getFirst("x-rh-identity"));
  }

  @Test
  void authenticationFailureAbortsRatherThanSendingAnAnonymousRequest() {
    provider.authenticated = true;
    when(provider.tokenProvider.authorizationHeader())
        .thenThrow(new IllegalStateException("token unavailable"));
    assertThrows(IllegalStateException.class, () -> provider.filter(context));
    assertFalse(headers.containsKey(HttpHeaders.AUTHORIZATION));
  }

  @Test
  void bearerAndIdentityReachTheGeneratedPermissionClient() throws Exception {
    var authorization = new AtomicReference<String>();
    var identity = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/rbac/v1/access/",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          identity.set(exchange.getRequestHeaders().getFirst("x-rh-identity"));
          byte[] response = "{\"data\":[]}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          try (var output = exchange.getResponseBody()) {
            output.write(response);
          }
        });
    server.start();
    provider.authenticated = true;
    when(provider.tokenProvider.authorizationHeader()).thenReturn("Bearer workload-token");
    AccessApi api = null;
    try {
      api =
          new RestClientBuilderImpl()
              .baseUri(
                  URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/rbac/v1"))
              .register(provider)
              .build(AccessApi.class);
      api.getPrincipalAccess("subscriptions", null, "background-export-identity", null, null);
      assertEquals("Bearer workload-token", authorization.get());
      assertEquals("background-export-identity", identity.get());
    } finally {
      if (api != null) {
        ((Closeable) api).close();
      }
      server.stop(0);
    }
  }
}
