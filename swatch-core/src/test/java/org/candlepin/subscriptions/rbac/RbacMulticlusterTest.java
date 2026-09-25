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
package org.candlepin.subscriptions.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.redhat.swatch.kessel.HccCredentials;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RbacMulticlusterTest {
  private HttpServer server;
  private RbacProperties properties;
  private final List<Headers> requests = new CopyOnWriteArrayList<>();

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          requests.add(exchange.getRequestHeaders());
          String data =
              exchange.getRequestURI().getPath().contains("workspaces")
                  ? "{\"data\":[{\"id\":\"workspace-1\",\"name\":\"Default\",\"type\":\"default\"}]}"
                  : "{\"data\":[]}";
          byte[] body = data.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.start();
    properties = new RbacProperties();
    properties.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api/rbac/v1");
  }

  @AfterEach
  void close() {
    RequestContextHolder.resetRequestAttributes();
    server.stop(0);
  }

  @Test
  void interactiveAndBackgroundRequestsPreserveIdentityAndGetCurrentToken() throws Exception {
    properties.setAuthenticated(true);
    var tokens = new AtomicInteger();
    var client =
        new RbacApiFactory(properties, () -> "Bearer token-" + tokens.incrementAndGet())
            .getObject();
    var request = new MockHttpServletRequest();
    request.addHeader("x-rh-identity", "interactive-identity");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    client.getCurrentUserAccess("subscriptions");
    RequestContextHolder.resetRequestAttributes();
    client.getCurrentIdentityAccess("subscriptions", "background-identity");
    assertEquals(2, requests.size());
    assertEquals("interactive-identity", requests.get(0).getFirst("x-rh-identity"));
    assertEquals("background-identity", requests.get(1).getFirst("x-rh-identity"));
    assertEquals("Bearer token-1", requests.get(0).getFirst("Authorization"));
    assertEquals("Bearer token-2", requests.get(1).getFirst("Authorization"));
  }

  @Test
  void legacyIdentityOnlyDoesNotAcquireToken() throws Exception {
    var client =
        new RbacApiFactory(
                properties,
                () -> {
                  throw new AssertionError("Unexpected token request");
                })
            .getObject();
    client.getCurrentIdentityAccess("subscriptions", "identity");
    assertNull(requests.getFirst().getFirst("Authorization"));
    assertEquals("identity", requests.getFirst().getFirst("x-rh-identity"));
  }

  @Test
  void requiredAuthFailureDoesNotReachServer() throws Exception {
    properties.setAuthenticated(true);
    var client =
        new RbacApiFactory(
                properties,
                () -> {
                  throw new IllegalStateException("Token unavailable");
                })
            .getObject();
    assertThrows(
        Exception.class, () -> client.getCurrentIdentityAccess("subscriptions", "identity"));
    assertEquals(0, requests.size());
  }

  @Test
  void workspacePreservesOrgAndUsesRbacEndpointWhenOverrideIsBlank() {
    var kessel = new KesselProperties();
    kessel.setRbacBaseEndpoint("");
    properties.setAuthenticated(true);
    var credentials = mock(HccCredentials.class);
    when(credentials.authorizationHeader()).thenReturn("Bearer workspace-token");
    try (var client = new RbacWorkspaceClient(kessel, properties, credentials)) {
      assertEquals("workspace-1", client.defaultWorkspaceId("org-123"));
    }
    assertEquals("Bearer workspace-token", requests.getFirst().getFirst("Authorization"));
    assertEquals("org-123", requests.getFirst().getFirst("x-rh-rbac-org-id"));
  }

  @Test
  void workspaceRequiredAuthDoesNotDowngrade() {
    var kessel = new KesselProperties();
    kessel.setRbacBaseEndpoint("");
    kessel.setAuthEnabled(true);
    var credentials = new HccCredentials(() -> null, () -> null, () -> null);
    try (var client = new RbacWorkspaceClient(kessel, properties, credentials)) {
      assertThrows(IllegalStateException.class, () -> client.defaultWorkspaceId("org-123"));
    }
    assertEquals(0, requests.size());
  }

  @Test
  void workspaceInvalidTruststoreFailsClosed() {
    properties.setTruststore(new FileSystemResource("/does-not-exist"));
    assertThrows(
        IllegalStateException.class,
        () ->
            new RbacWorkspaceClient(
                new KesselProperties(), properties, mock(HccCredentials.class)));
  }
}
