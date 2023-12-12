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
package org.candlepin.subscriptions.resource.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * This test covers the logic in ResteasyConfiguration and the static swagger resources (to ensure
 * that point out to the correct openapi endpoints).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SwaggerTest {

  private static final String LOCALHOST = "http://localhost:";

  @LocalServerPort int port;

  private final TestRestTemplate restTemplate =
      new TestRestTemplate(TestRestTemplate.HttpClientOption.ENABLE_REDIRECTS);

  @ParameterizedTest
  @ValueSource(
      strings = {
        "swatch-subscription-sync",
        "swatch-tally",
        "swatch-billing",
        "swatch-producer-red-hat-marketplace"
      })
  void testSwaggerPage(String app) {
    ResponseEntity<String> response =
        restTemplate.getForEntity(
            LOCALHOST + port + "/api/" + app + "/internal/swagger-ui", String.class);
    assertNotNull(response.getBody());
    assertTrue(response.getBody().contains("API Docs"));
  }
}
