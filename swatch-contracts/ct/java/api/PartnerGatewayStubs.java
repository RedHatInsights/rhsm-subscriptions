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
    if (contract.getBillingProvider() == BillingProvider.AWS) {
      stubAwsContract(contract);
    } else if (contract.getBillingProvider() == BillingProvider.AZURE) {
      stubAzureContract(contract);
    } else {
      throw new UnsupportedOperationException(
          contract.getBillingProvider() + " is not supported yet!");
    }
  }

  /**
   * Stub the partner entitlement API for sync by org ID.
   *
   * @param orgId the organization ID
   * @param contracts list of contracts to return for the org
   */
  public void stubSyncContractsByOrg(String orgId, java.util.List<Contract> contracts) {
    var responseBody =
        Map.of(
            "content",
            contracts.stream()
                .map(
                    contract -> {
                      if (contract.getBillingProvider() == BillingProvider.AWS) {
                        return buildAwsContractBody(contract, orgId);
                      } else if (contract.getBillingProvider() == BillingProvider.AZURE) {
                        return buildAzureContractBody(contract, orgId);
                      } else {
                        throw new UnsupportedOperationException(
                            contract.getBillingProvider() + " is not supported!");
                      }
                    })
                .toList(),
            "page",
            Map.of("size", 20, "totalElements", contracts.size(), "totalPages", 1, "number", 0));

    wiremockService
        .given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method", "POST", "urlPathPattern", "/mock/partnerApi/v1/partnerSubscriptions"),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "jsonBody",
                    responseBody),
                "priority",
                10,
                "metadata",
                wiremockService.getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);

    // Stub subscription service lookups for all contracts
    for (Contract contract : contracts) {
      if (contract.getSubscriptionNumber() != null) {
        stubSubscriptionLookup(contract.getSubscriptionNumber(), contract.getSubscriptionId());
      }
    }
  }

  /**
   * Stub the subscription service to return a subscription by subscription number.
   *
   * @param subscriptionNumber the subscription number to look up
   * @param subscriptionId the subscription ID to return
   */
  private void stubSubscriptionLookup(String subscriptionNumber, String subscriptionId) {
    var subscription =
        Map.of(
            "id",
            Integer.parseInt(subscriptionId),
            "subscriptionNumber",
            subscriptionNumber,
            "effectiveStartDate",
            System.currentTimeMillis(),
            "effectiveEndDate",
            java.time.OffsetDateTime.now().plusYears(1).toInstant().toEpochMilli());

    var responseBody = java.util.List.of(subscription);

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
                    String.format(
                        "/mock/subscriptionApi/search/criteria;subscription_number=%s/options;products=ALL;showExternalReferences=true/",
                        subscriptionNumber)),
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

  /** Stub empty contract list for sync by org ID. */
  public void stubEmptySyncContractsByOrg(String orgId) {
    stubSyncContractsByOrg(orgId, java.util.List.of());
  }

  /**
   * Build AWS contract response body for Partner Gateway API. Can be used for both single contract
   * and sync endpoints.
   *
   * @param contract the contract data
   * @param orgId the organization ID
   * @return AWS contract response body map
   */
  private Map<String, Object> buildAwsContractBody(Contract contract, String orgId) {
    return Map.of(
        "rhAccountId",
        orgId,
        "sourcePartner",
        "aws_marketplace",
        "entitlementDates",
        buildEntitlementDates(contract),
        "partnerIdentities",
        Map.of(
            "awsCustomerId",
            contract.getCustomerId(),
            "sellerAccountId",
            contract.getSellerAccountId()),
        "purchase",
        Map.of(
            "vendorProductCode",
            contract.getProductCode(),
            "contracts",
            java.util.List.of(buildContractDetails(contract))),
        "rhEntitlements",
        buildRhEntitlements(contract));
  }

  /**
   * Build Azure contract response body for Partner Gateway API. Can be used for both single
   * contract and sync endpoints.
   *
   * @param contract the contract data
   * @param orgId the organization ID
   * @return Azure contract response body map
   */
  private Map<String, Object> buildAzureContractBody(Contract contract, String orgId) {
    var contractDetails = new java.util.HashMap<String, Object>(buildContractDetails(contract));
    contractDetails.put("planId", contract.getPlanId());

    return Map.of(
        "rhAccountId",
        orgId,
        "sourcePartner",
        "azure_marketplace",
        "entitlementDates",
        buildEntitlementDates(contract),
        "partnerIdentities",
        Map.of(
            "azureSubscriptionId",
            contract.getBillingAccountId(),
            "azureTenantId",
            contract.getBillingAccountId(),
            "azureCustomerId",
            contract.getCustomerId(),
            "clientId",
            contract.getClientId()),
        "purchase",
        Map.of(
            "vendorProductCode",
            contract.getProductCode(),
            "azureResourceId",
            contract.getResourceId(),
            "contracts",
            java.util.List.of(contractDetails)),
        "rhEntitlements",
        buildRhEntitlements(contract));
  }

  /** Build common entitlement dates map. */
  private Map<String, String> buildEntitlementDates(Contract contract) {
    return Map.of(
        "startDate",
        contract.getStartDate().format(DateTimeFormatter.ISO_INSTANT),
        "endDate",
        contract.getEndDate().format(DateTimeFormatter.ISO_INSTANT));
  }

  /** Build common RH entitlements list. */
  private java.util.List<Map<String, String>> buildRhEntitlements(Contract contract) {
    return java.util.List.of(
        Map.of(
            "sku",
            contract.getOffering().getSku(),
            "subscriptionNumber",
            contract.getSubscriptionNumber()));
  }

  /** Build common contract details (dates and dimensions). */
  private Map<String, Object> buildContractDetails(Contract contract) {
    return Map.of(
        "startDate",
        contract.getStartDate().format(DateTimeFormatter.ISO_INSTANT),
        "endDate",
        contract.getEndDate().format(DateTimeFormatter.ISO_INSTANT),
        "dimensions",
        contract.getContractMetrics().entrySet().stream()
            .map(e -> Map.of("name", e.getKey(), "value", e.getValue()))
            .toList());
  }

  private void stubAzureContract(Contract contract) {
    var responseBody = buildAzureContractBody(contract, contract.getOrgId());

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
                    Map.of("azureResourceId", Map.of("equalTo", contract.getResourceId()))),
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

  private void stubAwsContract(Contract contract) {
    var responseBody = buildAwsContractBody(contract, contract.getOrgId());

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
