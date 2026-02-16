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
package com.redhat.swatch.contract.test.resources;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequest;
import com.redhat.cloud.event.parser.GenericConsoleCloudEvent;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import java.util.UUID;
import org.testcontainers.shaded.org.awaitility.Awaitility;

public class ExportServiceWireMockResource implements QuarkusTestResourceLifecycleManager {

  private WireMockServer wireMockServer;

  @Override
  public Map<String, String> start() {
    wireMockServer = startWireMockServer();
    setup();
    return Map.of("clowder.private-endpoints.export-service-service.url", wireMockServer.baseUrl());
  }

  @Override
  public void stop() {
    if (wireMockServer != null) {
      wireMockServer.stop();
      wireMockServer = null;
    }
  }

  @Override
  public void inject(TestInjector testInjector) {
    testInjector.injectIntoFields(
        this,
        new TestInjector.AnnotatedAndMatchesType(
            InjectWireMock.class, ExportServiceWireMockResource.class));
  }

  public void setup() {
    wireMockServer.resetAll();
    wireMockServer.stubFor(post(urlPathMatching("/app/export/.*")));
  }

  public void verifyNoRequestsWereSentToExportServiceWithError(
      GenericConsoleCloudEvent<ResourceRequest> request) {
    wireMockServer.verify(
        0,
        postRequestedFor(
            urlEqualTo(
                String.format(
                    "/app/export/v1/%s/subscriptions/%s/error",
                    request.getData().getResourceRequest().getUUID(),
                    request.getData().getResourceRequest().getExportRequestUUID()))));
  }

  public void verifyRequestWasSentToExportServiceWithError(
      GenericConsoleCloudEvent<ResourceRequest> request, String message) {
    Awaitility.await()
        .untilAsserted(
            () ->
                wireMockServer.verify(
                    postRequestedFor(
                            urlEqualTo(
                                String.format(
                                    "/app/export/v1/%s/subscriptions/%s/error",
                                    request.getData().getResourceRequest().getExportRequestUUID(),
                                    request.getData().getResourceRequest().getUUID())))
                        .withRequestBody(containing(message))));
  }

  public void verifyNoRequestsWereSentToExportServiceWithUploadData(
      GenericConsoleCloudEvent<ResourceRequest> request) {
    wireMockServer.verify(
        0,
        postRequestedFor(
            urlEqualTo(
                String.format(
                    "/app/export/v1/%s/subscriptions/%s/upload",
                    request.getData().getResourceRequest().getExportRequestUUID(),
                    request.getData().getResourceRequest().getUUID()))));
  }

  public void verifyRequestWasSentToExportServiceWithUploadData(
      GenericConsoleCloudEvent<ResourceRequest> request, String expected) {
    Awaitility.await()
        .untilAsserted(
            () ->
                wireMockServer.verify(
                    postRequestedFor(
                            urlEqualTo(
                                String.format(
                                    "/app/export/v1/%s/subscriptions/%s/upload",
                                    request.getData().getResourceRequest().getExportRequestUUID(),
                                    request.getData().getResourceRequest().getUUID())))
                        .withHeader("x-rh-exports-psk", equalTo("placeholder"))
                        .withRequestBody(equalTo(expected))));
  }

  public void mockRequestToReturnGatewayTimeout(GenericConsoleCloudEvent<ResourceRequest> request) {
    wireMockServer.stubFor(
        post(urlMatching(
                String.format(
                    "/app/export/v1/%s/subscriptions/%s/.*",
                    request.getData().getResourceRequest().getExportRequestUUID(),
                    request.getData().getResourceRequest().getUUID())))
            .willReturn(status(HttpResponseStatus.GATEWAY_TIMEOUT.code())));
  }

  public void verifyUploadWasSent(UUID exportId, String application, UUID resourceId) {
    Awaitility.await()
        .untilAsserted(
            () ->
                wireMockServer.verify(
                    postRequestedFor(
                        urlPathMatching(
                            "/app/export/v1/"
                                + exportId
                                + "/"
                                + application
                                + "/"
                                + resourceId
                                + "/upload"))));
  }

  private static WireMockServer startWireMockServer() {
    var wireMockServer =
        new WireMockServer(
            wireMockConfig()
                .dynamicPort()
                .notifier(new ConsoleNotifier(true))
                // This is mandatory to handle large files, otherwise Wiremock returns 500 Server
                // Error
                .jettyHeaderRequestSize(16384)
                .jettyHeaderResponseSize(80000)
                .stubRequestLoggingDisabled(true)
                .maxLoggedResponseSize(1000));
    wireMockServer.start();
    System.out.printf("Running export service on port %d%n", wireMockServer.port());
    return wireMockServer;
  }
}
