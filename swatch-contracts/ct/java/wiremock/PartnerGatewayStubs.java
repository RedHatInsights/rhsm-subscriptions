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
package wiremock;

import java.util.Map;
import model.ContractTestData;

/** Facade for stubbing Partner Gateway API endpoints. */
public class PartnerGatewayStubs {

  private final ContractsWiremockService wiremockService;

  public PartnerGatewayStubs(ContractsWiremockService wiremockService) {
    this.wiremockService = wiremockService;
  }

  /**
   * Stub the partner entitlement API for a given contract test data.
   *
   * @param contractData the contract test data containing AWS and product details
   */
  public void stubPartnerEntitlement(ContractTestData contractData) {
    stubPartnerEntitlementSuccess(
        contractData.awsCustomerId(),
        contractData.awsAccountId(),
        contractData.productCode(),
        contractData.orgId());
  }

  /**
   * Stub the partner entitlement API to return success for given AWS and product details.
   *
   * @param awsCustomerId AWS customer ID
   * @param awsAccountId AWS account ID
   * @param productCode product code
   * @param orgId organization ID
   */
  public void stubPartnerEntitlementSuccess(
      String awsCustomerId, String awsAccountId, String productCode, String orgId) {

    var responseBody =
        Map.of(
            "rhAccountId",
            orgId,
            "sourcePartner",
            "aws_marketplace",
            "partnerIdentities",
            Map.of(
                "awsCustomerId",
                awsCustomerId,
                "customerAwsAccountId",
                awsAccountId,
                "sellerAccountId",
                "123456789"),
            "purchase",
            Map.of(
                "vendorProductCode",
                productCode,
                "contracts",
                java.util.List.of(
                    Map.of(
                        "startDate",
                        "2025-01-01T00:00:00Z",
                        "endDate",
                        "2025-12-31T23:59:59Z",
                        "dimensions",
                        java.util.List.of(Map.of("name", "four_vcpu_hour", "value", "10"))))),
            "rhEntitlements",
            java.util.List.of(Map.of("sku", "RH00001", "subscriptionNumber", "12400374")));

    wiremockService
        .given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "GET",
                    "urlPathPattern",
                    "/mock/partnerApi/v1/partnerSubscriptions.*",
                    "queryParameters",
                    Map.of(
                        "customerAwsAccountId",
                        Map.of("equalTo", awsAccountId),
                        "awsCustomerId",
                        Map.of("equalTo", awsCustomerId),
                        "vendorProductCode",
                        Map.of("equalTo", productCode))),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "jsonBody",
                    responseBody),
                "priority",
                9,
                "metadata",
                Map.of(wiremockService.getMetadataTag(), "true")))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }
}
