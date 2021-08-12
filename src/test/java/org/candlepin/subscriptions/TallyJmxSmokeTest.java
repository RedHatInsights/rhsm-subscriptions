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

import static org.candlepin.subscriptions.IntegrationTestUtils.auth;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.RequestHeadersSpec;
import org.springframework.web.util.DefaultUriBuilderFactory;

@Slf4j
@Tag("smoke")
class TallyJmxSmokeTest {
  WebTestClient client;

  @BeforeEach
  void setup() {
    client =
        WebTestClient.bindToServer()
            .uriBuilderFactory(
                new DefaultUriBuilderFactory(
                    String.format("http://%s:%d/", getServerHost(), getServerPort())))
            .defaultHeaders(auth(IntegrationTestUtils.ADMIN_IDENTITY))
            .defaultHeader("Origin", "example.redhat.com")
            .build();
  }

  /** To be overridden if used in an integration test */
  protected int getServerPort() {
    return 8080;
  }

  /** Can be overridden to point to a different host */
  protected String getServerHost() {
    return "localhost";
  }

  @Test
  void testTallyViaJmx() {
    log.info("{}", getServerPort());
    Double initialCount = fetchTaskCount();
    log.info("Initial tally count: {}", initialCount);
    invokeViaJmx(
            client,
            "org.candlepin.subscriptions.jmx:name=tallyJmxBean,type=TallyJmxBean",
            "tallyAccount(java.lang.String)",
            List.of("account123"))
        .exchange()
        .expectStatus()
        .isOk();
    RetryTemplate.builder()
        .fixedBackoff(200)
        .maxAttempts(15)
        .build()
        .execute(
            execution -> {
              Double currentCount = fetchTaskCount();
              if (currentCount != initialCount + 1.0) {
                throw new RuntimeException("count not incremented yet!");
              }
              assertEquals(currentCount, initialCount + 1.0);
              log.info("Final tally count: {}", currentCount);
              return null;
            });
  }

  RequestHeadersSpec<?> invokeViaJmx(
      WebTestClient client, String mbean, String operation, List<String> arguments) {
    Map<String, Object> request = new HashMap<>();
    request.put("type", "exec");
    request.put("mbean", mbean);
    request.put("operation", operation);
    request.put("arguments", arguments);
    return client
        .post()
        .uri("/actuator/hawtio/jolokia")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request);
  }

  @Nullable
  private Double fetchTaskCount() {
    return client
        .get()
        .uri("/actuator/prometheus")
        .exchange()
        .returnResult(String.class)
        .getResponseBody()
        .filter(line -> line.startsWith("spring_kafka_listener_seconds_count"))
        .filter(line -> line.contains("exception=\"none\""))
        .filter(line -> line.contains("rhsm-subscriptions-task-processor"))
        .defaultIfEmpty(" 0.0")
        .map(line -> line.substring(line.indexOf(" ") + 1))
        .map(Double::parseDouble)
        .blockFirst();
  }
}
