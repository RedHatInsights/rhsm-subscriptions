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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.component.tests.utils.JsonUtils;
import com.redhat.swatch.contract.product.umb.CanonicalMessage;
import com.redhat.swatch.contract.test.model.CapacityReportByMetricId;
import com.redhat.swatch.contract.test.model.ContractRequest;
import com.redhat.swatch.contract.test.model.GranularityType;
import com.redhat.swatch.contract.test.model.ReportCategory;
import com.redhat.swatch.contract.test.model.ServiceLevelType;
import com.redhat.swatch.contract.test.model.SkuCapacityReportV2;
import com.redhat.swatch.contract.test.model.SkuCapacityV2;
import com.redhat.swatch.contract.test.model.SubscriptionResponse;
import com.redhat.swatch.contract.test.model.UsageType;
import domain.BillingProvider;
import domain.Contract;
import domain.Product;
import domain.Subscription;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
import utils.CanonicalMessageMapper;
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
  private static final String SYNC_CONTRACTS_BY_ORG_ENDPOINT =
      ENDPOINT_PREFIX + "/rpc/sync/contracts/%s";
  private static final String SYNC_ALL_CONTRACTS_ENDPOINT = "/internal/rpc/syncAllContracts";
  private static final String SYNC_ALL_SUBSCRIPTIONS_ENDPOINT =
      ENDPOINT_PREFIX + "/rpc/subscriptions/sync";
  private static final String SUBSCRIPTIONS_UMB_ENDPOINT = ENDPOINT_PREFIX + "/subscriptions/umb";
  private static final String SYNC_SUBSCRIPTIONS_FOR_CONTRACTS_BY_ORG_ENDPOINT =
      ENDPOINT_PREFIX + "/rpc/sync/contracts/%s/subscriptions";
  private static final String SKU_PRODUCT_TAGS_ENDPOINT =
      ENDPOINT_PREFIX + "/offerings/%s/product_tags";
  private static final String FORCE_RECONCILE_OFFERING_ENDPOINT =
      ENDPOINT_PREFIX + "/rpc/offerings/reconcile/%s";

  public Response syncOffering(String sku) {
    Objects.requireNonNull(sku, "sku must not be null");

    String endpoint = String.format(OFFERING_SYNC_ENDPOINT, sku);
    return given().headers(SECURITY_HEADERS).when().put(endpoint);
  }

  public List<com.redhat.swatch.contract.test.model.Contract> getContracts(Contract contract) {
    Objects.requireNonNull(contract.getOrgId(), "orgId must not be null");
    Objects.requireNonNull(contract.getBillingProvider(), "billingProvider must not be null");
    Objects.requireNonNull(contract.getBillingAccountId(), "billingAccountId must not be null");
    Objects.requireNonNull(contract.getProduct().getName(), "productTag must not be null");

    return getContracts(
        Map.of(
            "org_id",
            contract.getOrgId(),
            "billing_provider",
            contract.getBillingProvider().toApiModel(),
            "billing_account_id",
            contract.getBillingAccountId(),
            "product_tag",
            contract.getProduct().getName()));
  }

  public List<com.redhat.swatch.contract.test.model.Contract> getContractsByOrgId(String orgId) {
    Objects.requireNonNull(orgId, "orgId must not be null");
    return getContracts(Map.of("org_id", orgId));
  }

  public List<com.redhat.swatch.contract.test.model.Contract> getContractsByOrgIdAndTimestamp(
      String orgId, OffsetDateTime timestamp) {
    Objects.requireNonNull(orgId, "orgId must not be null");
    Objects.requireNonNull(timestamp, "timestamp must not be null");
    return getContracts(Map.of("org_id", orgId, "timestamp", timestamp.toString()));
  }

  public List<com.redhat.swatch.contract.test.model.Contract> getContractsByOrgIdAndBillingProvider(
      String orgId, BillingProvider billingProvider) {
    Objects.requireNonNull(orgId, "orgId must not be null");
    Objects.requireNonNull(billingProvider, "billingProvider must not be null");
    return getContracts(Map.of("org_id", orgId, "billing_provider", billingProvider.toApiModel()));
  }

  public List<com.redhat.swatch.contract.test.model.Contract>
      getContractsByOrgIdAndBillingProviderAndTimestamp(
          String orgId, BillingProvider billingProvider, OffsetDateTime timestamp) {
    Objects.requireNonNull(orgId, "orgId must not be null");
    Objects.requireNonNull(billingProvider, "billingProvider must not be null");
    Objects.requireNonNull(timestamp, "timestamp must not be null");
    return getContracts(
        Map.of(
            "org_id",
            orgId,
            "billing_provider",
            billingProvider.toApiModel(),
            "timestamp",
            timestamp.toString()));
  }

  public Response createContract(Contract contract) {
    Objects.requireNonNull(contract, "contract must not be null");

    ContractRequest contractRequest = ContractRequestMapper.buildContractRequest(contract);
    return createContract(contractRequest);
  }

  public Response createContract(ContractRequest contractRequest) {
    Objects.requireNonNull(contractRequest, "contractRequest must not be null");

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

  public Response deleteContract(String contractUuid) {
    Objects.requireNonNull(contractUuid, "contractUuid must not be null");
    return given().headers(SECURITY_HEADERS).when().delete(CONTRACTS_ENDPOINT + "/" + contractUuid);
  }

  public List<com.redhat.swatch.contract.test.model.Subscription> getSubscriptionsByOrgId(
      String orgId) {
    return given()
        .headers(SECURITY_HEADERS)
        .when()
        .queryParam("org_id", orgId)
        .get(SUBSCRIPTIONS_ENDPOINT)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .extract()
        .as(new TypeRef<>() {});
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
      GranularityType granularity,
      ReportCategory category) {
    return getCapacityReportByMetricId(
        product, orgId, metricId, beginning, ending, granularity, category, null, null, null);
  }

  public CapacityReportByMetricId getCapacityReportByMetricId(
      Product product,
      String orgId,
      String metricId,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      GranularityType granularity,
      ReportCategory category,
      ServiceLevelType sla,
      UsageType usage,
      String billingAccountId) {
    return getCapacityReportByMetricId(
        product,
        orgId,
        metricId,
        beginning,
        ending,
        granularity,
        category,
        sla,
        usage,
        billingAccountId,
        null,
        null);
  }

  public CapacityReportByMetricId getCapacityReportByMetricId(
      Product product,
      String orgId,
      String metricId,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      GranularityType granularity,
      Integer offset,
      Integer limit) {
    return getCapacityReportByMetricId(
        product,
        orgId,
        metricId,
        beginning,
        ending,
        granularity,
        null,
        null,
        null,
        null,
        offset,
        limit);
  }

  public CapacityReportByMetricId getCapacityReportByMetricId(
      Product product,
      String orgId,
      String metricId,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      GranularityType granularity,
      ReportCategory category,
      ServiceLevelType sla,
      UsageType usage,
      String billingAccountId,
      Integer offset,
      Integer limit) {
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
    if (sla != null) {
      request.queryParam("sla", sla);
    }
    if (usage != null) {
      request.queryParam("usage", usage);
    }
    if (billingAccountId != null) {
      request.queryParam("billing_account_id", billingAccountId);
    }

    if (offset != null) {
      request.queryParam("offset", offset);
    }

    if (limit != null) {
      request.queryParam("limit", limit);
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

  /**
   * Get capacity report with raw granularity string (for testing invalid values).
   *
   * @return Raw Response object for status code validation
   */
  public Response getCapacityReportByMetricIdRaw(
      Product product,
      String orgId,
      String metricId,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      String granularity,
      ReportCategory category) {
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

    return request.when().get(CAPACITY_REPORT_ENDPOINT);
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

  public Response syncContractsByOrg(String orgId) {
    return syncContractsByOrg(orgId, false, false);
  }

  public Response syncContractsByOrg(
      String orgId, Boolean isPreCleanup, Boolean deleteContractsAndSubs) {
    Objects.requireNonNull(orgId, "orgId must not be null");

    String endpoint = SYNC_CONTRACTS_BY_ORG_ENDPOINT.formatted(orgId);
    return given()
        .headers(SECURITY_HEADERS)
        .queryParam("is_pre_cleanup", isPreCleanup)
        .queryParam("delete_contracts_and_subs", deleteContractsAndSubs)
        .when()
        .post(endpoint);
  }

  public Response syncAllContracts() {
    return given().headers(SECURITY_HEADERS).when().post(SYNC_ALL_CONTRACTS_ENDPOINT);
  }

  public Response syncAllSubscriptions() {
    return given().headers(SECURITY_HEADERS).when().put(SYNC_ALL_SUBSCRIPTIONS_ENDPOINT);
  }

  public SubscriptionResponse syncUmbSubscription(Subscription subscription) {
    XmlMapper mapper = CanonicalMessage.createMapper();
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    try {
      return given()
          .headers(SECURITY_HEADERS)
          .when()
          .contentType(ContentType.XML)
          .body(
              mapper.writeValueAsString(CanonicalMessageMapper.mapActiveSubscription(subscription)))
          .post(SUBSCRIPTIONS_UMB_ENDPOINT)
          .then()
          .statusCode(SC_OK)
          .extract()
          .as(SubscriptionResponse.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public Response syncSubscriptionsForContractsByOrg(String orgId) {
    Objects.requireNonNull(orgId, "orgId must not be null");

    String endpoint = SYNC_SUBSCRIPTIONS_FOR_CONTRACTS_BY_ORG_ENDPOINT.formatted(orgId);
    return given().headers(SECURITY_HEADERS).when().post(endpoint);
  }

  public Response getSkuProductTags(String sku) {
    Objects.requireNonNull(sku, "sku must not be null");

    String endpoint = String.format(SKU_PRODUCT_TAGS_ENDPOINT, sku);
    return given().headers(SECURITY_HEADERS).accept("application/json").when().get(endpoint);
  }

  public Response forceReconcileOffering(String sku) {
    Objects.requireNonNull(sku, "sku must not be null");

    String endpoint = String.format(FORCE_RECONCILE_OFFERING_ENDPOINT, sku);
    return given().headers(SECURITY_HEADERS).when().put(endpoint);
  }

  private List<com.redhat.swatch.contract.test.model.Contract> getContracts(
      Map<String, ?> queryParams) {
    return given()
        .headers(SECURITY_HEADERS)
        .queryParams(queryParams)
        .when()
        .get(CONTRACTS_ENDPOINT)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .extract()
        .as(new TypeRef<>() {});
  }
}
