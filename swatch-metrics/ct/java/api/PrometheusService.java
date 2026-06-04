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

import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.util.List;

/**
 * Component-test helper: OpenMetrics {@code POST /import} is on {@code localhost:9091} (Prometheus
 * container port 9000); PromQL {@code /api/v1/query} is on {@code localhost:9090} (container 8000),
 * matching {@code PROM_URL} for swatch-metrics in dev/component tests.
 */
public class PrometheusService {

  private static final String PROMETHEUS_QUERY_URL = "http://localhost:9090";
  private static final String PROMETHEUS_IMPORT_URL = "http://localhost:9091";

  public Response importMetrics(String openMetricsData) {
    return RestAssured.given()
        .baseUri(PROMETHEUS_IMPORT_URL)
        .contentType("text/plain")
        .body(openMetricsData)
        .when()
        .post("/import");
  }

  /**
   * @throws AssertionError if {@code /import} does not return 2xx or the body suggests failure
   */
  public void importMetricsExpectSuccess(String openMetricsData) {
    Response response = importMetrics(openMetricsData);
    int code = response.getStatusCode();
    if (code < 200 || code >= 300) {
      throw new AssertionError(
          "Prometheus /import expected 2xx but got "
              + code
              + ". Body: "
              + response.asPrettyString());
    }
  }

  /**
   * @param evaluationTimeEpochSeconds optional {@code time=} for instant query (unix seconds); if
   *     null, engine uses &quot;now&quot;
   */
  public int getInstantQueryResultCount(String promql, Long evaluationTimeEpochSeconds) {
    Response r = query(promql, evaluationTimeEpochSeconds);
    r.then().statusCode(200);
    String status = r.jsonPath().getString("status");
    if (!"success".equals(status)) {
      throw new AssertionError("Prometheus instant query not successful: " + r.asPrettyString());
    }
    List<?> results = r.jsonPath().getList("data.result");
    return results == null ? 0 : results.size();
  }

  /**
   * @param evaluationTimeEpochSeconds if non-null, passed as {@code time} to the instant query API
   */
  public Response query(String query, Long evaluationTimeEpochSeconds) {
    var req = RestAssured.given().baseUri(PROMETHEUS_QUERY_URL).queryParam("query", query);
    if (evaluationTimeEpochSeconds != null) {
      req = req.queryParam("time", evaluationTimeEpochSeconds);
    }
    return req.when().get("/api/v1/query");
  }
}
