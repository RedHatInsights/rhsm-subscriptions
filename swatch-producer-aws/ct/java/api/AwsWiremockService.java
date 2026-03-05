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

import com.redhat.swatch.component.tests.api.WiremockService;
import java.util.List;
import java.util.Map;

public class AwsWiremockService extends WiremockService {

  private static final String AWS_USAGE_EVENT_PATH = "/mock/aws";

  /**
   * The seller account ID must match a profile name in the AWS credentials file (both locally in
   * config/moto/local-config.ini and in the ephemeral secret aws-marketplace-credentials) so that
   * the service can load credentials. The profile in those files is named "1234567".
   */
  public static final String SELLER_ACCOUNT_ID = "1234567";

  /**
   * Sets up the AWS usage context endpoint (contracts API) and a wiremock stub for the AWS
   * Marketplace BatchMeterUsage endpoint (at /mock/aws).
   */
  public void setupAwsUsageContext(
      String awsAccountId, String rhSubscriptionId, String customerId, String productCode) {
    var contextData =
        Map.of(
            "rhSubscriptionId", rhSubscriptionId,
            "customerId", customerId,
            "productCode", productCode,
            "awsSellerAccountId", SELLER_ACCOUNT_ID,
            "subscriptionStartDate", "2020-01-01T00:00:00Z");

    given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "GET",
                    "urlPathPattern",
                    "/mock/contractApi/api/swatch-contracts/internal/subscriptions/awsUsageContext.*",
                    "queryParameters",
                    Map.of("awsAccountId", Map.of("equalTo", awsAccountId))),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "jsonBody",
                    contextData),
                // the default mapping defined in config/wiremock uses priority 10,
                // so we need a higher priority here.
                "priority",
                9,
                "metadata",
                getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);

    setupAwsBatchMeterUsage();
  }

  public void setupAwsUsageContextToReturnSubscriptionNotFound(String awsAccountId) {
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "GET",
                    "urlPathPattern",
                    "/mock/contractApi/api/swatch-contracts/internal/subscriptions/awsUsageContext.*",
                    "queryParameters",
                    Map.of("awsAccountId", Map.of("equalTo", awsAccountId))),
                "response",
                Map.of("status", 404),
                // the default mapping defined in config/wiremock uses priority 10,
                // so we need a higher priority here.
                "priority",
                9,
                "metadata",
                getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }

  private void setupAwsBatchMeterUsage() {
    // The AWS SDK v2 sends JSON (application/x-amz-json-1.1) with the X-Amz-Target header
    // to identify the operation. We return a minimal valid BatchMeterUsage JSON response.
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "POST",
                    "urlPathPattern",
                    AWS_USAGE_EVENT_PATH + ".*",
                    "headers",
                    Map.of(
                        "X-Amz-Target",
                        Map.of("equalTo", "AWSMarketplaceMetering.BatchMeterUsage"))),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/x-amz-json-1.1"),
                    "jsonBody",
                    Map.of(
                        "Results",
                        List.of(Map.of("MeteringRecordId", "test-record-id", "Status", "Success")),
                        "UnprocessedRecords",
                        List.of())),
                "priority",
                9,
                "metadata",
                getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }

  public void verifyNoAwsUsage() {
    var count = countRequests(AWS_USAGE_EVENT_PATH);
    if (count > 0) {
      throw new AssertionError(
          "Unexpected "
              + count
              + " BatchMeterUsage request(s) found at "
              + AWS_USAGE_EVENT_PATH
              + " but none were expected");
    }
  }
}
