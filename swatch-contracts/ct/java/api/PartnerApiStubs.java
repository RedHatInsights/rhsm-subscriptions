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

import com.redhat.swatch.component.tests.utils.JsonUtils;
import domain.BillingProvider;
import domain.Contract;
import io.restassured.http.ContentType;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Facade for stubbing Partner Gateway API endpoints. */
public class PartnerApiStubs {

  private final ContractsWiremockService wiremockService;

  public PartnerApiStubs(ContractsWiremockService wiremockService) {
    this.wiremockService = wiremockService;
  }

  /** Stub the partner entitlement API for a given contract test data. */
  public void stubPartnerSubscriptions(PartnerSubscriptionsStubRequest request) {
    var responseBody = toResponseBody(request);
    var expectedQuery = toExpectedQuery(request);

    wiremockService
        .given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "POST",
                    "urlPathPattern",
                    "/mock/partnerApi/v1/partnerSubscriptions",
                    "bodyPatterns",
                    List.of(Map.of("equalToJson", JsonUtils.toJson(expectedQuery)))),
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

  private Map<String, Object> toExpectedQuery(PartnerSubscriptionsStubRequest request) {
    if (request.queryByOrgIdOnly) {
      return Map.of("rhAccountId", request.orgId, "page", Map.of("size", 20, "number", 0));
    }

    if (request.contracts.size() > 1) {
      throw new UnsupportedOperationException(
          "Can't stub the Partner Gateway API for "
              + "multiple contracts without setting the orgId!");
    }

    Contract contract = request.contracts.get(0);
    if (contract.getBillingProvider() == BillingProvider.AWS) {
      return buildAwsExpectedQuery(contract);
    } else if (contract.getBillingProvider() == BillingProvider.AZURE) {
      return buildAzureExpectedQuery(contract);
    } else {
      throw new UnsupportedOperationException(contract.getBillingProvider() + " is not supported!");
    }
  }

  private Map<String, Object> toResponseBody(PartnerSubscriptionsStubRequest request) {
    return Map.of(
        "content",
        request.contracts.stream()
            .map(
                contract -> {
                  if (contract.getBillingProvider() == BillingProvider.AWS) {
                    return buildAwsContractBody(contract);
                  } else if (contract.getBillingProvider() == BillingProvider.AZURE) {
                    return buildAzureContractBody(contract);
                  } else {
                    throw new UnsupportedOperationException(
                        contract.getBillingProvider() + " is not supported!");
                  }
                })
            .toList(),
        "page",
        Map.of(
            "size", 20, "totalElements", request.contracts.size(), "totalPages", 1, "number", 0));
  }

  private Map<String, Object> buildAwsExpectedQuery(Contract contract) {
    return Map.of(
        "customerAwsAccountId", contract.getBillingAccountId(),
        "vendorProductCode", contract.getProductCode(),
        "page", Map.of("size", 20, "number", 0));
  }

  private Map<String, Object> buildAzureExpectedQuery(Contract contract) {
    return Map.of(
        "azureResourceId", contract.getResourceId(), "page", Map.of("size", 20, "number", 0));
  }

  public static class PartnerSubscriptionsStubRequest {
    private final String orgId;
    private final List<Contract> contracts;
    private final boolean queryByOrgIdOnly;

    private PartnerSubscriptionsStubRequest(
        String orgId, List<Contract> contracts, boolean queryByOrgIdOnly) {
      this.orgId = orgId;
      this.contracts = contracts;
      this.queryByOrgIdOnly = queryByOrgIdOnly;
    }

    public static PartnerSubscriptionsStubRequest forContract(Contract contract) {
      return new PartnerSubscriptionsStubRequest(contract.getOrgId(), List.of(contract), false);
    }

    public static PartnerSubscriptionsStubRequest forContractsInOrgId(
        String orgId, Contract... contracts) {
      return new PartnerSubscriptionsStubRequest(orgId, List.of(contracts), true);
    }
  }

  /**
   * Build AWS contract response body for Partner Gateway API. Can be used for both single contract
   * and sync endpoints.
   *
   * @param contract the contract data
   * @return AWS contract response body map
   */
  private Map<String, Object> buildAwsContractBody(Contract contract) {
    var body = new java.util.HashMap<String, Object>();
    body.put("rhAccountId", contract.getOrgId());
    body.put("sourcePartner", "aws_marketplace");
    body.put("entitlementDates", buildEntitlementDates(contract));
    body.put(
        "partnerIdentities",
        Map.of(
            "awsCustomerId",
            contract.getCustomerId(),
            "customerAwsAccountId",
            contract.getBillingAccountId(),
            "sellerAccountId",
            contract.getSellerAccountId()));
    body.put(
        "purchase",
        Map.of(
            "vendorProductCode",
            contract.getProductCode(),
            "contracts",
            java.util.List.of(buildContractDetails(contract))));
    body.put("rhEntitlements", buildRhEntitlements(contract));
    return body;
  }

  /**
   * Build Azure contract response body for Partner Gateway API. Can be used for both single
   * contract and sync endpoints.
   *
   * @param contract the contract data
   * @return Azure contract response body map
   */
  private Map<String, Object> buildAzureContractBody(Contract contract) {
    var contractDetails = new java.util.HashMap<>(buildContractDetails(contract));
    contractDetails.put("planId", contract.getPlanId());

    var body = new java.util.HashMap<String, Object>();
    body.put("rhAccountId", contract.getOrgId());
    body.put("sourcePartner", "azure_marketplace");
    body.put("entitlementDates", buildEntitlementDates(contract));
    body.put(
        "partnerIdentities",
        Map.of(
            "azureSubscriptionId",
            contract.getBillingAccountId(),
            "azureTenantId",
            contract.getBillingAccountId(),
            "azureCustomerId",
            contract.getCustomerId(),
            "clientId",
            contract.getClientId()));
    body.put(
        "purchase",
        Map.of(
            "vendorProductCode",
            contract.getProductCode(),
            "azureResourceId",
            contract.getResourceId(),
            "contracts",
            java.util.List.of(contractDetails)));
    body.put("rhEntitlements", buildRhEntitlements(contract));
    return body;
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
}
