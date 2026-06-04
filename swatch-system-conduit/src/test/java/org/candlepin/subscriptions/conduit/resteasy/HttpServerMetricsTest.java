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
package org.candlepin.subscriptions.conduit.resteasy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.subscriptions.security.IdentityHeaderAuthenticationFilter.RH_IDENTITY_HEADER;

import org.candlepin.subscriptions.ConduitBaseTest;
import org.candlepin.subscriptions.conduit.InventoryController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

class HttpServerMetricsTest extends ConduitBaseTest {

  private RestTestClient restClient;
  private RestTestClient mgmtRestClient;

  @MockitoBean InventoryController controller;

  @BeforeEach
  void setup() {
    restClient = RestTestClient.bindToServer().baseUrl(apiBasePath()).build();
    mgmtRestClient = RestTestClient.bindToServer().baseUrl(managementBasePath()).build();
  }

  @Test
  void testShouldProduceHttpMetrics() {
    // verify no http server metrics are found because we have not used the API server yet
    verifyMetricsDoNotContainAnyOf("http_server_");
    // let's use the api now
    whenUsingTheServerApi();
    // and now the http server metrics should be found.
    assertMetricIsFoundWithSuccess();
    // let's now use the api but without the header, so an error is expected
    whenUsingTheServerApiWithError();
    // and next the http server metrics with error should also be found.
    assertMetricIsFoundWithError();
  }

  private void verifyMetricsDoNotContainAnyOf(String text) {
    assertThat(getMetrics()).doesNotContain(text);
  }

  private void verifyMetricsContainAnyOf(String text) {
    assertThat(getMetrics()).contains(text);
  }

  private void whenUsingTheServerApi() {
    restClient
        .get()
        .uri("/internal/organizations/org123/inventory?limit=1")
        .header(RH_IDENTITY_HEADER, user())
        .exchange()
        .expectStatus()
        .is2xxSuccessful();
  }

  private void whenUsingTheServerApiWithError() {
    restClient
        .get()
        .uri("/internal/organizations/org123/inventory?limit=1")
        .exchange()
        .expectStatus()
        .is4xxClientError();
  }

  private void assertMetricIsFoundWithSuccess() {
    verifyMetricsContainAnyOf(
        "http_server_requests_seconds_count{error=\"none\","
            + "exception=\"none\","
            + "method=\"GET\","
            + "outcome=\"SUCCESS\","
            + "status=\"204\","
            + "uri=\"/api/rhsm-subscriptions/v1/internal/organizations/{org_id}/inventory\"} 1");
  }

  private void assertMetricIsFoundWithError() {
    verifyMetricsContainAnyOf(
        "http_server_requests_seconds_count{error=\"none\","
            + "exception=\"none\","
            + "method=\"GET\","
            + "outcome=\"CLIENT_ERROR\","
            + "status=\"401\","
            + "uri=\"/api/rhsm-subscriptions/v1/internal/organizations/org123/inventory\"} 1");
  }

  private String getMetrics() {
    var result =
        mgmtRestClient
            .get()
            .uri("/metrics")
            .header(RH_IDENTITY_HEADER, user())
            .exchange()
            .expectBody(String.class)
            .returnResult();
    return result.getResponseBody();
  }
}
