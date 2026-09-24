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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RbacWorkspaceClientTest {
  private RbacWorkspaceClient client;
  private HttpServer server;
  private final AtomicInteger requests = new AtomicInteger();
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private final AtomicReference<String> orgId = new AtomicReference<>();
  @TempDir Path temp;

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          requests.incrementAndGet();
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          orgId.set(exchange.getRequestHeaders().getFirst("x-rh-rbac-org-id"));
          byte[] response =
              "{\"data\":[{\"id\":\"workspace-id\",\"name\":\"Default\",\"type\":\"default\"}]}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          try (var output = exchange.getResponseBody()) {
            output.write(response);
          }
        });
    server.start();
    client = new RbacWorkspaceClient();
    client.properties = mock(KesselProperties.class);
    client.tokenProvider = mock(HccAuthTokenProvider.class);
    when(client.properties.rbacBaseEndpoint())
        .thenReturn(Optional.of("http://127.0.0.1:" + server.getAddress().getPort()));
    when(client.properties.timeoutMs()).thenReturn(5000L);
    client.trustStore = Optional.empty();
    client.trustStorePassword = Optional.empty();
    client.trustStoreType = "PKCS12";
  }

  @AfterEach
  void close() {
    client.close();
    server.stop(0);
  }

  @Test
  void blankWorkspaceOverrideUsesDiscoveredRbacEndpoint() {
    when(client.properties.rbacBaseEndpoint()).thenReturn(Optional.empty());
    client.rbacEndpoint = "http://127.0.0.1:" + server.getAddress().getPort();
    assertEquals("workspace-id", client.defaultWorkspaceId("org-123"));
  }

  @Test
  void requiredRbacAuthReachesTheActualSdkWorkspaceRequest() {
    client.authenticated = true;
    when(client.tokenProvider.authorizationHeader()).thenReturn("Bearer workspace-token");
    assertEquals("workspace-id", client.defaultWorkspaceId("org-123"));
    assertEquals("Bearer workspace-token", authorization.get());
    assertEquals("org-123", orgId.get());
  }

  @Test
  void kesselAuthFlagAlsoRequiresWorkspaceAuthentication() {
    client.kesselAuthEnabled = true;
    when(client.tokenProvider.authorizationHeader()).thenReturn("Bearer kessel-token");
    assertEquals("workspace-id", client.defaultWorkspaceId("org-123"));
    assertEquals("Bearer kessel-token", authorization.get());
  }

  @Test
  void existingCredentialDrivenWorkspaceAuthIsPreserved() {
    when(client.tokenProvider.isConfigured()).thenReturn(true);
    when(client.tokenProvider.authorizationHeader()).thenReturn("Bearer legacy-workspace-token");
    assertEquals("workspace-id", client.defaultWorkspaceId("org-123"));
    assertEquals("Bearer legacy-workspace-token", authorization.get());
  }

  @Test
  void optionalLegacyWorkspaceDoesNotFetchTokens() {
    assertEquals("workspace-id", client.defaultWorkspaceId("org-123"));
    assertNull(authorization.get());
    verify(client.tokenProvider, never()).authorizationHeader();
  }

  @Test
  void missingRequiredCredentialsNeverSendTheWorkspaceRequest() {
    client.authenticated = true;
    when(client.tokenProvider.authorizationHeader())
        .thenThrow(new IllegalStateException("credentials missing"));
    assertThrows(IllegalStateException.class, () -> client.defaultWorkspaceId("org-123"));
    assertEquals(0, requests.get());
  }

  @Test
  void absentEndpointCaUsesSystemTrust() throws Exception {
    assertSame(SSLContext.getDefault(), client.httpClient().sslContext());
    assertSame(client.httpClient(), client.httpClient());
  }

  @Test
  void suppliedEndpointTruststoreBuildsAnIsolatedTlsContext() throws Exception {
    Path storeFile = temp.resolve("endpoint.p12");
    KeyStore store = KeyStore.getInstance("PKCS12");
    store.load(null);
    try (var output = Files.newOutputStream(storeFile)) {
      store.store(output, "test-password".toCharArray());
    }
    client.trustStore = Optional.of(storeFile.toUri().toString());
    client.trustStorePassword = Optional.of("test-password");
    assertNotSame(SSLContext.getDefault(), client.httpClient().sslContext());
  }

  @Test
  void invalidTruststoreFailsWithoutFallingBackToSystemTrust() throws Exception {
    Path invalid = temp.resolve("invalid.p12");
    Files.writeString(invalid, "not a truststore");
    client.trustStore = Optional.of(invalid.toString());
    assertThrows(IllegalStateException.class, () -> client.defaultWorkspaceId("org-123"));
    assertEquals(0, requests.get());
  }
}
