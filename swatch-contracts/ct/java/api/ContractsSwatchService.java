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
import static com.redhat.swatch.component.tests.utils.SwatchUtils.securityHeadersWithServiceRole;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.component.tests.utils.JsonUtils;
import com.redhat.swatch.contract.test.model.CapacityReportByMetricId;
import com.redhat.swatch.contract.test.model.ContractRequest;
import com.redhat.swatch.contract.test.model.SkuCapacityReportV2;
import com.redhat.swatch.contract.test.model.SkuCapacityV2;
import domain.Contract;
import domain.Product;
import domain.Subscription;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import utils.ContractRequestMapper;
import utils.SubscriptionRequestMapper;

public class ContractsSwatchService extends SwatchService {

  private static final String ENDPOINT_PREFIX = "/api/swatch-contracts/internal";
  private static final String OFFERING_SYNC_ENDPOINT = ENDPOINT_PREFIX + "/rpc/offerings/sync/%s";
  private static final String RESET_DATA_ENDPOINT = ENDPOINT_PREFIX + "/rpc/reset/%s";
  private static final String CONTRACTS_ENDPOINT = ENDPOINT_PREFIX + "/contracts";
  private static final String SUBSCRIPTIONS_ENDPOINT = ENDPOINT_PREFIX + "/subscriptions";
  private static final String GET_SKU_ENDPOINT =
      "/api/rhsm-subscriptions/v2/subscriptions/products/{product_id}";
  private static final String CAPACITY_REPORT_ENDPOINT =
      "/api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}";
  private static final String TERMINATE_SUBSCRIPTION_ENDPOINT =
      ENDPOINT_PREFIX + "/subscriptions/terminate/{subscription_id}";

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

  public Optional<SkuCapacityV2> getSkuCapacityByProductIdForOrgAndSku(
      Product product, String orgId, String sku) {
    Objects.requireNonNull(sku, "sku must not be null");
    SkuCapacityReportV2 report = getSkuCapacityByProductIdForOrg(product, orgId);
    assertNotNull(report.getData());
    return report.getData().stream().filter(d -> sku.equals(d.getSku())).findFirst();
  }

  public SkuCapacityReportV2 getSkuCapacityBySubscription(Subscription subscription) {
    Objects.requireNonNull(subscription, "subscription must not be null");
    return getSkuCapacityByProductIdForOrg(subscription.getProduct(), subscription.getOrgId());
  }

  public SkuCapacityReportV2 getSkuCapacityByProductIdForOrg(Product product, String orgId) {
    Objects.requireNonNull(product, "product id must not be null");
    Objects.requireNonNull(orgId, "org id must not be null");

    return given()
        .headers(securityHeadersWithServiceRole(orgId))
        .accept("application/vnd.api+json")
        .pathParam("product_id", product.getName())
        .get(GET_SKU_ENDPOINT)
        .then()
        .extract()
        .as(SkuCapacityReportV2.class);
  }

  public CapacityReportByMetricId getCapacityReportByMetricId(
      Product product,
      String orgId,
      String metricId,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      String granularity,
      String category) {
    Objects.requireNonNull(product, "product must not be null");
    Objects.requireNonNull(orgId, "orgId must not be null");
    Objects.requireNonNull(metricId, "metricId must not be null");
    Objects.requireNonNull(beginning, "beginning must not be null");
    Objects.requireNonNull(ending, "ending must not be null");
    Objects.requireNonNull(granularity, "granularity must not be null");

    var request =
        given()
            .headers(securityHeadersWithServiceRole(orgId))
            .accept("application/vnd.api+json")
            .pathParam("product_id", product.getName())
            .pathParam("metric_id", metricId)
            .queryParam("beginning", beginning.toString())
            .queryParam("ending", ending.toString())
            .queryParam("granularity", granularity);

    if (category != null) {
      request.queryParam("category", category);
    }

    return request
        .when()
        .get(CAPACITY_REPORT_ENDPOINT)
        .then()
        .statusCode(SC_OK)
        .and()
        .extract()
        .as(CapacityReportByMetricId.class);
  }

  public Response terminateSubscription(Subscription subscription) {
    return terminateSubscription(subscription, OffsetDateTime.now());
  }

  public Response terminateSubscription(Subscription subscription, OffsetDateTime timestamp) {
    Objects.requireNonNull(subscription, "contract must not be null");
    Objects.requireNonNull(subscription.getSubscriptionId(), "subscriptionId must not be null");
    Objects.requireNonNull(timestamp, "timestamp must not be null");

    return given()
        .headers(SECURITY_HEADERS)
        .pathParam("subscription_id", subscription.getSubscriptionId())
        .queryParam("timestamp", timestamp.toString())
        .when()
        .post(TERMINATE_SUBSCRIPTION_ENDPOINT);
  }
}
