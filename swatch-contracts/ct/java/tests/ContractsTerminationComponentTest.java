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

import static api.PartnerApiStubs.PartnerSubscriptionsStubRequest.forContract;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.DateUtils.assertDatesAreEqual;

import api.ContractsArtemisService;
import com.redhat.swatch.component.tests.api.Artemis;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.SkuCapacityV2;
import com.redhat.swatch.contract.test.model.SubscriptionEventType;
import domain.BillingProvider;
import domain.Contract;
import domain.Offering;
import domain.Subscription;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ContractsTerminationComponentTest extends BaseContractComponentTest {

  private static final double ROSA_CORES_CAPACITY = 8.0;
  private static final double RHEL_CORES_CAPACITY = 4.0;
  private static final double RHEL_SOCKETS_CAPACITY = 1.0;

  @Artemis static ContractsArtemisService artemis = new ContractsArtemisService();

  @TestPlanName("contracts-termination-TC001")
  @Test
  void shouldRemainActiveAfterReceivingMessageWithFutureEndDate() {
    // Given: An active contract exists
    Contract initialContract = givenContractCreatedViaMessageBroker();

    // When: A UMB message is received with a future end date
    whenUpdateContractViaMessageBroker(initialContract);

    // Then: The existing contract should remain active
    var actual = thenContractIsUpdatedViaMessageBroker(initialContract);
    assertNotNull(actual.getEndDate());
    assertTrue(
        actual.getEndDate().isAfter(OffsetDateTime.now()),
        "End date should still be in the future (contract remains active)");
  }

  @TestPlanName("contracts-termination-TC002")
  @Test
  void shouldUpdateEndDateAfterReceivingMessageWithNewEndDate() {
    // Given: An active contract exists
    Contract initialContract = givenContractCreatedViaMessageBroker();

    // When: A UMB message is received with a new end date
    Contract updatedContract = whenUpdateContractViaMessageBroker(initialContract);

    // Then: The contract end date should be updated to the value from the message
    var actual = thenContractIsUpdatedViaMessageBroker(initialContract);
    assertNotNull(actual.getEndDate(), "End date should not be null");
    assertDatesAreEqual(updatedContract.getEndDate(), actual.getEndDate());
  }

  @TestPlanName("contracts-termination-TC003")
  @Test
  void shouldUpdateEndDateForExistingTerminatedContractAfterReceivingMessage() {
    // Given: A terminated contract exists (end date in the past)
    Contract terminatedContract = givenContractTerminatedViaMessageBroker();

    // When: A UMB message is received with a new end date
    Contract updatedContract = whenUpdateContractViaMessageBroker(terminatedContract);

    // Then: The contract end date should be updated to the value from the message
    var actual = thenContractIsUpdatedViaMessageBroker(updatedContract);
    assertDatesAreEqual(actual.getEndDate(), updatedContract.getEndDate());
  }

  @TestPlanName("contracts-termination-TC004")
  @Test
  void shouldCreateContractAfterReceivingMessageForNonExistingContract() {
    // Given: No contract exists

    // When: A UMB message is received for a non-existing contract
    Contract contract =
        Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, ROSA_CORES_CAPACITY));
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(contract);
    service.syncOffering(contract.getOffering().getSku());
    whenUpdateContractViaMessageBroker(contract);

    // Wait for the contract to be processed
    AwaitilityUtils.until(() -> service.getContracts(contract).size(), is(1));

    // Then: The contract should be created with the end date from the message
    var actual = thenContractIsCreatedViaMessageBroker(contract);
    assertNotNull(actual.getEndDate(), "End date should not be null");
    assertTrue(
        actual.getEndDate().isAfter(OffsetDateTime.now()), "End date should be in the future");
  }

  @TestPlanName("contracts-termination-TC005")
  @Test
  void shouldReactivateTerminatedContractAfterReceivingMessageWithFutureEndDate() {
    // Given: A terminated contract exists (end date in the past)
    Contract terminatedContract = givenContractTerminatedViaMessageBroker();

    // When: A UMB message is received with a future end date
    Contract updatedContract = whenUpdateContractViaMessageBroker(terminatedContract);

    // Then: The contract should be reactivated with the future end date
    var actual = thenContractIsUpdatedViaMessageBroker(updatedContract);
    assertNotNull(actual.getEndDate(), "End date should not be null");
    assertDatesAreEqual(updatedContract.getEndDate(), actual.getEndDate());
  }

  @TestPlanName("contracts-termination-TC006")
  @Test
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

  @TestPlanName("contracts-termination-TC007")
  @Test
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

  private Contract givenContractTerminatedViaMessageBroker() {
    Contract contract =
        Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, ROSA_CORES_CAPACITY))
            // end date in the past
            .toBuilder()
            .endDate(OffsetDateTime.now().minusDays(1))
            .build();
    return whenSendContractViaMessageBroker(contract);
  }

  private Contract givenContractCreatedViaMessageBroker() {
    Contract contract =
        Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, ROSA_CORES_CAPACITY));
    return whenSendContractViaMessageBroker(contract);
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

  private Contract whenUpdateContractViaMessageBroker(Contract initialContract) {
    Contract updatedContract =
        initialContract.toBuilder().endDate(OffsetDateTime.now().plusDays(30)).build();
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(updatedContract));
    artemis.forContracts().sendAsText(updatedContract);
    return updatedContract;
  }

  private Contract whenSendContractViaMessageBroker(Contract contract) {
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(contract);

    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertThat("Sync offering should succeed", sync.statusCode(), is(HttpStatus.SC_OK));

    // Send the contract via Message Broker (Artemis)
    artemis.forContracts().sendAsText(contract);

    // Wait for the contract to be processed
    AwaitilityUtils.until(() -> service.getContracts(contract).size(), is(1));
    return contract;
  }

  private com.redhat.swatch.contract.test.model.Contract thenContractIsUpdatedViaMessageBroker(
      Contract initialContract) {
    return thenContractIsUpdatedWithMessageViaMessageBroker(
        initialContract, "Existing contracts and subscriptions updated");
  }

  private com.redhat.swatch.contract.test.model.Contract thenContractIsCreatedViaMessageBroker(
      Contract initialContract) {
    return thenContractIsUpdatedWithMessageViaMessageBroker(
        initialContract, "New contract created");
  }

  private com.redhat.swatch.contract.test.model.Contract
      thenContractIsUpdatedWithMessageViaMessageBroker(
          Contract initialContract, String expectedMessage) {
    service.logs().assertContains(expectedMessage);
    var contracts = service.getContracts(initialContract);
    Assertions.assertEquals(1, contracts.size());
    return contracts.get(0);
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
}
