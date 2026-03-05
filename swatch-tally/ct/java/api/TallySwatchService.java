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

import static com.redhat.swatch.component.tests.utils.SwatchUtils.SECURITY_HEADERS;
import static com.redhat.swatch.component.tests.utils.SwatchUtils.X_RH_IDENTITY_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.component.tests.utils.SwatchUtils;
import com.redhat.swatch.tally.test.model.InstanceResponse;
import com.redhat.swatch.tally.test.model.TallyReportData;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpStatus;

/**
 * Service facade for swatch-tally component tests.
 *
 * <p>Provides clean, intention-revealing methods for interacting with the swatch-tally service API.
 * Methods are organized by functional area:
 *
 * <ul>
 *   <li>Configuration - Org opt-in setup
 *   <li>Tally Operations - Triggering hourly and nightly tally processes
 *   <li>Report Retrieval - Fetching tally and instance reports
 * </ul>
 */
public class TallySwatchService extends SwatchService {

  private static final String API_PATH = "/api/rhsm-subscriptions/v1";
  private static final String INTERNAL_API_PATH = API_PATH + "/internal";

  // --- Configuration methods ---

  /**
   * Creates opt-in configuration for an organization.
   *
   * @param orgId the organization ID to configure
   */
  public void createOptInConfig(String orgId) {
    Response response =
        given()
            .headers(SECURITY_HEADERS)
            .queryParam("org_id", orgId)
            .put(INTERNAL_API_PATH + "/rpc/tally/opt-in")
            .then()
            .extract()
            .response();

    assertEquals(
        HttpStatus.SC_OK,
        response.getStatusCode(),
        "Create opt-in config failed with status code: "
            + response.getStatusCode()
            + ", response body: "
            + response.getBody().asString());

    Log.info(this, "Opt-in config created successfully for org: %s", orgId);
  }

  // --- Tally operation methods ---

  /**
   * Performs hourly tally for an organization.
   *
   * @param orgId the organization ID to tally
   */
  public void performHourlyTallyForOrg(String orgId) {
    Response response =
        given()
            .headers(SECURITY_HEADERS)
            .queryParam("org", orgId)
            .post(INTERNAL_API_PATH + "/tally/hourly")
            .then()
            .extract()
            .response();

    assertEquals(
        HttpStatus.SC_NO_CONTENT,
        response.getStatusCode(),
        "Hourly tally sync failed with status code: "
            + response.getStatusCode()
            + ", response body: "
            + response.getBody().asString());

    Log.info(this, "Hourly tally endpoint called successfully for org: %s", orgId);
  }

  /**
   * Performs nightly tally (snapshot creation) for an organization.
   *
   * @param orgId the organization ID to tally
   */
  public void tallyOrg(String orgId) {
    Response response =
        given()
            .headers(SECURITY_HEADERS)
            .header("x-rh-swatch-synchronous-request", "true")
            .put(INTERNAL_API_PATH + "/rpc/tally/snapshots/" + orgId)
            .then()
            .extract()
            .response();

    assertEquals(
        HttpStatus.SC_OK,
        response.getStatusCode(),
        "Tally sync failed with status code: "
            + response.getStatusCode()
            + ", response body: "
            + response.getBody().asString());

    Log.info(this, "Sync nightly tally endpoint called successfully for org: %s", orgId);
  }

  // --- Report retrieval methods ---

  /**
   * Retrieves tally report data for a specific product and metric.
   *
   * @param orgId the organization ID
   * @param productId the product ID
   * @param metricId the metric ID
   * @param queryParams additional query parameters (granularity, beginning, ending, etc.)
   * @return the tally report data
   */
  public TallyReportData getTallyReportData(
      String orgId, String productId, String metricId, Map<String, ?> queryParams) {
    Response response = getTallyReportDataRaw(orgId, productId, metricId, queryParams);

    assertEquals(
        HttpStatus.SC_OK,
        response.getStatusCode(),
        "Get tally report failed with status code: "
            + response.getStatusCode()
            + ", response body: "
            + response.getBody().asString());

    Log.debug(
        this,
        "Tally report response for orgId=%s, productId=%s, metricId=%s: %s",
        orgId,
        productId,
        metricId,
        response.getBody().asString());

    return response.as(TallyReportData.class);
  }

