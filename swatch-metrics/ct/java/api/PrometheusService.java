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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Component-test helper: OpenMetrics {@code POST /import} is on {@code localhost:9091} (Prometheus
 * container port 9000); PromQL {@code /api/v1/query} is on {@code localhost:9090} (container
 * 8000), matching {@code PROM_URL} for swatch-metrics in dev/component tests.
 */
public class PrometheusService {

  private static final String PROMETHEUS_QUERY_URL = "http://localhost:9090";
  private static final String PROMETHEUS_IMPORT_URL = "http://localhost:9091";
  private static final String PROMETHEUS_CONTAINER = "prometheus";

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
   * Runs {@code GET /api/v1/query} (instant vector selector). Use after {@link #importMetrics} to
   * confirm samples are visible in the TSDB queried by swatch-metrics ({@code PROM_URL}).
   *
   * <p>Without {@code evaluationTimeEpochSeconds}, Prometheus evaluates at &quot;now&quot; and only
   * returns series with a sample within the engine lookback (default 5m). Imported points older
   * than that do not show up; pass an explicit {@code time} or ensure recent timestamps in the
   * fixture.
   *
   * @return number of result series (0 if empty matrix)
   */
  public int getInstantQueryResultCount(String promql) {
    return getInstantQueryResultCount(promql, null);
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

  public void pushMetricsViaRemoteWrite(String prometheusData) throws IOException {
    // Write data to temp file
    Path tempFile = Files.createTempFile("prometheus-data-", ".txt");
    try {
      Files.writeString(tempFile, prometheusData);

      // Copy to container
      String containerPath = "/tmp/prometheus-test-data.txt";
      executeCommand(
          "podman", "cp", tempFile.toString(), PROMETHEUS_CONTAINER + ":" + containerPath);

      // Push metrics using promtool inside container
      executeCommand(
          "podman",
          "exec",
          PROMETHEUS_CONTAINER,
          "/opt/prometheus/promtool",
          "push",
          "metrics",
          "http://localhost:8000/api/v1/write",
          containerPath);

    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  private void executeCommand(String... command) throws IOException {
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.inheritIO();
    try {
      Process process = pb.start();
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IOException(
            "Command failed with exit code " + exitCode + ": " + String.join(" ", command));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Command interrupted: " + String.join(" ", command), e);
    }
  }

  public Response query(String query) {
    return query(query, null);
  }

  /** @param evaluationTimeEpochSeconds if non-null, passed as {@code time} to the instant query API */
  public Response query(String query, Long evaluationTimeEpochSeconds) {
    var req =
        RestAssured.given()
            .baseUri(PROMETHEUS_QUERY_URL)
            .queryParam("query", query);
    if (evaluationTimeEpochSeconds != null) {
      req = req.queryParam("time", evaluationTimeEpochSeconds);
    }
    return req.when().get("/api/v1/query");
  }

  public Response queryRange(String query, long start, long end, String step) {
    return RestAssured.given()
        .baseUri(PROMETHEUS_QUERY_URL)
        .queryParam("query", query)
        .queryParam("start", start)
        .queryParam("end", end)
        .queryParam("step", step)
        .when()
        .get("/api/v1/query_range");
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getFirstResult(Response response) {
    Map<String, Object> data = response.jsonPath().get("data");
    var results = (java.util.List<Map<String, Object>>) data.get("result");
    return results.isEmpty() ? null : results.get(0);
  }
}
