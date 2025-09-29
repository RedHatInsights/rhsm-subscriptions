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
package com.redhat.swatch.component.tests.api;

import static com.redhat.swatch.component.tests.utils.SwatchUtils.MANAGEMENT_PORT;

import com.redhat.swatch.component.tests.logging.Log;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SwatchService extends RestService {

  public RequestSpecification managementServer() {
    return RestAssured.given()
        .baseUri(HTTP + getHost())
        .basePath(BASE_PATH)
        .port(getMappedPort(MANAGEMENT_PORT));
  }

  @Override
  public boolean isRunning() {
    return super.isRunning() && managementServer().get("/health").statusCode() == 200;
  }

  /**
   * Get the metric value using internal REST APIs
   *
   * @param metricName The metric name to search for
   * @param tags List of tags to match in the metric line
   * @return The metric value as double, or 0.0 if not found
   */
  public double getMetricByTags(String metricName, String... tags) {
    // Get metrics response from the endpoint
    Response metricsResponse = getMetrics();
    String metricsContent = metricsResponse.getBody().asString();
    Log.debug(this, "Metrics response: %s", metricsContent);

    // Parse and extract the metric value
    return getValueFromMetrics(metricsContent, metricName, tags);
  }

  /**
   * Get metrics from the internal API endpoint
   *
   * @return RestAssured Response containing the metrics
   */
  private Response getMetrics() {
    return managementServer().get("/metrics").then().extract().response();
  }

  /**
   * Parse metrics response and extract value for specific metric name and tags
   *
   * <p>From a metrics API response like: # TYPE kafka_consumer_node_request_size_avg gauge # HELP
   * kafka_consumer_node_request_size_avg The average size of requests sent.
   * kafka_consumer_node_request_size_avg{client_id="kafka-consumer-export-requests",kafka_version="3.7.2",node_id="node-2147483647"}
   * 146.0
   *
   * <p>It will return the metric value filtered by the metric name and tags.
   *
   * @param metricsResponse The metrics API response as string
   * @param metricName The metric name to search for
   * @param tags List of tags that must all be present in the metric line
   * @return The metric value as double, or 0.0 if not found
   */
  private double getValueFromMetrics(String metricsResponse, String metricName, String[] tags) {
    String[] lines = metricsResponse.split("\\n");

    for (String line : lines) {
      // Check if line starts with metric name followed by '{'
      if (line.startsWith(metricName)) {
        // Check if all tags are present in the line
        boolean allTagsPresent = Stream.of(tags).allMatch(line::contains);

        if (allTagsPresent) {
          // Extract the numeric value using regex
          Pattern pattern = Pattern.compile("([0-9]+\\.?[0-9]*)");
          Matcher matcher = pattern.matcher(line);

          if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
          }
        }
      }
    }

    return 0.0; // Return 0.0 if metric not found
  }
}
