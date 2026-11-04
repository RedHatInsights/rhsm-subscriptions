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
package com.redhat.swatch.gcp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.Test;

@QuarkusTest
class GcpUsageResourceTest {
  @Inject ObjectMapper mapper;
  @Inject GcpBillableUsageAggregateConsumer consumer;

  @Inject
  @Connector("smallrye-in-memory")
  InMemoryConnector connector;

  static String example() throws IOException {
    return Files.readString(Path.of("examples/usage.json"));
  }

  @Test
  void previewsBothRequestsWithExactCustomerMappingAndStableId() throws IOException {
    for (int retry = 0; retry < 2; retry++) {
      given()
          .contentType("application/json")
          .body(example())
          .when()
          .post("/api/swatch-producer-gcp/poc/usage")
          .then()
          .statusCode(200)
          .body("status", equalTo("DRY_RUN"))
          .body(
              "checkRequest.operation.operationId", equalTo("38959044-976c-4cb4-ae2e-a24116872f3c"))
          .body(
              "reportRequest.operations[0].operationId",
              equalTo("38959044-976c-4cb4-ae2e-a24116872f3c"))
          .body("reportRequest.operations[0].consumerId", equalTo("project_number:123456789012"))
          .body("reportRequest.operations[0].startTime", equalTo("2026-11-04T12:00:00Z"))
          .body("reportRequest.operations[0].endTime", equalTo("2026-11-04T13:00:00Z"))
          .body(
              "reportRequest.operations[0].metricValueSets[0].metricValues[0].int64Value",
              equalTo("150"));
    }
  }

  @Test
  void rejectsUnknownCustomersMetricsFractionalQuantitiesAndMixedUsageHours() throws IOException {
    for (String invalid :
        new String[] {
          example().replace("example-org", "another-org"),
          example().replace("example-billing-account", "another-account"),
          example().replace("Instance-hours", "unknown-metric"),
          example().replace("150", "1.5"),
          example().replace("150", "9223372036854775808"),
          example().replace("150", "-1"),
          example().replace("[\"2026-11-04T12:00:00Z\"]", "[]"),
          example()
              .replace(
                  "[\"2026-11-04T12:00:00Z\"]",
                  "[\"2026-11-04T12:00:00Z\",\"2026-11-04T11:00:00Z\"]")
        }) {
      given()
          .contentType("application/json")
          .body(invalid)
          .when()
          .post("/api/swatch-producer-gcp/poc/usage")
          .then()
          .statusCode(400);
    }
  }

  @Test
  void dryRunDoesNotPublishBilledStatusAndOtherProvidersAreIgnored() throws IOException {
    var sink = connector.sink("billable-usage-status-out");
    sink.clear();
    consumer.process(mapper.readValue(example(), BillableUsageAggregate.class));
    consumer.process(
        mapper.readValue(example().replace("gcp", "aws"), BillableUsageAggregate.class));
    assertTrue(sink.received().isEmpty());
  }
}
