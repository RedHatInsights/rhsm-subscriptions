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
package com.redhat.swatch.clients.export.resources;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import java.util.UUID;

public class ExportServiceWiremock {
  private WireMockServer wireMockServer;

  public void start() {
    wireMockServer =
        new WireMockServer(wireMockConfig().dynamicPort().notifier(new ConsoleNotifier(true)));
    wireMockServer.resetAll();
    wireMockServer.start();
    System.out.printf("Running mock export services on port %d%n", wireMockServer.port());
  }

  public void stop() {
    if (wireMockServer != null) {
      System.out.printf("Stopping mock export services on port %d%n", wireMockServer.port());
      wireMockServer.stop();
      wireMockServer = null;
    }
  }

  public String baseUrl() {
    return wireMockServer.baseUrl();
  }

  public void stubDownloadExportError(UUID id, String application, UUID resource) {
    wireMockServer.stubFor(
        post(downloadExportErrorRequest(id, application, resource)).willReturn(ok()));
  }

  public void stubDownloadExportUpload(UUID id, String application, UUID resource) {
    wireMockServer.stubFor(
        post(downloadExportErrorUpload(id, application, resource)).willReturn(ok()));
  }

  public void verifyDownloadExportError(UUID id, String application, UUID resource) {
    wireMockServer.verify(postRequestedFor(downloadExportErrorRequest(id, application, resource)));
  }

  public void verifyDownloadExportUpload(UUID id, String application, UUID resource) {
    wireMockServer.verify(postRequestedFor(downloadExportErrorUpload(id, application, resource)));
  }

  private UrlPathPattern downloadExportErrorRequest(UUID id, String application, UUID resource) {
    return urlPathEqualTo("/app/export/v1/" + id + "/" + application + "/" + resource + "/error");
  }

  private UrlPathPattern downloadExportErrorUpload(UUID id, String application, UUID resource) {
    return urlPathEqualTo("/app/export/v1/" + id + "/" + application + "/" + resource + "/upload");
  }
}
