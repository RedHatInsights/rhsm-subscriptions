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

import static domain.Contract.buildRosaContract;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;

import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import domain.BillingProvider;
import domain.Contract;
import domain.Product;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class ContractsComponentTest extends BaseContractComponentTest {

  @Test
  @Tag("contract")
  void shouldCreatePrepaidRosaContract_whenAllDataIsValid() {
    // The metric Cores is valid for the rosa product
    Contract contractData = buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0));
    givenContractIsCreated(contractData);

    // Retrieve and verify contract
    Response getContractsResponse = service.getContracts(contractData);
    assertThat(
        "Contract retrieval call should succeed", getContractsResponse.statusCode(), is(200));

    getContractsResponse
        .then()
        .body("size()", equalTo(1))
        .body("[0].org_id", equalTo(orgId))
        .body("[0].subscription_number", equalTo(contractData.getSubscriptionNumber()))
        .body("[0].billing_account_id", equalTo(contractData.getBillingAccountId()))
        .body("[0].sku", equalTo(contractData.getOffering().getSku()))
        .body("[0].metrics", notNullValue())
        .body("[0].metrics.size()", greaterThan(0));
  }

  /** Verify pure pay-as-you-go ROSA contract is created when all dimensions are incorrect. */
  @Test
  @Tag("contract")
  void shouldCreatePurePaygRosaContract_whenAllDimensionsAreIncorrect() {
    // The metric Instance-hours is NOT valid for the rosa product, so it should be ignored
    Contract contractData =
        buildRosaContract(
            orgId, BillingProvider.AWS, Map.of(MetricIdUtils.getInstanceHours(), 10.0));
    givenContractIsCreated(contractData);

    // Retrieve and verify contract
    Response getContractsResponse = service.getContracts(contractData);
    assertThat(
        "Contract retrieval call should succeed", getContractsResponse.statusCode(), is(200));

    // Having metrics size as zero is what is indicating that this is pure paygo because there are
    // no valid prepaid metric amounts
    getContractsResponse
        .then()
        .body("size()", equalTo(1))
        .body("[0].org_id", equalTo(orgId))
        .body("[0].subscription_number", equalTo(contractData.getSubscriptionNumber()))
        .body("[0].billing_account_id", equalTo(contractData.getBillingAccountId()))
        .body("[0].sku", equalTo(contractData.getOffering().getSku()))
        .body("[0].metrics.size()", equalTo(0));
  }

  @Test
  @Tag("contract")
  @Tag("contracts-termination-TC006")
  void shouldDecreaseCapacityWhenContractIsTerminated() {
    String productId = Product.ROSA.getName();

    // Arrange: create a ROSA contract that maps AWS dimensions to CORES
    Contract contractData = buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 8.0));
    givenContractIsCreated(contractData);

    // Get the initial capacity (uses productId path param, org from identity header)
    await("Capacity should increase")
        .atMost(1, MINUTES)
        .pollInterval(1, SECONDS)
        .until(
            () -> {
              return getCapacityCount(productId, orgId) > 0;
            });

    final int initialCapacity = getCapacityCount(productId, orgId);

    // Act: Terminate the contract and get the new capacity
    Response terminateContractResponse = service.terminateContract(contractData);
    assertThat(
        "Terminate contract failed: subscriptionId="
            + contractData.getSubscriptionId()
            + ", status="
            + terminateContractResponse.statusCode()
            + ", body="
            + terminateContractResponse.asString(),
        terminateContractResponse.statusCode(),
        is(200));

    await("Capacity should decrease")
        .atMost(1, MINUTES)
        .pollInterval(1, SECONDS)
        .until(
            () -> {
              int currentCapcity = getCapacityCount(productId, orgId);
              return (currentCapcity < initialCapacity);
            });
    int newCapacity = getCapacityCount(productId, orgId);

    Log.info("Initial capacity: %d, New capacity: %d\n", initialCapacity, newCapacity);

    // Assert: Verify the contract was terminated and the capacity was decreased
    assertThat("Termination should succeed", terminateContractResponse.statusCode(), is(200));
    assertThat("Capacity should have decreased", newCapacity, lessThan(initialCapacity));
    assertThat("Capacity should be 0", newCapacity, equalTo(0));
  }

  @Test
  @SuppressWarnings("unchecked")
  @Tag("contract")
  @Tag("contracts-termination-TC006")
  void shouldUpdateSubscriptionTableWhenContractIsTerminated() {
    String productId = Product.RHEL.getName();
    String sku = "RH00006";

    // Arrange: stub/sync offering and save a PAYG subscription
    stubOfferingAndSync(sku, 4.0, 1.0);
    String paygSubId = saveSubscriptionForOrgAndSku(orgId, sku);

    // Pre-condition: verify active-only subscriptions include our subscription id
    Response preReport = getCapacityReport(productId, orgId);
    List<Map<String, Object>> items = preReport.jsonPath().getList("data");

    Optional<Map<String, Object>> skuItem =
        items.stream().filter(i -> sku.equals(i.get("sku"))).findFirst();

    assertThat("SKU item should be present", skuItem.isPresent(), is(true));

    Map<String, Object> item = skuItem.get();
    List<Map<String, Object>> subs = (List<Map<String, Object>>) item.get("subscriptions");

    boolean containsId = false;

    containsId =
        subs.stream()
            .anyMatch(i -> i.get("id") != null && i.get("id").toString().equals(paygSubId));

    assertThat("Active subscriptions should include created subscription id", containsId, is(true));

    // Verify active-only subscriptions include our subscription id and provide next event info
    // Act: terminate the PAYG subscription
    OffsetDateTime termination = OffsetDateTime.now().minusHours(2).withNano(0);
    Response subscriptionStatus = service.terminateSubscription(paygSubId, termination);
    assertThat(
        "Terminate subscription failed: subscriptionId="
            + paygSubId
            + ", status="
            + subscriptionStatus.statusCode()
            + ", body="
            + subscriptionStatus.asString(),
        subscriptionStatus.statusCode(),
        is(200));

    // Verify the terminated subscription is no longer listed as active for the SKU (with a short
    // poll)
    await("SKU should be removed from active subscriptions")
        .atMost(1, MINUTES)
        .pollInterval(1, SECONDS)
        .until(
            () -> {
              Response postReport = getCapacityReport(productId, orgId);
              List<Map<String, Object>> postItems = postReport.jsonPath().getList("data");

              if (postItems != null) {
                for (Map<String, Object> it : postItems) {
                  if (sku.equals(it.get("sku"))) {
                    return false; // SKU found - keep waiting
                  }
                }
              }
              return true; // SKU not found - condition met, stop waiting
            });

    Response finalReport = getCapacityReport(productId, orgId);
    List<Map<String, Object>> finalItems = finalReport.jsonPath().getList("data");
    assertThat(
        "SKU should be removed from capacity report",
        finalItems.stream().noneMatch(it -> sku.equals(it.get("sku"))),
        is(true));
  }
}
