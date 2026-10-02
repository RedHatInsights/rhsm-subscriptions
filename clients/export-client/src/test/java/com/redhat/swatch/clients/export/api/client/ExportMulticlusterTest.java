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
package com.redhat.swatch.clients.export.api.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.redhat.swatch.clients.export.api.model.DownloadExportErrorRequest;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.naming.ConfigurationException;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportMulticlusterTest {
  @TempDir Path temp;
  private HttpServer server;
  private final HttpClientProperties properties = new HttpClientProperties();
  private final List<Headers> requests = new CopyOnWriteArrayList<>();

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          requests.add(exchange.getRequestHeaders());
          exchange.getRequestBody().readAllBytes();
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
    properties.setUrl("http://127.0.0.1:" + server.getAddress().getPort());
  }

  @AfterEach
  void close() {
    server.stop(0);
  }

  @Test
  void uploadAndErrorUseFreshBearerWithoutPsk() throws Exception {
    properties.setAuthenticated(true);
    var count = new AtomicInteger();
    var client =
        new ExportApiClientFactory(properties, () -> "Bearer token-" + count.incrementAndGet())
            .getObject();
    Path payload = java.nio.file.Files.writeString(temp.resolve("export.json"), "{}");
    try (var http = client.getApiClient().getHttpClient()) {
      client.downloadExportUpload(UUID.randomUUID(), "SWATCH", UUID.randomUUID(), payload.toFile());
      client.downloadExportError(
          UUID.randomUUID(), "SWATCH", UUID.randomUUID(), new DownloadExportErrorRequest());
    }
    assertEquals(2, requests.size());
    for (int i = 0; i < requests.size(); i++) {
      assertEquals("Bearer token-" + (i + 1), requests.get(i).getFirst("Authorization"));
      assertNull(requests.get(i).getFirst("x-rh-exports-psk"));
    }
  }

  @Test
  void legacyPskIsRequiredAndBearerConfigurationDoesNotLeakBetweenClients() throws Exception {
    assertThrows(
        ConfigurationException.class, () -> new ExportApiClientFactory(properties).getObject());
    properties.setPsk("legacy-psk");
    var client =
        new ExportApiClientFactory(
                properties,
                () -> {
                  throw new AssertionError("Unexpected token request");
                })
            .getObject();
    try (var http = client.getApiClient().getHttpClient()) {
      client.downloadExportError(
          UUID.randomUUID(), "SWATCH", UUID.randomUUID(), new DownloadExportErrorRequest());
    }
    assertEquals("legacy-psk", requests.getFirst().getFirst("x-rh-exports-psk"));
    assertNull(requests.getFirst().getFirst("Authorization"));
  }

  @Test
  void tokenFailureNeverFallsBackToPsk() throws Exception {
    properties.setAuthenticated(true);
    properties.setPsk("must-not-be-used");
    var client =
        new ExportApiClientFactory(
                properties,
                () -> {
                  throw new IllegalStateException("Token unavailable");
                })
            .getObject();
    try (var http = client.getApiClient().getHttpClient()) {
      assertThrows(
          Exception.class,
          () ->
              client.downloadExportError(
                  UUID.randomUUID(),
                  "SWATCH",
                  UUID.randomUUID(),
                  new DownloadExportErrorRequest()));
    }
    assertEquals(0, requests.size());
  }
}
