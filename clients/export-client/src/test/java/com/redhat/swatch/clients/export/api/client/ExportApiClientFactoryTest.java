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
import static org.junit.jupiter.api.Assertions.fail;

import com.redhat.swatch.clients.export.api.model.DownloadExportErrorRequest;
import com.redhat.swatch.clients.export.api.resources.ExportApi;
import com.redhat.swatch.clients.export.resources.ExportServiceWiremock;
import java.util.UUID;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExportApiClientFactoryTest {
  private static final UUID EXPORT_ID = UUID.randomUUID();
  private static final String APPLICATION_NAME = "SWATCH";
  private static final UUID RESOURCE_ID = UUID.randomUUID();
  private static final String PSK = "placeholder";

  private final HttpClientProperties config = new HttpClientProperties();

  private final ExportServiceWiremock server = new ExportServiceWiremock();

  @BeforeEach
  void setup() {
    this.server.start();
    this.config.setUrl(this.server.baseUrl());
    this.config.setPsk(PSK);
  }

  @AfterEach
  void tearDown() {
    this.server.stop();
  }

  @Test
  void testStubClientConfiguration() throws Exception {
    config.setUseStub(true);
    ExportApiClientFactory factory = new ExportApiClientFactory(config);
    assertEquals(StubExportApi.class, factory.getObject().getClass());
  }

  @Test
  void testDownloadExportError() throws Exception {
    ExportApi client = givenClient();
    whenExportServiceExpectsDownloadExportError();
    thenInvokeDownloadExportErrorShouldWork(client);
  }

  @Test
  void testDownloadExportUpload() throws Exception {
    ExportApi client = givenClient();
    whenExportServiceExpectsDownloadExportUpload();
    thenInvokeDownloadExportUploadShouldWork(client);
  }

  private void whenExportServiceExpectsDownloadExportError() {
    server.stubDownloadExportError(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID);
  }

  private void whenExportServiceExpectsDownloadExportUpload() {
    server.stubDownloadExportUpload(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID);
  }

  private void thenInvokeDownloadExportErrorShouldWork(ExportApi client) {
    try {
      client.downloadExportError(
          EXPORT_ID, APPLICATION_NAME, RESOURCE_ID, new DownloadExportErrorRequest());
      server.verifyDownloadExportError(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID);
    } catch (ApiException e) {
      fail(e);
    }
  }

  private void thenInvokeDownloadExportUploadShouldWork(ExportApi client) {
    try {
      client.downloadExportUpload(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID, "content");
      server.verifyDownloadExportUpload(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID);
    } catch (ApiException e) {
      fail(e);
    }
  }

  private ExportApi givenClient() throws Exception {
    ExportApiClientFactory factory = new ExportApiClientFactory(config);
    return factory.getObject();
  }
}
