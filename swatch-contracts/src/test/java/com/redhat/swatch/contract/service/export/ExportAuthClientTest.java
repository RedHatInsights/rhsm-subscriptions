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
package com.redhat.swatch.contract.service.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.export.api.model.DownloadExportErrorRequest;
import com.redhat.swatch.clients.export.api.resources.ExportApi;
import com.redhat.swatch.common.security.HccAuthTokenProvider;
import com.sun.net.httpserver.HttpServer;
import java.io.Closeable;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.resteasy.microprofile.client.RestClientBuilderImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportAuthClientTest {
  @TempDir Path temp;

  @Test
  void bearerIsUsedForBothUploadAndErrorCallbacksWithoutPsk() throws Exception {
    verifyRequests(true);
  }

  @Test
  void legacyPskIsUsedForBothUploadAndErrorCallbacksWithoutTokens() throws Exception {
    verifyRequests(false);
  }

  private void verifyRequests(boolean authenticated) throws Exception {
    Map<String, Map<String, String>> requests = new ConcurrentHashMap<>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          var received = new java.util.HashMap<String, String>();
          received.put("authorization", exchange.getRequestHeaders().getFirst("Authorization"));
          received.put("psk", exchange.getRequestHeaders().getFirst("x-rh-exports-psk"));
          requests.put(exchange.getRequestURI().getPath(), received);
          try (var input = exchange.getRequestBody()) {
            input.transferTo(OutputStream.nullOutputStream());
          }
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        });
    server.start();
    var provider = new ExportPskHeaderProvider();
    provider.authenticated = authenticated;
    provider.psk = authenticated ? Optional.empty() : Optional.of("legacy-psk");
    provider.tokenProvider = mock(HccAuthTokenProvider.class);
    if (authenticated) {
      when(provider.tokenProvider.authorizationHeader())
          .thenReturn("Bearer upload-token", "Bearer error-token");
    }
    ExportApi api = null;
    try {
      api =
          new RestClientBuilderImpl()
              .baseUri(URI.create("http://127.0.0.1:" + server.getAddress().getPort()))
              .register(provider)
              .build(ExportApi.class);
      UUID exportId = UUID.randomUUID();
      UUID resourceId = UUID.randomUUID();
      Path payload = temp.resolve("export.json");
      Files.writeString(payload, "{}");
      api.downloadExportUpload(exportId, "subscriptions", resourceId, payload.toFile());
      api.downloadExportError(
          exportId,
          "subscriptions",
          resourceId,
          new DownloadExportErrorRequest().error(500).message("test error"));
      String prefix = "/app/export/v1/" + exportId + "/subscriptions/" + resourceId;
      assertEquals(2, requests.size());
      if (authenticated) {
        assertEquals("Bearer upload-token", requests.get(prefix + "/upload").get("authorization"));
        assertEquals("Bearer error-token", requests.get(prefix + "/error").get("authorization"));
        assertNull(requests.get(prefix + "/upload").get("psk"));
        assertNull(requests.get(prefix + "/error").get("psk"));
      } else {
        for (Map<String, String> request : requests.values()) {
          assertEquals("legacy-psk", request.get("psk"));
          assertNull(request.get("authorization"));
        }
        verifyNoInteractions(provider.tokenProvider);
      }
    } finally {
      if (api != null) {
        ((Closeable) api).close();
      }
      server.stop(0);
    }
  }
}
