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
package org.candlepin.subscriptions.test;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Slf4j
public class ExtendWithExportServiceWireMock {
  private static WireMockServer exportServer = start();
  private static final String EXPORT_ERROR_REQUEST =
      "/app/export/v1/b24c269d-33d6-410e-8808-c71c9635e84f/subscriptions/2e3d7746-2cf2-441e-84fe-cf28863d22ae/error";
  private static final String EXPORT_UPLOAD_REQUEST_FORMAT = "/app/export/v1/%s/%s/%s/upload";
  private static boolean logExportRequestsBody = true;

  static WireMockServer start() {
    exportServer =
        new WireMockServer(
            wireMockConfig()
                .dynamicPort()
                // This is mandatory to handle large files, otherwise Wiremock returns 500 Server
                // Error
                .jettyHeaderRequestSize(16384)
                .jettyHeaderResponseSize(80000)
                .stubRequestLoggingDisabled(true)
                .maxLoggedResponseSize(1000));
    exportServer.resetAll();
    exportServer.addMockServiceRequestListener(
        (request, response) -> {
          log.info("" + request.getHeaders());
          if (logExportRequestsBody) {
            log.info(request.getBodyAsString());
          }
        });
    exportServer.start();
    exportServer.stubFor(post(EXPORT_ERROR_REQUEST));
    System.out.printf("Running mock export services on port %d%n", exportServer.port());
    return exportServer;
  }

  @DynamicPropertySource
  static void registerExportApiProperties(DynamicPropertyRegistry registry) {
    registry.add("rhsm-subscriptions.export-service.url", exportServer::baseUrl);
  }

  public void logExportRequestsBody(boolean enable) {
    logExportRequestsBody = enable;
  }

  public void stubExportUploadFor(UUID id, String application, UUID resource) {
    exportServer.stubFor(
        post(String.format(EXPORT_UPLOAD_REQUEST_FORMAT, id, application, resource)));
  }

  public void verifyExportUpload(UUID id, String application, UUID resource) {
    exportServer.verify(
        postRequestedFor(
                urlPathEqualTo(
                    String.format(EXPORT_UPLOAD_REQUEST_FORMAT, id, application, resource)))
            .withHeader("Content-Type", equalTo("application/json")));
  }
}
