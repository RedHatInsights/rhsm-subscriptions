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
package tests;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

import dto.ContractDataDto;
import dto.OfferingDataDto;
import helpers.ContractsTestHelper;
import helpers.OfferingTestHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class ContractsComponentTest extends BaseContractComponentTest {

  /** Verify contract is created when all required data is provided. */
  @Test
  @Tag("contract")
  public void testCreateContractWithValidData() {
    // Setup offering data before running this test since it is required for contract creation
    OfferingTestHelper.setupOfferingData(
        offeringWiremock,
        service,
        OfferingDataDto.builder()
            .sku("MW02393")
            .description("Test component for ROSA")
            .level1("OpenShift")
            .level2("ROSA - RH OpenShift on AWS")
            .metered("Y")
            .build());

    // Setup test data for contract creation
    ContractDataDto contractData =
        ContractDataDto.builder()
            .orgId("org123")
            .subscriptionId("sub14968327")
            .subscriptionNumber("14968327")
            .awsCustomerId("HCwCpt6sqkC")
            .awsAccountId("168056954830")
            .productCode("1n58d3s3qpvk22dgew2gal7w3")
            .build();

    // Setup WireMock to return partner entitlement
    wiremock.stubPartnerEntitlementSuccess(
        contractData.getAwsCustomerId(),
        contractData.getAwsAccountId(),
        contractData.getProductCode(),
        contractData.getOrgId());

    // Create contract request body
    String contractRequest = ContractsTestHelper.createContractRequest(contractData);

    // Send contract creation request via REST API. It can be replaced with Kafka when we move from
    // UMB to Kafka
    service
        .given()
        .contentType("application/json")
        .body(contractRequest)
        .when()
        .post("/api/swatch-contracts/internal/contracts")
        .then()
        .statusCode(200);

    // Verify contract was created with correct data
    service
        .given()
        .queryParam("org_id", contractData.getOrgId())
        .when()
        .get("/api/swatch-contracts/internal/contracts")
        .then()
        .statusCode(200)
        .body("size()", greaterThan(0))
        .body("[0].org_id", equalTo(contractData.getOrgId()))
        .body("[0].subscription_number", equalTo(contractData.getSubscriptionNumber()))
        .body("[0].sku", equalTo("MW02393"))
        .body("[0].billing_provider", equalTo("aws"))
        .body("[0].metrics", notNullValue())
        .body("[0].metrics.size()", greaterThan(0));
  }
}
