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

import static api.CanonicalMessageArtemisSender.SUBSCRIPTION_CHANNEL;
import static api.PartnerApiStubs.PartnerSubscriptionsStubRequest.forContract;
import static com.redhat.swatch.contract.product.umb.UmbSubscription.convertToUtc;
import static domain.Contract.buildRosaContract;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.DateUtils.assertDatesAreEqual;

import api.ContractsArtemisService;
import com.redhat.swatch.component.tests.api.Artemis;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.ContractResponse;
import domain.BillingProvider;
import domain.Contract;
import domain.Offering;
import domain.Subscription;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class SubscriptionsCreationComponentTest extends BaseContractComponentTest {

  private static final double RHEL_SOCKETS_CAPACITY = 1.0;

  @Artemis static ContractsArtemisService artemis = new ContractsArtemisService();

  private String sku;

  @BeforeEach
  void setUp() {
    super.setUp();
    this.sku = RandomUtils.generateRandom();
  }

  @TestPlanName("subscriptions-creation-TC001")
  @Test
  void shouldProcessValidSubscriptionMessage() {
    Subscription subscription = givenSubscription();

    artemis.forSubscriptions().send(subscription);

    var actual = thenSubscriptionIsCreated(subscription);
    assertEquals(subscription.getQuantity(), actual.getQuantity());
    assertEquals(subscription.getOffering().getSku(), actual.getSku());
    assertDatesAreEqual(
        convertToUtc(subscription.getStartDate().toLocalDateTime()), actual.getStartDate());
    assertDatesAreEqual(
        convertToUtc(subscription.getEndDate().toLocalDateTime()), actual.getEndDate());
  }

  @TestPlanName("subscriptions-creation-TC002")
  @Test
  void shouldProcessValidSubscriptionMessageWithAwsExternalReferences() {
    Subscription subscription = givenSubscriptionWithBillingProvider(BillingProvider.AWS);

    artemis.forSubscriptions().send(subscription);

    var actual = thenSubscriptionIsCreated(subscription);
    // Verify AWS external references
    assertEquals(BillingProvider.AWS.name(), actual.getBillingProvider());
    assertEquals(subscription.getBillingProviderId(), actual.getBillingProviderId());
    assertEquals(subscription.getBillingAccountId(), actual.getBillingAccountId());
  }

  /**
   * Disabled since this is not supported via UMB message. To be investigated as part of
   * SWATCH-4433.
   */
  @Disabled
  @TestPlanName("subscriptions-creation-TC003")
  @Test
  void shouldProcessValidSubscriptionMessageWithAzureExternalReferences() {
    Subscription subscription = givenSubscriptionWithBillingProvider(BillingProvider.AZURE);

    artemis.forSubscriptions().send(subscription);

    var actual = thenSubscriptionIsCreated(subscription);
    // Verify AWS external references
    assertEquals(BillingProvider.AZURE.name(), actual.getBillingProvider());
    assertEquals(subscription.getBillingAccountId(), actual.getBillingAccountId());
  }

  @TestPlanName("subscriptions-creation-TC004")
  @Test
  void shouldProcessMalformedMessage() {
    artemis.send(SUBSCRIPTION_CHANNEL, "<mal>for</med>", ContentType.XML.toString());

    service
        .logs()
        .assertContains("A message sent to channel `subscription-sync-umb` has been nacked");
  }

  @TestPlanName("subscriptions-creation-TC005")
  @Test
  void shouldProcessMessageWithMissingRequiredFields() {
    artemis.send(SUBSCRIPTION_CHANNEL, "<empty></empty>", ContentType.XML.toString());

    service
        .logs()
        .assertContains("A message sent to channel `subscription-sync-umb` has been nacked");
  }

  @TestPlanName("subscriptions-creation-TC006")
  @Test
  void shouldProcessSubscriptionUpdate() {
    Subscription subscription = givenSubscription();
    artemis.forSubscriptions().send(subscription);
    thenSubscriptionIsCreated(subscription);

    // when we update the subscription and send a new message
    subscription.toBuilder().quantity(3).build();
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(subscription);
    artemis.forSubscriptions().send(subscription);
    // then the subscription is updated
    AwaitilityUtils.untilAsserted(
        () -> {
          var subscriptions = service.getSubscriptionsByOrgId(orgId);
          assertEquals(1, subscriptions.size());
          assertEquals(
              subscription.getSubscriptionNumber(), subscriptions.get(0).getSubscriptionNumber());
          assertEquals(subscription.getQuantity(), subscriptions.get(0).getQuantity());
        });
  }

  @TestPlanName("subscriptions-creation-TC007")
  @Test
  void shouldProcessTerminatedSubscription() {
    // Given: An active subscription
    Subscription subscription = givenSubscription();
    artemis.forSubscriptions().send(subscription);
    var createdSubscription = thenSubscriptionIsCreated(subscription);

    assertNotNull(createdSubscription.getEndDate());
    OffsetDateTime originalEndDate = createdSubscription.getEndDate();
    assertTrue(
        originalEndDate.isAfter(OffsetDateTime.now()),
        "Subscription should initially have end date in the future");

    // When: Send a termination message
    // Note: The system interprets LocalDateTime in UMB messages as America/New_York time
    // and converts it to UTC, so we calculate the expected result using the same conversion
    OffsetDateTime terminationDate = OffsetDateTime.now();
    OffsetDateTime expectedEndDate = convertToUtc(terminationDate.toLocalDateTime());

    Subscription terminatedSubscription = subscription.toBuilder().endDate(terminationDate).build();
    artemis.forSubscriptions().sendTerminated(terminatedSubscription);

    // Then: Subscription should be marked as terminated with updated end date
    AwaitilityUtils.untilAsserted(
        () -> {
          var subscriptions = service.getSubscriptionsByOrgId(orgId);
          assertEquals(1, subscriptions.size(), "Should have exactly one subscription");

          var updatedSubscription = subscriptions.get(0);
          assertEquals(
              subscription.getSubscriptionNumber(),
              updatedSubscription.getSubscriptionNumber(),
              "Subscription number should match");

          assertNotNull(updatedSubscription.getEndDate(), "End date should be set");
          assertTrue(
              updatedSubscription.getEndDate().isBefore(originalEndDate),
              String.format(
                  "End date should be earlier than original. Original: %s, Updated: %s",
                  originalEndDate, updatedSubscription.getEndDate()));

          long diffSeconds =
              Math.abs(
                  updatedSubscription.getEndDate().toEpochSecond()
                      - expectedEndDate.toEpochSecond());
          assertTrue(
              diffSeconds < 10,
              String.format(
                  "End date should match expected UTC date (within 10s). "
                      + "Expected: %s, Actual: %s, Diff: %ds",
                  expectedEndDate, updatedSubscription.getEndDate(), diffSeconds));
        });
  }

  @TestPlanName("subscriptions-creation-TC009")
  @Test
  void shouldCreateSubscriptionWhenValidPaygContractIsReceived() {
    Contract contract = buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0), sku);
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertThat("Sync offering should succeed", sync.statusCode(), is(HttpStatus.SC_OK));

    // when create the valid PAYG contract
    Response response = service.createContract(contract);

    // assert the created contract
    assertThat("Creating contract should succeed", response.statusCode(), is(HttpStatus.SC_OK));
    var actual = response.then().extract().as(ContractResponse.class);
    assertNotNull(actual.getContract());
    var actualContract = actual.getContract();
    assertEquals(contract.getSubscriptionNumber(), actualContract.getSubscriptionNumber());
    assertEquals(contract.getOffering().getSku(), actualContract.getSku());
    assertDatesAreEqual(contract.getStartDate(), actualContract.getStartDate());
    assertDatesAreEqual(contract.getEndDate(), actualContract.getEndDate());
    assertEquals(contract.getOrgId(), actualContract.getOrgId());
    assertEquals(contract.getBillingProvider().toApiModel(), actualContract.getBillingProvider());
    // assert is in the subscription table as well
    var subscriptions = service.getSubscriptionsByOrgId(orgId);
    assertEquals(1, subscriptions.size(), "Should have exactly one subscription");
    assertSubscription(contract, subscriptions.get(0));
  }

  @TestPlanName("subscriptions-creation-TC010")
  @Test
  void shouldCreateAndSyncMultiplePaygSubscriptions() {
    // sync common offering for the multiple subscriptions
    var offering = Offering.buildRosaOffering(sku);
    wiremock.forProductAPI().stubOfferingData(offering);
    service.syncOffering(sku);

    // create the multiple subscriptions (which are really contracts)
    var firstSubscription = buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0), sku);
    var secondSubscription =
        buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 20.0), sku);
    var create = service.saveSubscriptions(firstSubscription, secondSubscription);
    assertThat("Subscription creation should succeed", create.statusCode(), is(HttpStatus.SC_OK));
    var sync = service.syncSubscriptionsForContractsByOrg(orgId);
    assertThat("Subscription sync should succeed", sync.statusCode(), is(HttpStatus.SC_OK));

    // assert is in the subscription table as well
    var subscriptions = service.getSubscriptionsByOrgId(orgId);
    assertEquals(2, subscriptions.size(), "Should have exactly two subscriptions");
    var actualFirstSubscription =
        subscriptions.stream()
            .filter(
                s ->
                    Objects.equals(
                        s.getSubscriptionNumber(), firstSubscription.getSubscriptionNumber()))
            .findFirst();
    assertTrue(actualFirstSubscription.isPresent());
    assertSubscription(firstSubscription, actualFirstSubscription.get());
    var actualSecondSubscription =
        subscriptions.stream()
            .filter(
                s ->
                    Objects.equals(
                        s.getSubscriptionNumber(), secondSubscription.getSubscriptionNumber()))
            .findFirst();
    assertTrue(actualSecondSubscription.isPresent());
    assertSubscription(secondSubscription, actualSecondSubscription.get());
  }

  private Subscription givenSubscription() {
    var subscription =
        Subscription.buildRhelSubscription(orgId, Map.of(SOCKETS, RHEL_SOCKETS_CAPACITY), sku);
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(subscription);
    wiremock.forProductAPI().stubOfferingData(subscription.getOffering());
    return subscription;
  }

  private Subscription givenSubscriptionWithBillingProvider(BillingProvider billingProvider) {
    var subscription =
        Subscription.buildRhelSubscription(orgId, Map.of(SOCKETS, RHEL_SOCKETS_CAPACITY), sku)
            .toBuilder()
            .billingProvider(billingProvider)
            // generate the billing provider Id in the form of a;b;c
            .billingProviderId(
                IntStream.range(0, 3)
                    .mapToObj(i -> RandomUtils.generateRandom())
                    .collect(Collectors.joining(";")))
            .billingAccountId("billing-" + RandomUtils.generateRandom())
            .build();
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(subscription);
    wiremock.forProductAPI().stubOfferingData(subscription.getOffering());
    return subscription;
  }

  private com.redhat.swatch.contract.test.model.Subscription thenSubscriptionIsCreated(
      Subscription expected) {
    return AwaitilityUtils.until(
            () -> service.getSubscriptionsByOrgId(orgId),
            actual ->
                actual != null
                    && actual.size() == 1
                    && Objects.equals(
                        actual.get(0).getSubscriptionNumber(), expected.getSubscriptionNumber()))
        .get(0);
  }

  private void assertSubscription(
      Contract expected, com.redhat.swatch.contract.test.model.Subscription actual) {
    assertEquals(expected.getSubscriptionNumber(), actual.getSubscriptionNumber());
    assertEquals(expected.getOffering().getSku(), actual.getSku());
    assertDatesAreEqual(expected.getStartDate(), actual.getStartDate());
    assertDatesAreEqual(expected.getEndDate(), actual.getEndDate());
    assertEquals(expected.getOrgId(), actual.getOrgId());
    assertEquals(expected.getBillingProvider().toString(), actual.getBillingProvider());
  }
}
