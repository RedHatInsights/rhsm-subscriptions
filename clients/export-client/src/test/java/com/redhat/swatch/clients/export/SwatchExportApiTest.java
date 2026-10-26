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
package com.redhat.swatch.clients.export;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.redhat.swatch.clients.ClientConfig;
import com.redhat.swatch.clients.ClientConnection;
import com.redhat.swatch.clients.TokenProvider;
import com.redhat.swatch.clients.export.api.client.ApiException;
import com.redhat.swatch.clients.export.api.model.DownloadExportErrorRequest;
import com.redhat.swatch.clients.export.resources.ExportServiceWiremock;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SwatchExportApiTest {
  private static final UUID EXPORT_ID = UUID.randomUUID();
  private static final String APPLICATION_NAME = "SWATCH";
  private static final UUID RESOURCE_ID = UUID.randomUUID();
  private static final String PSK = "placeholder";
  private static final String TOKEN = "test-token";
  private static final TokenProvider TOKENS = () -> TOKEN;

  private final ExportServiceWiremock server = new ExportServiceWiremock();

  @BeforeEach
  void setup() {
    this.server.start();
  }

  @AfterEach
  void tearDown() {
    this.server.stop();
  }

  @Test
  void testDownloadExportError() {
    SwatchExportApi client = givenClient(null);
    server.stubDownloadExportError(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID);
    try {
      client.downloadExportError(
          EXPORT_ID, APPLICATION_NAME, RESOURCE_ID, new DownloadExportErrorRequest());
      server.verifyDownloadExportError(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID);
    } catch (ApiException e) {
      fail(e);
    }
  }

  @Test
  void testDownloadExportUpload() throws IOException {
    SwatchExportApi client = givenClient(null);
    server.stubDownloadExportUpload(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID);
    File file = File.createTempFile("export", ".json");
    file.deleteOnExit();
    try {
      client.downloadExportUpload(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID, file);
      server.verifyDownloadExportUpload(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID);
    } catch (ApiException e) {
      fail(e);
    }
  }

  @Test
  void describeExposesCurrentEndpoint() {
    SwatchExportApi client = givenClient(null);
    assertEquals("export-service", client.name());
    assertEquals("export-service", client.getConfig().getName());
    assertEquals("v1", client.describe().get("current"));
  }

  @Test
  void authenticatedTrueSendsBearerAndOmitsPsk() {
    SwatchExportApi client = givenClient(true);
    server.stubDownloadExportError(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID);
    try {
      client.downloadExportError(
          EXPORT_ID, APPLICATION_NAME, RESOURCE_ID, new DownloadExportErrorRequest());
    } catch (ApiException e) {
      fail(e);
    }
    server.verify(
        server
            .downloadExportErrorRequested(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID)
            .withHeader("Authorization", equalTo("Bearer " + TOKEN))
            .withHeader("x-rh-exports-psk", absent()));
  }

  @Test
  void authenticatedFalseSendsPskAndOmitsBearer() {
    SwatchExportApi client = givenClient(false);
    server.stubDownloadExportError(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID);
    try {
      client.downloadExportError(
          EXPORT_ID, APPLICATION_NAME, RESOURCE_ID, new DownloadExportErrorRequest());
    } catch (ApiException e) {
      fail(e);
    }
    server.verify(
        server
            .downloadExportErrorRequested(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID)
            .withHeader("x-rh-exports-psk", equalTo(PSK))
            .withHeader("Authorization", absent()));
  }

  @Test
  void constructorRejectsNullTokenProvider() {
    ClientConnection connection =
        new ClientConnection("v2", server.baseUrl(), null, null, null, true);
    ClientConfig config = new ClientConfig("export-service", PSK, connection, null, null);
    assertThrows(NullPointerException.class, () -> new SwatchExportApi(config, null));
  }

  @Test
  void authenticatedTrueTokenFailureDoesNotSendPsk() {
    TokenProvider failing =
        () -> {
          throw new IllegalStateException("token unavailable");
        };
    SwatchExportApi client = givenClient(true, failing);
    server.stubDownloadExportError(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID);
    assertThrows(
        Exception.class,
        () ->
            client.downloadExportError(
                EXPORT_ID, APPLICATION_NAME, RESOURCE_ID, new DownloadExportErrorRequest()));
    server.verify(0, server.downloadExportErrorRequested(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID));
  }

  private SwatchExportApi givenClient(Boolean authenticated) {
    return givenClient(authenticated, TOKENS);
  }

  private SwatchExportApi givenClient(Boolean authenticated, TokenProvider tokens) {
    String id = authenticated == null ? "v1" : "v2";
    ClientConnection connection =
        new ClientConnection(id, server.baseUrl(), null, null, null, authenticated);
    ClientConfig config = new ClientConfig("export-service", PSK, connection, null, null);
    return new SwatchExportApi(config, tokens);
  }
}
