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
package api;

import io.restassured.http.ContentType;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.candlepin.clock.ApplicationClock;

/**
 * Facade for stubbing the swatch-contracts Capacity API endpoints via Wiremock.
 *
 * <p>The swatch-tally service (rhsm-subscriptions) calls swatch-contracts to get capacity data when
 * {@code billing_category} is specified in a tally report request. This stub class allows component
 * tests to mock the capacity API response so we can test the prepaid/on-demand split logic in
 * isolation.
 *
 * @see <a href="https://issues.redhat.com/browse/SWATCH-3288">SWATCH-3288</a>
 */
public class CapacityApiStubs {

  private static final ApplicationClock clock = new ApplicationClock();

  private final TallyWiremockService wiremockService;

  public CapacityApiStubs(TallyWiremockService wiremockService) {
    this.wiremockService = wiremockService;
  }

  /**
   * Stub the capacity API to return a fixed capacity value ONLY when the request includes a
   * specific {@code billing_account_id} query parameter.
   *
   * <p>This simulates the real swatch-contracts behavior: it returns capacity when
   * billing_account_id matches a known contract. When billing_account_id is absent or null, the
   * request falls through to the catch-all stub (priority 10) which returns capacity=0.
   *
   * <p>This is the key to reproducing SWATCH-3288: TallyResource passes null for billing_account_id
   * when the user omits it from the tally API request, so the capacity stub with the specific
   * billing_account_id won't match, and the catch-all returns 0.
   *
   * @param productId the product ID (e.g., "rosa")
   * @param metricId the metric ID (e.g., "Cores")
   * @param capacityValue the capacity value to return for each day
   * @param billingAccountId the billing account ID that must be present in the request
   * @param beginning start of the date range
   * @param ending end of the date range
   */
  public void stubCapacityReportWithBillingAccountId(
      String productId,
      String metricId,
      int capacityValue,
      String billingAccountId,
      OffsetDateTime beginning,
      OffsetDateTime ending) {

    List<Map<String, Object>> dataPoints =
        buildCapacityDataPoints(capacityValue, beginning, ending);

    Map<String, Object> responseBody =
        Map.of(
            "data",
            dataPoints,
            "meta",
            Map.of(
                "count",
                dataPoints.size(),
                "product",
                productId,
                "metric_id",
                metricId,
                "granularity",
                "Daily"),
            "links",
            Map.of());

    String urlPath =
        String.format("/api/rhsm-subscriptions/v1/capacity/products/%s/%s", productId, metricId);

    // Priority 1 (high): matches only when billing_account_id query param is present
    wiremockService
        .given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "GET",
                    "urlPathPattern",
                    urlPath,
                    "queryParameters",
                    Map.of("billing_account_id", Map.of("equalTo", billingAccountId))),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "jsonBody",
                    responseBody),
                "priority",
                1,
                "metadata",
                wiremockService.getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);

    // Priority 10 (low, catch-all): matches any request to this path WITHOUT the specific
    // billing_account_id. Returns capacity=0, simulating what happens in stage when
    // TallyResource passes null for billing_account_id.
    List<Map<String, Object>> emptyDataPoints = buildCapacityDataPoints(0, beginning, ending);

    Map<String, Object> emptyResponseBody =
        Map.of(
            "data",
            emptyDataPoints,
            "meta",
            Map.of(
                "count",
                emptyDataPoints.size(),
                "product",
                productId,
                "metric_id",
                metricId,
                "granularity",
                "Daily"),
            "links",
            Map.of());

    wiremockService
        .given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "request",
                Map.of("method", "GET", "urlPathPattern", urlPath),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "jsonBody",
                    emptyResponseBody),
                "priority",
                10,
                "metadata",
                wiremockService.getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }

  /**
   * Stub the capacity API to return a fixed capacity value for any request (no billing_account_id
   * filtering).
   *
   * @param productId the product ID (e.g., "rosa")
   * @param metricId the metric ID (e.g., "Cores")
   * @param capacityValue the capacity value to return for each day
   * @param beginning start of the date range
   * @param ending end of the date range
   */
  public void stubCapacityReport(
      String productId,
      String metricId,
      int capacityValue,
      OffsetDateTime beginning,
      OffsetDateTime ending) {

    List<Map<String, Object>> dataPoints =
        buildCapacityDataPoints(capacityValue, beginning, ending);

    Map<String, Object> responseBody =
        Map.of(
            "data",
            dataPoints,
            "meta",
            Map.of(
                "count",
                dataPoints.size(),
                "product",
                productId,
                "metric_id",
                metricId,
                "granularity",
                "Daily"),
            "links",
            Map.of());

    String urlPath =
        String.format("/api/rhsm-subscriptions/v1/capacity/products/%s/%s", productId, metricId);

    wiremockService
        .given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "request",
                Map.of("method", "GET", "urlPathPattern", urlPath),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "jsonBody",
                    responseBody),
                "priority",
                5,
                "metadata",
                wiremockService.getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }

  /**
   * Stub the capacity API to return zero capacity (simulating the SWATCH-3288 bug scenario).
   *
   * @param productId the product ID
   * @param metricId the metric ID
   * @param beginning start of the date range
   * @param ending end of the date range
   */
  public void stubEmptyCapacityReport(
      String productId, String metricId, OffsetDateTime beginning, OffsetDateTime ending) {
    stubCapacityReport(productId, metricId, 0, beginning, ending);
  }

  /**
   * Build capacity data points with dates truncated to the start of each day using {@link
   * ApplicationClock#startOfDay(OffsetDateTime)}.
   *
   * <p>This is critical because {@code TallyResource.transformToRunningTotalFormat} looks up
   * capacity using {@code clock.startOfDay(snapshot.date())}. The capacity response dates must
   * match this form exactly, otherwise the lookup returns 0 (the default) and the prepaid/on-demand
   * split breaks.
   */
  private List<Map<String, Object>> buildCapacityDataPoints(
      int capacityValue, OffsetDateTime beginning, OffsetDateTime ending) {
    List<Map<String, Object>> dataPoints = new ArrayList<>();
    OffsetDateTime current = clock.startOfDay(beginning);
    OffsetDateTime endDay = clock.startOfDay(ending);
    while (!current.isAfter(endDay)) {
      boolean hasData = capacityValue > 0;
      dataPoints.add(
          Map.of(
              "date",
              current.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
              "value",
              capacityValue,
              "has_data",
              hasData,
              "has_infinite_quantity",
              false));
      current = current.plusDays(1);
    }
    return dataPoints;
  }
}
