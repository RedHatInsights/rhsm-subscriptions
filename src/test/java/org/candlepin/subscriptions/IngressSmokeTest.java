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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.utilization.api.model.CandlepinPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

@Slf4j
@Tag("smoke")
class IngressSmokeTest {
  private static final ObjectMapper mapper = new ObjectMapper();

  WebTestClient client;

  /** To be overridden if used in an integration test */
  protected int getServerPort() {
    return 8080;
  }

  /** Can be overridden to point to a different host */
  protected String getServerHost() {
    return "localhost";
  }

  @BeforeEach
  void setup() {
    client =
        WebTestClient.bindToServer()
            .uriBuilderFactory(
                new DefaultUriBuilderFactory(
                    String.format(
                        "http://%s:%d/api/rhsm-subscriptions/v1",
                        getServerHost(), getServerPort())))
            .build();
  }

  @Test
  void testIngress() {
    CandlepinPool pool =
        new CandlepinPool()
            .accountNumber("account123")
            .activeSubscription(true)
            .startDate(OffsetDateTime.parse("2019-01-01T00:00Z"))
            .endDate(OffsetDateTime.parse("2021-01-01T00:00Z"))
            .quantity(1L)
            .productId("MW01484")
            .subscriptionId("12345")
            .type("NORMAL");
    client
        .post()
        .uri("/ingress/candlepin_pools/{org}", "org123")
        .bodyValue(List.of(pool))
        .exchange()
        .expectStatus()
        .isNoContent();
  }
}
