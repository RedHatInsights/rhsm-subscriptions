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
package org.candlepin.subscriptions;

import java.net.URI;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.Builder;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;

@Tag("smoke")
class ApiSmokeTest {

  WebTestClient anon;
  WebTestClient user;

  /** To be overridden if used in an integration test */
  protected int getServerPort() {
    return 8080;
  }

  /** Can be overridden to point to a different host */
  protected String getServerHost() {
    return "localhost";
  }

  protected Builder configureClient() {
    return WebTestClient.bindToServer()
        .defaultHeader("Origin", "example.redhat.com") // for anti-csrf
        .uriBuilderFactory(
            new DefaultUriBuilderFactory(
                String.format(
                    "http://%s:%d/api/rhsm-subscriptions/v1", getServerHost(), getServerPort())));
  }

  @BeforeEach
  void setup() {
    anon = configureClient().build();
    user =
        configureClient()
            .defaultHeaders(IntegrationTestUtils.auth(IntegrationTestUtils.DEFAULT_IDENTITY))
            .build();
    // ensure opted in
    user.put().uri("/opt-in").exchange().expectStatus().isOk();
  }

  Function<UriBuilder, URI> createReportURI(String basePath) {
    return uriBuilder ->
        uriBuilder
            .path(basePath)
            .queryParam("granularity", "Daily")
            .queryParam("beginning", "2021-07-01T00:00:00Z")
            .queryParam("ending", "2021-07-05T00:00:00Z")
            .build("OpenShift Container Platform");
  }

  @Test
  void testVersion() {
    anon.get().uri("/version").exchange().expectStatus().isOk();
    user.get().uri("/version").exchange().expectStatus().isOk();
  }

  @Test
  void testOptIn() {
    anon.get().uri("/opt-in").exchange().expectStatus().isUnauthorized();
    anon.delete().uri("/opt-in").exchange().expectStatus().isUnauthorized();
    anon.put().uri("/opt-in").exchange().expectStatus().isUnauthorized();

    user.get()
        .uri("/opt-in")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.meta")
        .exists();

    user.delete().uri("/opt-in").exchange().expectStatus().isNoContent();

    user.put().uri("/opt-in").exchange().expectStatus().isOk();
  }

  @Test
  void testTally() {
    anon.get()
        .uri(createReportURI("/tally/products/{product}"))
        .exchange()
        .expectStatus()
        .isUnauthorized();

    user.get().uri(createReportURI("/tally/products/{product}")).exchange().expectStatus().isOk();
  }

  @Test
  void testCapacity() {
    anon.get()
        .uri(createReportURI("/capacity/products/{product}"))
        .exchange()
        .expectStatus()
        .isUnauthorized();

    user.get()
        .uri(createReportURI("/capacity/products/{product}"))
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void testHosts() {
    anon.get()
        .uri(createReportURI("/hosts/products/{product}"))
        .exchange()
        .expectStatus()
        .isUnauthorized();
    anon.get()
        .uri("/hosts/{hostId}/guests", UUID.randomUUID())
        .exchange()
        .expectStatus()
        .isUnauthorized();

    user.get().uri(createReportURI("/hosts/products/{product}")).exchange().expectStatus().isOk();
    user.get().uri("/hosts/{hostId}/guests", UUID.randomUUID()).exchange().expectStatus().isOk();
  }
}
