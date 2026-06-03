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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stubs for mocking Prometheus API endpoints using WireMock.
 *
 * <p>This class provides methods to configure WireMock stubs that simulate Prometheus query and
 * query_range endpoints.
 */
public class PrometheusStubs {

  private final PrometheusWiremockService wiremock;

  public PrometheusStubs(PrometheusWiremockService wiremock) {
    this.wiremock = wiremock;
  }

  /**
   * Configures the mock to return metric data for a range query. This is what the metering service
   * actually uses when querying Prometheus. The response format is a matrix with time series data.
   */
  public void stubQueryRangeWithMetricData(
      String metricName, Map<String, String> labels, double value) {
    // Build label map - include all labels that came from the caller
    Map<String, String> metricLabels = new LinkedHashMap<>();
    metricLabels.put("__name__", metricName);
    if (labels != null) {
      metricLabels.putAll(labels);
    }

    // Build a matrix response with time series data
    long currentTime = System.currentTimeMillis() / 1000;
    java.util.List<java.util.List<Object>> values = new java.util.ArrayList<>();
    // Add multiple data points over time (simulating hourly data)
    for (int i = 0; i < 5; i++) {
      long timestamp = currentTime - (4 - i) * 3600; // hourly intervals
      values.add(java.util.List.of(timestamp, String.valueOf(value)));
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("metric", metricLabels);
    result.put("values", values);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("resultType", "matrix");
    data.put("result", java.util.List.of(result));

    Map<String, Object> responseBody = new LinkedHashMap<>();
    responseBody.put("status", "success");
    responseBody.put("data", data);

    Map<String, Object> mapping = new LinkedHashMap<>();
    mapping.put(
        "request",
        Map.of(
            "method",
            "GET",
            "urlPathPattern",
            "/api/v1/query_range",
            "queryParameters",
            Map.of("query", Map.of("contains", metricName))));
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", 200);
    response.put("headers", Map.of("Content-Type", "application/json"));
    response.put("jsonBody", responseBody);
    mapping.put("response", response);
    mapping.put("metadata", wiremock.getMetadataTags());

    wiremock
        .given()
        .contentType("application/json")
        .body(mapping)
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }

  /** Stub the query endpoint to return an empty result (default behavior). */
  public void stubQueryEmpty() {
    Map<String, Object> responseBody = new LinkedHashMap<>();
    responseBody.put("status", "success");
    responseBody.put("data", Map.of("resultType", "matrix", "result", java.util.List.of()));

    Map<String, Object> mapping = new LinkedHashMap<>();
    mapping.put("request", Map.of("method", "GET", "urlPathPattern", "/api/v1/query"));
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", 200);
    response.put("headers", Map.of("Content-Type", "application/json"));
    response.put("jsonBody", responseBody);
    mapping.put("response", response);
    mapping.put("metadata", wiremock.getMetadataTags());

    wiremock
        .given()
        .contentType("application/json")
        .body(mapping)
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }

  /** Stub the query_range endpoint to return an empty result (default behavior). */
  public void stubQueryRangeEmpty() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("resultType", "matrix");
    data.put("result", java.util.List.of());

    Map<String, Object> responseBody = new LinkedHashMap<>();
    responseBody.put("status", "success");
    responseBody.put("data", data);

    Map<String, Object> mapping = new LinkedHashMap<>();
    mapping.put("request", Map.of("method", "GET", "urlPathPattern", "/api/v1/query_range"));
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", 200);
    response.put("headers", Map.of("Content-Type", "application/json"));
    response.put("jsonBody", responseBody);
    mapping.put("response", response);
    mapping.put("metadata", wiremock.getMetadataTags());

    wiremock
        .given()
        .contentType("application/json")
        .body(mapping)
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }
}
