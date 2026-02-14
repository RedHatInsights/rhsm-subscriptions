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

public class TallySwatchService extends SwatchService {

  private static final String API_PATH = "/api/rhsm-subscriptions/v1";
  private static final String INTERNAL_API_PATH = API_PATH + "/internal";

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

  public Response getTallyReportDataRaw(
      String orgId, String productId, String metricId, Map<String, ?> queryParams) {
    Map<String, Object> params = new HashMap<>();
    if (queryParams != null) {
      params.putAll(queryParams);
    }

    return given()
        .header(X_RH_IDENTITY_HEADER, SwatchUtils.createUserIdentityHeader(orgId))
        .queryParams(params)
        // Use path params so product IDs with spaces are safely encoded.
        .get(API_PATH + "/tally/products/{productId}/{metricId}", productId, metricId)
        .then()
        .extract()
        .response();
  }

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
