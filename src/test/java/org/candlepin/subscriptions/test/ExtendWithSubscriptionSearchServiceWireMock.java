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
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequest;
import com.redhat.cloud.event.parser.GenericConsoleCloudEvent;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.shaded.org.awaitility.Awaitility;

public interface ExtendWithSubscriptionSearchServiceWireMock {

  WireMockServer SUBSCRIPTION_SERVICE_WIRE_MOCK_SERVER = startWireMockServer();

  @BeforeEach
  default void resetExportServiceWireMock() {
    SUBSCRIPTION_SERVICE_WIRE_MOCK_SERVER.resetAll();
    SUBSCRIPTION_SERVICE_WIRE_MOCK_SERVER.stubFor(
        post(urlPathMatching("/search/criteria;subscription_number=*")));
  }

  @DynamicPropertySource
  static void registerExportApiProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "rhsm-subscriptions.subscription.url", SUBSCRIPTION_SERVICE_WIRE_MOCK_SERVER::baseUrl);
  }

  default void verifyNoRequestsWereSentToExportServiceWithError(
      GenericConsoleCloudEvent<ResourceRequest> request) {
    SUBSCRIPTION_SERVICE_WIRE_MOCK_SERVER.verify(
        0,
        postRequestedFor(
            urlEqualTo(
                String.format(
                    "/app/export/v1/%s/subscriptions/%s/error",
                    request.getData().getResourceRequest().getUUID(),
                    request.getData().getResourceRequest().getExportRequestUUID()))));
  }

  default void verifyRequestWasSentToExportServiceWithError(
      GenericConsoleCloudEvent<ResourceRequest> request) {
    Awaitility.await()
        .untilAsserted(
            () ->
                SUBSCRIPTION_SERVICE_WIRE_MOCK_SERVER.verify(
                    postRequestedFor(
                        urlEqualTo(
                            String.format(
                                "/app/export/v1/%s/subscriptions/%s/error",
                                request.getData().getResourceRequest().getExportRequestUUID(),
                                request.getData().getResourceRequest().getUUID())))));
  }

  default void verifyNoRequestsWereSentToExportServiceWithUploadData(
      GenericConsoleCloudEvent<ResourceRequest> request) {
    SUBSCRIPTION_SERVICE_WIRE_MOCK_SERVER.verify(
        0,
        postRequestedFor(
            urlEqualTo(
                String.format(
                    "/app/export/v1/%s/subscriptions/%s/upload",
                    request.getData().getResourceRequest().getExportRequestUUID(),
                    request.getData().getResourceRequest().getUUID()))));
  }

  default void verifyRequestWasSentToExportServiceWithUploadData(
      GenericConsoleCloudEvent<ResourceRequest> request, String expected) {
    Awaitility.await()
        .untilAsserted(
            () ->
                SUBSCRIPTION_SERVICE_WIRE_MOCK_SERVER.verify(
                    postRequestedFor(
                            urlEqualTo(
                                String.format(
                                    "/app/export/v1/%s/subscriptions/%s/upload",
                                    request.getData().getResourceRequest().getExportRequestUUID(),
                                    request.getData().getResourceRequest().getUUID())))
                        .withRequestBody(equalTo(expected))));
  }

  static WireMockServer startWireMockServer() {
    var wireMockServer =
        new WireMockServer(wireMockConfig().dynamicPort().notifier(new ConsoleNotifier(true)));
    wireMockServer.start();
    System.out.printf("Running subscription search service on port %d%n", wireMockServer.port());
    return wireMockServer;
  }
}
