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
package com.redhat.swatch.metrics.admin.api;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.put;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.metrics.configuration.ApplicationConfiguration;
import com.redhat.swatch.metrics.service.PrometheusMeteringController;
import com.redhat.swatch.metrics.service.PrometheusMetricsTaskManager;
import com.redhat.swatch.metrics.test.resources.InMemoryMessageBrokerKafkaResource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import org.apache.http.HttpStatus;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
class MetricsTest {

  private static final String VALID_PRODUCT = "rosa";

  @Inject ApplicationClock clock;
  @InjectMock PrometheusMetricsTaskManager tasks;
  @InjectMock PrometheusMeteringController controller;
  @InjectMock ApplicationConfiguration applicationConfiguration;

  @Test
  void testServiceIsUpAndRunning() {
    // health resource is up and running
    get("/q/health").then().statusCode(HttpStatus.SC_OK);
    // openapi resources are up and running
    get("/api/swatch-metrics/internal/openapi").then().statusCode(HttpStatus.SC_OK);
    get("/api/swatch-metrics/internal/swagger-ui").then().statusCode(HttpStatus.SC_OK);
  }

  @Test
  void testBadRequestIsReturnedIfProductTagIsInvalid() {
    meterProductForOrgIdAndRange("test-product", "org1", clock.startOfCurrentHour(), 120, false)
        .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  void testMetersUsingDefaultRange() {
    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(60);
    meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", endDate, null, false);
    verify(tasks).updateMetricsForOrgId("org1", VALID_PRODUCT, startDate, endDate);
    verifyNoInteractions(controller);
  }

  @Test
  void testEnsureMeterProductValidatesDateRange() {
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusMinutes(5L);
    // asynchronous
    meterProductForOrgIdAndRange(VALID_PRODUCT, null, end, 120, false)
        .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", clock.startOfHour(end), 120, false)
        .statusCode(HttpStatus.SC_NO_CONTENT);

    // synchronous
    meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", end, 120, true)
        .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);

    // Avoid additional exception by enabling synchronous operations.
    when(applicationConfiguration.isEnableSynchronousOperations()).thenReturn(true);
    meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", clock.startOfHour(end), 120, true)
        .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  @Test
  void testPreventSynchronousMeteringForAccountWhenSyncRequestsDisabled() {
    OffsetDateTime end = clock.startOfCurrentHour();
    meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", end, 120, true)
        .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  void testAllowAsynchronousMeteringForAccountWhenSyncRequestsDisabled() {
    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(120);
    meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", endDate, 120, false);
    verify(tasks).updateMetricsForOrgId("org1", VALID_PRODUCT, startDate, endDate);
    verifyNoInteractions(controller);
  }

  @Test
  void testAllowSynchronousMeteringForAccountWhenSyncRequestsEnabled() {
    when(applicationConfiguration.isEnableSynchronousOperations()).thenReturn(true);

    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(120);
    meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", endDate, 120, true);
    verify(controller)
        .collectMetrics(
            VALID_PRODUCT, MetricId.fromString("Instance-hours"), "org1", startDate, endDate);
    verifyNoInteractions(tasks);
  }

  @Test
  void testPerformAsynchronousMeteringForAccountWhenHeaderIsFalseAndSynchronousEnabled() {
    when(applicationConfiguration.isEnableSynchronousOperations()).thenReturn(true);

    OffsetDateTime endDate = clock.startOfCurrentHour();
    OffsetDateTime startDate = endDate.minusMinutes(120);
    meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", endDate, 120, false);
    verify(tasks).updateMetricsForOrgId("org1", VALID_PRODUCT, startDate, endDate);
    verifyNoInteractions(controller);
  }

  @Test
  void testRangeInMinutesMustBeNonNegative() {
    OffsetDateTime endDate = clock.startOfCurrentHour();
    meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", endDate, -1, false)
        .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  void testEndDateMustBeAtStartOfHour() {
    OffsetDateTime endDate = clock.now();
    meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", endDate, 120, false)
        .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void testBadRangeThrowsException() {
    OffsetDateTime endDate = clock.startOfCurrentHour();
    meterProductForOrgIdAndRange(VALID_PRODUCT, "org1", endDate, 13, false)
        .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void testSyncMetricsForAllAccounts() {
    syncMetricsForAllAccounts();

    verify(tasks).updateMetricsForAllAccounts("OpenShift-metrics");
  }

  private ValidatableResponse meterProductForOrgIdAndRange(
      String productTag,
      String orgId,
      OffsetDateTime endDate,
      Integer rangeInMinutes,
      Boolean xRhSwatchSynchronousRequest) {
    RequestSpecification request = given().queryParam("orgId", orgId);

    if (endDate != null) {
      request = request.queryParam("endDate", endDate.toString());
    }

    if (rangeInMinutes != null) {
      request = request.queryParam("rangeInMinutes", rangeInMinutes);
    }

    if (xRhSwatchSynchronousRequest != null) {
      request = request.header("x-rh-swatch-synchronous-request", xRhSwatchSynchronousRequest);
    }

    return request.post("/api/swatch-metrics/v1/internal/metering/" + productTag).then();
  }

  private void syncMetricsForAllAccounts() {
    put("/api/swatch-metrics/v1/internal/metering/sync")
        .then()
        .statusCode(HttpStatus.SC_NO_CONTENT);
  }
}
