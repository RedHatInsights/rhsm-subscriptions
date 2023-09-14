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
package org.candlepin.subscriptions.resteasy;

import static org.apache.commons.codec.binary.Base64.encodeBase64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.subscriptions.security.IdentityHeaderAuthenticationFilter.RH_IDENTITY_HEADER;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "DEV_MODE=true",
      // enable grabbing metrics in tests
      "management.prometheus.metrics.export.enabled=true",
      // use a random port in management server
      "management.server.port=0",
    })
@Tag("integration")
@ActiveProfiles({"api", "test"})
class HttpServerMetricsTest {

  private static final String IDENTITY_TEMPLATE =
      "{\"identity\":{\"type\":\"User\",\"internal\":{\"org_id\":\"%s\"}}}";
  private static final String ORG_ID = "org123";
  private static final String LOCALHOST = "http://localhost:";

  @LocalServerPort private int port;

  @LocalManagementPort private int mgt;

  @Autowired private TestRestTemplate restTemplate;

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
            apiBasePath() + "/api/rhsm-subscriptions/v1/opt-in",
            HttpMethod.PUT,
            request(),
            String.class);
    assertTrue(
        response.getStatusCode().is2xxSuccessful(),
        () -> "Unexpected response status: " + response.getStatusCode());
  }

  private void whenUsingTheServerApiWithError() {
    ResponseEntity<String> response =
        restTemplate.exchange(
            apiBasePath() + "/api/rhsm-subscriptions/v1/opt-in",
            HttpMethod.PUT,
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
            + "method=\"PUT\","
            + "outcome=\"SUCCESS\","
            + "status=\"200\","
            + "uri=\"/api/rhsm-subscriptions/v1/opt-in\",} 1.0");
  }

  private void assertMetricIsFoundWithError() {
    verifyMetricsContainAnyOf(
        "http_server_requests_seconds_count{error=\"none\","
            + "exception=\"none\","
            + "method=\"PUT\","
            + "outcome=\"CLIENT_ERROR\","
            + "status=\"401\","
            + "uri=\"/api/rhsm-subscriptions/v1/opt-in\",} 1.0");
  }

  private HttpEntity<Void> request() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(RH_IDENTITY_HEADER, user());

    return new HttpEntity<>(headers);
  }

  private String user() {
    String identity = String.format(IDENTITY_TEMPLATE, ORG_ID);
    return new String(encodeBase64(identity.getBytes(StandardCharsets.UTF_8)));
  }

  private String getMetrics() {
    return restTemplate
        .exchange(managementBasePath() + "/metrics", HttpMethod.GET, request(), String.class)
        .getBody();
  }

  private String apiBasePath() {
    return LOCALHOST + port;
  }

  private String managementBasePath() {
    return LOCALHOST + mgt;
  }
}
