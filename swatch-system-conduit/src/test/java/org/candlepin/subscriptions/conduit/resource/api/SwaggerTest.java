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
package org.candlepin.subscriptions.conduit.resource.api;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Stream;
import org.candlepin.subscriptions.ConduitBaseTest;
import org.candlepin.subscriptions.conduit.security.ApiSecurityConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * This test verifies that all URLs listed in URLS_PERMITTED_WITHOUT_AUTH in
 * ApiSecurityConfiguration are not blocked by Spring Security when no authentication is provided.
 * The assertion is that requests do not return 401 Unauthorized; some URLs may return redirects,
 * 404s, or other non-auth-related responses depending on whether the backing resource exists.
 */
class SwaggerTest extends ConduitBaseTest {

  /**
   * Maps wildcard patterns from URLS_PERMITTED_WITHOUT_AUTH to concrete URLs that should match
   * them. Patterns without wildcards are tested directly.
   */
  private static final Map<String, String> WILDCARD_TO_CONCRETE =
      Map.of(
          "/api/rhsm-subscriptions/v1/*openapi.yaml",
              "/api/rhsm-subscriptions/v1/internal-organizations-sync-openapi.yaml",
          "/api/rhsm-subscriptions/v1/*openapi.json",
              "/api/rhsm-subscriptions/v1/internal-organizations-sync-openapi.json",
          "/webjars/**", "/webjars/swagger-ui/index.css");

  private RestTestClient restClient;

  @BeforeEach
  void setup() {
    restClient = RestTestClient.bindToServer().baseUrl(basePath()).build();
  }

  static Stream<Arguments> urlsPermittedWithoutAuth() {
    return Stream.of(ApiSecurityConfiguration.URLS_PERMITTED_WITHOUT_AUTH)
        .map(
            pattern -> {
              String concreteUrl = WILDCARD_TO_CONCRETE.getOrDefault(pattern, pattern);
              return Arguments.of(pattern, concreteUrl);
            });
  }

  @ParameterizedTest(name = "pattern \"{0}\" -> {1}")
  @MethodSource("urlsPermittedWithoutAuth")
  void testUrlsPermittedWithoutAuth(String pattern, String concreteUrl) {
    restClient
        .get()
        .uri(concreteUrl)
        .exchange()
        .expectStatus()
        .value(status -> assertNotEquals(401, status, concreteUrl + " should not require auth"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"swatch-system-conduit"})
  void testSwaggerPage(String app) {
    restClient
        .get()
        .uri("/api/" + app + "/internal/swagger-ui/index.html")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(Assertions::assertNotNull)
        .value(body -> assertTrue(body.contains("API Docs")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/swatch-system-conduit/internal/swagger-ui",
        "/api/swatch-system-conduit/internal/swagger-ui/",
      })
  void testSwaggerRedirects(String url) {
    restClient.get().uri(url).exchange().expectStatus().is3xxRedirection();
  }
}
