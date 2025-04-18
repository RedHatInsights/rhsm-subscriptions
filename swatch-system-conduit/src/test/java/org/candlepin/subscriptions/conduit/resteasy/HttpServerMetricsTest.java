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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.subscriptions.ConduitBaseTest;
import org.candlepin.subscriptions.conduit.InventoryController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class HttpServerMetricsTest extends ConduitBaseTest {

  @Autowired private TestRestTemplate restTemplate;

  @MockitoBean InventoryController controller;

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
    ResponseEntity<String> response =
        restTemplate.exchange(
            apiBasePath() + "/internal/organizations/org123/inventory?limit=1",
            HttpMethod.GET,
            request(),
            String.class);
    assertTrue(
        response.getStatusCode().is2xxSuccessful(),
        () -> "Unexpected response status: " + response.getStatusCode());
  }

  private void whenUsingTheServerApiWithError() {
    ResponseEntity<String> response =
        restTemplate.exchange(
            apiBasePath() + "/internal/organizations/org123/inventory?limit=1",
            HttpMethod.GET,
            // this causes the server API to fail because we're
            // not sending the identity header
            new HttpEntity<Void>(new HttpHeaders()),
            String.class);
    assertTrue(
        response.getStatusCode().is4xxClientError(),
        () -> "Unexpected response status: " + response.getStatusCode());
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
    return restTemplate
        .exchange(managementBasePath() + "/metrics", HttpMethod.GET, request(), String.class)
        .getBody();
  }
}
