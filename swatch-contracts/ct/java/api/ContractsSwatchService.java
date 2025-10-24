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

import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.contract.test.model.ContractRequest;
import domain.Contract;
import io.restassured.response.Response;
import java.util.Objects;
import utils.ContractRequestMapper;

public class ContractsSwatchService extends SwatchService {

  private static final String OFFERING_SYNC_ENDPOINT =
      "/api/swatch-contracts/internal/rpc/offerings/sync/%s";
  private static final String GET_CONTRACTS_ENDPOINT = "/api/swatch-contracts/internal/contracts";
  private static final String CREATE_CONTRACT_ENDPOINT = "/api/swatch-contracts/internal/contracts";

  public Response syncOffering(String sku) {
    Objects.requireNonNull(sku, "sku must not be null");

    String endpoint = String.format(OFFERING_SYNC_ENDPOINT, sku);
    return given().when().put(endpoint);
  }

  public Response getContracts(Contract contract) {
    Objects.requireNonNull(contract.getOrgId(), "orgId must not be null");
    Objects.requireNonNull(contract.getBillingProvider(), "billingProvider must not be null");
    Objects.requireNonNull(contract.getBillingAccountId(), "billingAccountId must not be null");
    Objects.requireNonNull(contract.getProductId(), "productTag must not be null");

    return given()
        .queryParam("org_id", contract.getOrgId())
        .queryParam("billing_provider", contract.getBillingProvider().toApiModel())
        .queryParam("billing_account_id", contract.getBillingAccountId())
        .queryParam("product_tag", contract.getProductId())
        .when()
        .get(GET_CONTRACTS_ENDPOINT);
  }

  public Response createContract(Contract contract) {
    Objects.requireNonNull(contract, "contract must not be null");

    ContractRequest contractRequest = ContractRequestMapper.buildContractRequest(contract);
    return given()
        .contentType("application/json")
        .body(contractRequest)
        .when()
        .post(CREATE_CONTRACT_ENDPOINT);
  }
}
