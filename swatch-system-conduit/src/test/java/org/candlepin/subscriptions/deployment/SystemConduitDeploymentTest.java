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
package org.candlepin.subscriptions.deployment;

import static com.redhat.swatch.traceresponse.TraceResponseFilter.TRACE_RESPONSE_HEADER;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.subscriptions.ConduitBaseTest;
import org.candlepin.subscriptions.SystemConduitApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

class SystemConduitDeploymentTest extends ConduitBaseTest {

  @Autowired SystemConduitApplication configuration;

  private final TestRestTemplate restTemplate =
      new TestRestTemplate(TestRestTemplate.HttpClientOption.ENABLE_REDIRECTS);

  @Test
  void testDeployment() {
    assertNotNull(configuration);
  }

  @Test
  void testSwaggerPage() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(
            basePath() + "/api/swatch-system-conduit/internal/swagger-ui", String.class);
    assertTrue(response.getStatusCode().is2xxSuccessful());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().contains("API Docs"));
  }

  @Test
  void testTraceResponseHeader() {
    ResponseEntity<Object> response =
        restTemplate.exchange(
            apiBasePath() + "/internal/organizations-sync-list/123",
            HttpMethod.GET,
            request(),
            Object.class);
    assertTrue(response.getHeaders().containsKey(TRACE_RESPONSE_HEADER));
    String traceResponse = response.getHeaders().getFirst(TRACE_RESPONSE_HEADER);
    assertNotNull(traceResponse);
    assertTrue(traceResponse.startsWith("00-"));
  }
}
