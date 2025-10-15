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

import domain.BillingProvider;
import domain.Contract;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Facade for stubbing Partner Gateway API endpoints. */
public class PartnerGatewayStubs {

  private final ContractsWiremockService wiremockService;

  public PartnerGatewayStubs(ContractsWiremockService wiremockService) {
    this.wiremockService = wiremockService;
  }

  /**
   * Stub the partner entitlement API for a given contract test data.
   *
   * @param contract the contract test data containing product details
   */
  public void stubContract(Contract contract) {
    if (contract.getBillingProvider() != BillingProvider.AWS) {
      throw new UnsupportedOperationException(
          contract.getBillingProvider() + " is not supported yet!");
    }

    var responseBody =
        Map.of(
            "rhAccountId",
            contract.getOrgId(),
            "sourcePartner",
            "aws_marketplace",
            "partnerIdentities",
            Map.of(
                "awsCustomerId",
                contract.getCustomerId(),
                "customerAwsAccountId",
                contract.getBillingAccountId(),
                "sellerAccountId",
                contract.getSellerAccountId()),
            "purchase",
            Map.of(
                "vendorProductCode",
                contract.getProductCode(),
                "contracts",
                java.util.List.of(
                    Map.of(
                        "startDate",
                        contract.getStartDate().format(DateTimeFormatter.ISO_INSTANT),
                        "endDate",
                        contract.getEndDate().format(DateTimeFormatter.ISO_INSTANT),
                        "dimensions",
                        contract.getContractMetrics().entrySet().stream()
                            .map(e -> Map.of("name", e.getKey(), "value", e.getValue()))
                            .toList()))),
            "rhEntitlements",
            java.util.List.of(
                Map.of(
                    "sku",
                    contract.getOffering().getSku(),
                    "subscriptionNumber",
                    contract.getSubscriptionNumber())));

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
                        Map.of("equalTo", contract.getBillingAccountId()),
                        "awsCustomerId",
                        Map.of("equalTo", contract.getCustomerId()),
                        "vendorProductCode",
                        Map.of("equalTo", contract.getProductCode()))),
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
                wiremockService.getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }
}
