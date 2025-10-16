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
package services;

import com.redhat.swatch.component.tests.api.SwatchService;
import dto.ContractTestData;
import helpers.ContractsTestHelper;
import io.restassured.response.Response;
import wiremock.ContractsWiremockService;

/** Service layer for contract-related component test workflows. */
public class ContractsTestService {

  private final SwatchService service;
  private final ContractsWiremockService contractsWiremock;

  public ContractsTestService(SwatchService service, ContractsWiremockService contractsWiremock) {
    this.service = service;
    this.contractsWiremock = contractsWiremock;
  }

  public void setupPartnerEntitlementStub(ContractTestData contractData) {
    contractsWiremock.stubPartnerEntitlementSuccess(
        contractData.getAwsCustomerId(),
        contractData.getAwsAccountId(),
        contractData.getProductCode(),
        contractData.getOrgId());
  }

  public Response createContract(ContractTestData contractData) {
    var contractRequest = ContractsTestHelper.buildContractRequest(contractData);

    return service
        .given()
        .contentType("application/json")
        .body(contractRequest)
        .when()
        .post("/api/swatch-contracts/internal/contracts");
  }

  public Response getContracts(
      String orgId, String billingProvider, String billingAccountId, String productTag) {
    return service
        .given()
        .queryParam("org_id", orgId)
        .queryParam("billing_provider", billingProvider)
        .queryParam("billing_account_id", billingAccountId)
        .queryParam("product_tag", productTag)
        .when()
        .get("/api/swatch-contracts/internal/contracts");
  }
}
