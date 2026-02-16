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
package org.candlepin.subscriptions.export;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequestClass;
import com.redhat.swatch.clients.export.api.client.ExportApiClientFactory;
import com.redhat.swatch.export.ExportServiceRequest;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExportDelegateImplTest {

  private static final UUID EXPORT_ID = UUID.randomUUID();
  private static final String APPLICATION = "subscriptions";
  private static final UUID RESOURCE_ID = UUID.randomUUID();

  private WireMockServer wireMockServer;
  private ExportDelegateImpl exportDelegate;
  private ExportServiceRequest request;

  @BeforeEach
  void setup() throws Exception {
    request = mock(ExportServiceRequest.class);
    when(request.getExportRequestUUID()).thenReturn(EXPORT_ID);
    when(request.getApplication()).thenReturn(APPLICATION);
    ResourceRequestClass resourceRequestClass = mock(ResourceRequestClass.class);
    when(resourceRequestClass.getUUID()).thenReturn(RESOURCE_ID);
    when(request.getRequest()).thenReturn(resourceRequestClass);
    wireMockServer =
        new WireMockServer(
            wireMockConfig()
                .dynamicPort()
                .stubRequestLoggingDisabled(true)
                .maxLoggedResponseSize(1000));
    wireMockServer.start();
    wireMockServer.stubFor(post(urlPathMatching("/app/export/.*")).willReturn(ok()));

    HttpClientProperties config = new HttpClientProperties();
    config.setUrl(wireMockServer.baseUrl());
    config.setPsk("placeholder");
    exportDelegate = new ExportDelegateImpl(new ExportApiClientFactory(config).getObject());
  }

  @AfterEach
  void tearDown() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  /**
   * Test to reproduce the IllegalAccessError reported by <a
   * href="https://issues.redhat.com/browse/SWATCH-4569">SWATCH-4569</a>.
   */
  @Test
  void testUploadWithLargeFileShouldNotThrowIllegalAccessError() throws Exception {
    File file = File.createTempFile("export-large-test", ".json");
    file.deleteOnExit();
    // 2MB exceeds the default 1MB EntityOutputStream memory threshold, forcing RESTEasy
    // to use the file-backed PathHttpEntity code path.
    byte[] data = new byte[2 * 1024 * 1024];
    java.util.Arrays.fill(data, (byte) '{');
    Files.write(file.toPath(), data);

    assertDoesNotThrow(() -> exportDelegate.upload(file, request));

    wireMockServer.verify(
        postRequestedFor(
            urlPathMatching(
                "/app/export/v1/"
                    + EXPORT_ID
                    + "/"
                    + APPLICATION
                    + "/"
                    + RESOURCE_ID
                    + "/upload")));
  }
}
