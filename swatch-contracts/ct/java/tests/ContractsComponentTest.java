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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.contract.test.model.SkuCapacityV2;
import com.redhat.swatch.contract.test.model.SubscriptionEventType;
import domain.BillingProvider;
import domain.Contract;
import domain.Offering;
import domain.Product;
import domain.Subscription;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class ContractsComponentTest extends BaseContractComponentTest {
  private static final double ROSA_CORES_CAPACITY = 8.0;
  private static final double RHEL_CORES_CAPACITY = 4.0;
  private static final double RHEL_SOCKETS_CAPACITY = 1.0;
  private static final String HYPERVISOR_SKU = RandomUtils.generateRandom();
  private static final String HYPERVISOR_PRODUCT_DESCRIPTION =
      "Test component for RHEL for Virtual Datacenters";

  @Test
  @Tag("contract")
  void shouldCreatePrepaidRosaContract_whenAllDataIsValid() {
    // The metric Cores is valid for the rosa product
    Contract contractData = buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0));
    givenContractIsCreated(contractData);

    // Retrieve and verify contract
    Response getContractsResponse = service.getContracts(contractData);
    assertThat(
        "Contract retrieval call should succeed",
        getContractsResponse.statusCode(),
        is(HttpStatus.SC_OK));

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
        "Contract retrieval call should succeed",
        getContractsResponse.statusCode(),
        is(HttpStatus.SC_OK));

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
    // Given: An active ROSA contract exists with capacity
    Contract contract =
        buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, ROSA_CORES_CAPACITY));
    givenContractIsCreated(contract);
    int initialCapacity = givenCapacityIsIncreased(contract);

    // When: The contract is terminated
    Response terminateResponse = service.terminateSubscription(contract);
    assertThat(
        "Terminate contract should succeed", terminateResponse.statusCode(), is(HttpStatus.SC_OK));

    // Then: The capacity decreases to zero
    int newCapacity = thenCapacityIsDecreased(contract, initialCapacity);
    assertThat("Capacity should have decreased", newCapacity, lessThan(initialCapacity));
    assertThat("Capacity should be 0", newCapacity, equalTo(0));
  }

  @Test
  @Tag("contract")
  @Tag("contracts-termination-TC007")
  void shouldUpdateSubscriptionTableWhenContractIsTerminated() {
    // Given: An active subscription exists for a RHEL product
    var subscription = givenRhelSubscriptionIsActiveInCapacityReport();

    // When: The subscription is terminated (setting end date to tomorrow at 23:59:59)
    OffsetDateTime terminationDate =
        OffsetDateTime.now().plusDays(1).withHour(23).withMinute(59).withSecond(59).withNano(0);
    Response terminationResponse = service.terminateSubscription(subscription, terminationDate);
    assertThat(
        "Terminate subscription should succeed",
        terminationResponse.statusCode(),
        is(HttpStatus.SC_OK));

    // Then: The subscription table is updated with next_event_date and next_event_type
    thenSubscriptionIsUpdatedWithNextEventData(subscription);
  }

  /** Helper method to verify a subscription is active in the capacity report. */
  private Subscription givenRhelSubscriptionIsActiveInCapacityReport() {
    String sku = RandomUtils.generateRandom();

    // mock offering data
    wiremock
        .forProductAPI()
        .stubOfferingData(
            Offering.buildRhelOffering(sku, RHEL_CORES_CAPACITY, RHEL_SOCKETS_CAPACITY));
    assertThat(
        "Sync offering should succeed",
        service.syncOffering(sku).statusCode(),
        is(HttpStatus.SC_OK));

    // build RHEL subscription
    Subscription sub = Subscription.buildRhelSubscriptionUsingSku(orgId, Map.of(), sku);
    assertThat(
        "Building a Rhel subscription should succeed",
        service.saveSubscriptions(true, sub).statusCode(),
        is(HttpStatus.SC_OK));

    // get initial capacity
    Optional<SkuCapacityV2> skuItem =
        service.getSkuCapacityByProductIdForOrgAndSku(
            sub.getProduct(), sub.getOrgId(), sub.getOffering().getSku());
    assertTrue(skuItem.isPresent(), "SKU item should be present");

    var containsId =
        skuItem.stream()
            .filter(i -> i.getSubscriptions() != null)
            .flatMap(i -> i.getSubscriptions().stream())
            .anyMatch(s -> sub.getSubscriptionId().equals(s.getId()));
    assertTrue(containsId, "Active subscriptions should include created subscription id");
    return sub;
  }

  private void thenSubscriptionIsUpdatedWithNextEventData(Subscription subscription) {
    var skuItem =
        await("Subscription table should be updated with next event info")
            .atMost(1, MINUTES)
            .pollInterval(1, SECONDS)
            .until(
                () ->
                    service.getSkuCapacityByProductIdForOrgAndSku(
                        subscription.getProduct(),
                        subscription.getOrgId(),
                        subscription.getOffering().getSku()),
                s ->
                    s.isPresent()
                        && s.get().getNextEventType() != null
                        && s.get().getNextEventDate() != null);

    assertTrue(skuItem.isPresent(), "SKU item should be present");
    assertThat(
        "next_event_type should be 'Subscription End'",
        skuItem.get().getNextEventType(),
        equalTo(SubscriptionEventType.SUBSCRIPTION_END));
    assertThat("next_event_date should be set", skuItem.get().getNextEventDate(), notNullValue());
    verifyNextEventDateIsTomorrowAtEndOfDay(skuItem.get().getNextEventDate());
  }

  /** Helper method to verify next_event_date is tomorrow at end of day (23:59:59). */
  private void verifyNextEventDateIsTomorrowAtEndOfDay(OffsetDateTime nextEventDate) {
    OffsetDateTime tomorrow = OffsetDateTime.now().plusDays(1);

    assertThat(
        "next_event_date should be tomorrow",
        nextEventDate.toLocalDate(),
        equalTo(tomorrow.toLocalDate()));

    // Verify it's at the end of the day (allowing for timezone differences)
    // The hour could be 22 or 23 depending on timezone conversion
    assertThat(
        "next_event_date hour should be near end of day (22 or 23)",
        nextEventDate.getHour(),
        is(greaterThan(21)));
    assertThat("next_event_date minute should be 59", nextEventDate.getMinute(), equalTo(59));
    assertThat("next_event_date second should be 59", nextEventDate.getSecond(), equalTo(59));
  }

  @Test
  @Tag("capacity")
  void shouldValidateSumOfAllSocketsForHypervisorSkus() {

    // Given: Get initial hypervisor capacity via REST API
    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);
    double initialHypervisorSockets =
        getHypervisorSocketCapacity(Product.RHEL, orgId, beginning, ending);

    Subscription hypervisorSubscription = givenHypervisorSubscriptionIsCreated();

    // Then: Verify hypervisor capacity increased via REST API
    double expectedCapacity = initialHypervisorSockets + RHEL_SOCKETS_CAPACITY;
    double finalHypervisorSockets =
        await("Hypervisor capacity should increase")
            .atMost(1, MINUTES)
            .pollInterval(2, SECONDS)
            .until(
                () -> getHypervisorSocketCapacity(Product.RHEL, orgId, beginning, ending),
                capacity -> capacity >= expectedCapacity);

    assertThat(
        "Hypervisor sockets capacity should increase by subscription amount",
        finalHypervisorSockets,
        equalTo(expectedCapacity));

    // Then: Verify the hypervisor SKU details via subscription table API
    Optional<SkuCapacityV2> skuCapacity =
        service.getSkuCapacityByProductIdForOrgAndSku(Product.RHEL, orgId, HYPERVISOR_SKU);
    assertTrue(skuCapacity.isPresent(), "Hypervisor SKU should be present in subscription table");

    assertThat(
        "Hypervisor SKU product name should not be null",
        skuCapacity.get().getProductName(),
        notNullValue());

    var containsSubscription =
        skuCapacity.stream()
            .filter(i -> i.getSubscriptions() != null)
            .flatMap(i -> i.getSubscriptions().stream())
            .anyMatch(s -> hypervisorSubscription.getSubscriptionId().equals(s.getId()));
    assertTrue(containsSubscription, "Hypervisor SKU should contain the created subscription");
  }

  private Subscription givenHypervisorSubscriptionIsCreated() {
    // Given: A hypervisor offering with hypervisor sockets capacity
    wiremock
        .forProductAPI()
        .stubOfferingData(
            Offering.buildRhelHypervisorOffering(
                HYPERVISOR_SKU,
                RHEL_CORES_CAPACITY,
                RHEL_SOCKETS_CAPACITY,
                HYPERVISOR_PRODUCT_DESCRIPTION));
    assertThat(
        "Sync hypervisor offering should succeed",
        service.syncOffering(HYPERVISOR_SKU).statusCode(),
        is(HttpStatus.SC_OK));

    // When: Create a hypervisor subscription with hypervisor sockets
    Subscription hypervisorSubscription =
        Subscription.buildRhelSubscriptionUsingSku(
            orgId, Map.of(SOCKETS, RHEL_SOCKETS_CAPACITY), HYPERVISOR_SKU);
    assertThat(
        "Creating hypervisor subscription should succeed",
        service.saveSubscriptions(true, hypervisorSubscription).statusCode(),
        is(HttpStatus.SC_OK));
    return hypervisorSubscription;
  }
}
