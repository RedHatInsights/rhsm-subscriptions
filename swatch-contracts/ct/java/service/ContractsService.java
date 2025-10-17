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
package service;

import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.contract.test.model.ContractRequest;
import io.restassured.response.Response;
import java.util.Objects;
import model.ContractTestData;

/** Service class for contracts service component tests. */
public class ContractsService extends SwatchService {

  private static final String OFFERING_SYNC_ENDPOINT =
      "/api/swatch-contracts/internal/rpc/offerings/sync/%s";
  private static final String GET_CONTRACTS_ENDPOINT = "/api/swatch-contracts/internal/contracts";
  private static final String CREATE_CONTRACT_ENDPOINT = "/api/swatch-contracts/internal/contracts";

  public Response syncOffering(String sku) {
    Objects.requireNonNull(sku, "sku must not be null");

    String endpoint = String.format(OFFERING_SYNC_ENDPOINT, sku);
    return given().when().put(endpoint);
  }

  public Response getContracts(
      String orgId, String billingProvider, String billingAccountId, String productTag) {
    Objects.requireNonNull(orgId, "orgId must not be null");
    Objects.requireNonNull(billingProvider, "billingProvider must not be null");
    Objects.requireNonNull(billingAccountId, "billingAccountId must not be null");
    Objects.requireNonNull(productTag, "productTag must not be null");

    return given()
        .queryParam("org_id", orgId)
        .queryParam("billing_provider", billingProvider)
        .queryParam("billing_account_id", billingAccountId)
        .queryParam("product_tag", productTag)
        .when()
        .get(GET_CONTRACTS_ENDPOINT);
  }

  public Response createContract(ContractTestData contractData) {
    Objects.requireNonNull(contractData, "contractData must not be null");

    ContractRequest contractRequest =
        helpers.ContractsTestHelper.buildContractRequest(contractData);
    return given()
        .contentType("application/json")
        .body(contractRequest)
        .when()
        .post(CREATE_CONTRACT_ENDPOINT);
  }
}
