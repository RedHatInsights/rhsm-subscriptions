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

import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.component.tests.utils.SwatchUtils;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/** Service for making Tally-related REST API calls in component tests. */
public class SwatchTallyRestAPIService {

  private static final String TEST_PSK = "placeholder";

  /** Default constructor. */
  public SwatchTallyRestAPIService() {}

  public static void syncTallyNightly(String orgId, SwatchService service) {
    Response response =
        service
            .given()
            .header("x-rh-swatch-psk", TEST_PSK)
            .header("x-rh-swatch-synchronous-request", "true")
            .put("/api/rhsm-subscriptions/v1/internal/rpc/tally/snapshots/" + orgId)
            .then()
            .extract()
            .response();

    if (response.getStatusCode() != 200) {
      throw new RuntimeException(
          "Tally sync failed with status code: "
              + response.getStatusCode()
              + ", response body: "
              + response.getBody().asString());
    }

    Log.info("Sync nightly tally endpoint called successfully for org: %s", orgId);
  }

  public void syncTallyHourly(String orgId, SwatchService service) {
    Response response =
        service
            .given()
            .header("x-rh-swatch-psk", TEST_PSK)
            .queryParam("org", orgId)
            .post("/api/rhsm-subscriptions/v1/internal/tally/hourly")
            .then()
            .extract()
            .response();

    if (response.getStatusCode() != 204) {
      throw new RuntimeException(
          "Hourly tally sync failed with status code: "
              + response.getStatusCode()
              + ", response body: "
              + response.getBody().asString());
    }

    Log.info("Hourly tally endpoint called successfully for org: %s", orgId);
  }

  public void createOptInConfig(String orgId, SwatchService service) {
    Response response =
        service
            .given()
            .header("x-rh-swatch-psk", TEST_PSK)
            .queryParam("org_id", orgId)
            .put("/api/rhsm-subscriptions/v1/internal/rpc/tally/opt-in")
            .then()
            .extract()
            .response();

    if (response.getStatusCode() != 200) {
      throw new RuntimeException(
          "Create opt-in config failed with status code: "
              + response.getStatusCode()
              + ", response body: "
              + response.getBody().asString());
    }

    Log.info("Opt-in config created successfully for org: %s", orgId);
  }

  public Response getTallyReport(
      SwatchService service,
      String orgId,
      String productId,
      String metricId,
      Map<String, ?> queryParams) {
    Map<String, Object> params = new HashMap<>();
    if (queryParams != null) {
      params.putAll(queryParams);
    }

    Response response =
        service
            .given()
            .header("x-rh-identity", SwatchUtils.createUserIdentityHeader(orgId))
            .queryParams(params)
            // Use path params so product IDs with spaces are safely encoded.
            .get(
                "/api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}",
                productId,
                metricId)
            .then()
            .extract()
            .response();

    if (response.getStatusCode() != 200) {
      throw new RuntimeException(
          "Get tally report failed with status code: "
              + response.getStatusCode()
              + ", response body: "
              + response.getBody().asString());
    }

    Log.info(
        "Tally report response for orgId=%s, productId=%s, metricId=%s: %s",
        orgId, productId, metricId, response.getBody().asString());

    return response;
  }

  /*
   * Get tally report with raw query parameters (for testing invalid values).
   *
   * @return Raw Response object for status code validation
   */
  public Response getTallyReportRaw(
      SwatchService service,
      String orgId,
      String productId,
      String metricId,
      Map<String, ?> queryParams) {
    Map<String, Object> params = new HashMap<>();
    if (queryParams != null) {
      params.putAll(queryParams);
    }

    return service
        .given()
        .header("x-rh-identity", SwatchUtils.createUserIdentityHeader(orgId))
        .queryParams(params)
        // Use path params so product IDs with spaces are safely encoded.
        .get(
            "/api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}", productId, metricId)
        .then()
        .extract()
        .response();
  }

  public Response getInstancesReport(
      String orgId,
      String productId,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      SwatchService service) {
    Response response =
        service
            .given()
            .header("x-rh-identity", SwatchUtils.createUserIdentityHeader(orgId))
            .queryParam("beginning", beginning.toString())
            .queryParam("ending", ending.toString())
            // Use path params so product IDs with spaces are safely encoded.
            .get("/api/rhsm-subscriptions/v1/instances/products/{productId}", productId)
            .then()
            .extract()
            .response();

    if (response.getStatusCode() != 200) {
      throw new RuntimeException(
          "Get instances report failed with status code: "
              + response.getStatusCode()
              + ", response body: "
              + response.getBody().asString());
    }

    Log.info(
        "Instances report response for orgId=%s, productId=%s: %s",
        orgId, productId, response.getBody().asString());

    return response;
  }
}