  /**
   * Retrieves raw tally report response for a specific product and metric.
   *
   * <p>Returns the raw Response object for tests that need to verify status codes or error
   * responses.
   *
   * @param orgId the organization ID
   * @param productId the product ID
   * @param metricId the metric ID
   * @param queryParams additional query parameters
   * @return the raw response
   */
  public Response getTallyReportDataRaw(
      String orgId, String productId, String metricId, Map<String, ?> queryParams) {
    Map<String, Object> params = new HashMap<>();
    if (queryParams != null) {
      params.putAll(queryParams);
    }

    return given()
        .header(X_RH_IDENTITY_HEADER, SwatchUtils.createUserIdentityHeader(orgId))
        .queryParams(params)
        .get(API_PATH + "/tally/products/{productId}/{metricId}", productId, metricId)
        .then()
        .extract()
        .response();
  }

  /**
   * Trigger hourly snapshot production for all configured orgs (same as the cron PUT
   * .../tally/snapshots). Tasks are enqueued to the dedicated tally-hourly-tasks topic.
   */
  public void triggerHourlySnapshotsForAllOrgs() {
    Response response =
        given()
            .headers(SECURITY_HEADERS)
            .put(INTERNAL_API_PATH + "/rpc/tally/snapshots")
            .then()
            .extract()
            .response();

    assertEquals(
        HttpStatus.SC_OK,
        response.getStatusCode(),
        "Trigger hourly snapshots for all orgs failed with status code: "
            + response.getStatusCode()
            + ", response body: "
            + response.getBody().asString());

    Log.info(this, "Hourly snapshots for all orgs triggered successfully");
  }

  /**
   * Triggers tally for an org asynchronously (enqueues UPDATE_SNAPSHOTS to the main tasks topic).
   * Use when asserting that nightly tasks are produced to the tasks topic.
   */
  public void tallyOrgAsync(String orgId) {
    Response response =
        given()
            .headers(SECURITY_HEADERS)
            .header("x-rh-swatch-synchronous-request", "false")
            .put(INTERNAL_API_PATH + "/rpc/tally/snapshots/" + orgId)
            .then()
            .extract()
            .response();

    assertEquals(
        HttpStatus.SC_OK,
        response.getStatusCode(),
        "Tally async failed with status code: "
            + response.getStatusCode()
            + ", response body: "
            + response.getBody().asString());

    Log.info(this, "Async nightly tally endpoint called for org: %s", orgId);
  }

  /**
   * Retrieves instances report for a specific product and time range.
   *
   * @param orgId the organization ID
   * @param productId the product ID
   * @param beginning the start of the time range
   * @param ending the end of the time range
   * @return the instances report
   */
  public InstanceResponse getInstancesByProduct(
      String orgId, String productId, OffsetDateTime beginning, OffsetDateTime ending) {
    return getInstancesByProduct(orgId, productId, beginning, ending, null);
  }

  public InstanceResponse getInstancesByProduct(
      String orgId,
      String productId,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      Map<String, ?> queryParams) {
    Map<String, Object> params = new HashMap<>();
    params.put("beginning", beginning.toString());
    params.put("ending", ending.toString());
    if (queryParams != null) {
      params.putAll(queryParams);
    }

    Response response =
        given()
            .header(X_RH_IDENTITY_HEADER, SwatchUtils.createUserIdentityHeader(orgId))
            .queryParams(params)
            // Use path params so product IDs with spaces are safely encoded.
            .get(API_PATH + "/instances/products/{productId}", productId)
            .then()
            .extract()
            .response();

    assertEquals(
        HttpStatus.SC_OK,
        response.getStatusCode(),
        "Get instances report failed with status code: "
            + response.getStatusCode()
            + ", response body: "
            + response.getBody().asString());

    Log.debug(
        this,
        "Instances report response for orgId=%s, productId=%s: %s",
        orgId,
        productId,
        response.getBody().asString());

    return response.as(InstanceResponse.class);
  }

  public Response getBillingAccountIds(String orgId, Map<String, ?> queryParams) {
    Map<String, Object> params = new HashMap<>();
    params.put("org_id", orgId);
    if (queryParams != null) {
      params.putAll(queryParams);
    }

    Response response =
        given()
            .header(X_RH_IDENTITY_HEADER, SwatchUtils.createUserIdentityHeader(orgId))
            .queryParams(params)
            .get(API_PATH + "/instances/billing_account_ids")
            .then()
            .extract()
            .response();

    assertEquals(
        HttpStatus.SC_OK,
        response.getStatusCode(),
        "Get billing account IDs failed with status code: "
            + response.getStatusCode()
            + ", response body: "
            + response.getBody().asString());

    Log.debug(
        this,
        "Billing account IDs response for orgId=%s: status=%d, body=%s",
        orgId,
        response.getStatusCode(),
        response.getBody().asString());

    return response;
  }
}
