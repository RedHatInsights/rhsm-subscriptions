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

import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.component.tests.utils.JsonUtils;
import com.redhat.swatch.contract.test.model.ContractRequest;
import domain.Contract;
import domain.Subscription;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Objects;
import java.util.stream.Stream;
import utils.ContractRequestMapper;
import utils.SubscriptionRequestMapper;

public class ContractsSwatchService extends SwatchService {

  private static final String ENDPOINT_PREFIX = "/api/swatch-contracts/internal";
  private static final String OFFERING_SYNC_ENDPOINT = ENDPOINT_PREFIX + "/rpc/offerings/sync/%s";
  private static final String RESET_DATA_ENDPOINT = ENDPOINT_PREFIX + "/rpc/reset/%s";
  private static final String CONTRACTS_ENDPOINT = ENDPOINT_PREFIX + "/contracts";
  private static final String SUBSCRIPTIONS_ENDPOINT = ENDPOINT_PREFIX + "/subscriptions";

  public Response syncOffering(String sku) {
    Objects.requireNonNull(sku, "sku must not be null");

    String endpoint = String.format(OFFERING_SYNC_ENDPOINT, sku);
    return given().headers(SECURITY_HEADERS).when().put(endpoint);
  }

  public Response getContracts(Contract contract) {
    Objects.requireNonNull(contract.getOrgId(), "orgId must not be null");
    Objects.requireNonNull(contract.getBillingProvider(), "billingProvider must not be null");
    Objects.requireNonNull(contract.getBillingAccountId(), "billingAccountId must not be null");
    Objects.requireNonNull(contract.getProduct().getName(), "productTag must not be null");

    return given()
        .headers(SECURITY_HEADERS)
        .queryParam("org_id", contract.getOrgId())
        .queryParam("billing_provider", contract.getBillingProvider().toApiModel())
        .queryParam("billing_account_id", contract.getBillingAccountId())
        .queryParam("product_tag", contract.getProduct().getName())
        .when()
        .get(CONTRACTS_ENDPOINT);
  }

  public Response createContract(Contract contract) {
    Objects.requireNonNull(contract, "contract must not be null");

    ContractRequest contractRequest = ContractRequestMapper.buildContractRequest(contract);
    return given()
        .headers(SECURITY_HEADERS)
        .contentType("application/json")
        .body(contractRequest)
        .when()
        .post(CONTRACTS_ENDPOINT);
  }

  public Response deleteDataForOrg(String orgId) {
    Objects.requireNonNull(orgId, "orgId must not be null");
    return given().headers(SECURITY_HEADERS).delete(RESET_DATA_ENDPOINT.formatted(orgId));
  }

  public Response saveSubscriptions(Subscription... subscriptions) {
    return saveSubscriptions(true, subscriptions);
  }

  public Response saveSubscriptions(Boolean reconcileCapacity, Subscription... subscriptions) {
    Objects.requireNonNull(subscriptions, "subscriptions must not be null");

    var list =
        Stream.of(subscriptions).map(SubscriptionRequestMapper::buildSubscriptionRequest).toList();
    return given()
        .headers(SECURITY_HEADERS)
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .queryParam("reconcileCapacity", reconcileCapacity)
        .body(JsonUtils.toJson(list))
        .when()
        .post(SUBSCRIPTIONS_ENDPOINT);
  }
}
