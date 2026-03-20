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
import java.util.Map;

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
    return RestAssured.given()
        .baseUri(PROMETHEUS_QUERY_URL)
        .queryParam("query", query)
        .when()
        .get("/api/v1/query");
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
