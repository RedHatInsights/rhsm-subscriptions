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
import static com.redhat.swatch.contract.product.umb.UmbSubscription.convertToUtc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import api.ContractsArtemisService;
import com.redhat.swatch.component.tests.api.Artemis;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import domain.BillingProvider;
import domain.Subscription;
import io.restassured.http.ContentType;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    assertDates(subscription.getStartDate(), actual.getStartDate());
    assertDates(subscription.getEndDate(), actual.getEndDate());
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

  /**
   * Assert that two OffsetDateTime values are within 1 second of each other. This is necessary
   * because timestamp conversions may lose precision in nanoseconds.
   */
  private static void assertDates(OffsetDateTime expected, OffsetDateTime actual) {
    assertNotNull(actual, "Actual date should not be null");
    assertNotNull(expected, "Expected date should not be null");
    long expectedInUtc = convertToUtc(expected.toLocalDateTime()).toEpochSecond();
    long diffSeconds = Math.abs(expectedInUtc - actual.toEpochSecond());
    assertTrue(
        diffSeconds <= 1,
        String.format(
            "Dates should be within 1 second. Expected: %s, Actual: %s, Difference: %ds",
            expected, actual, diffSeconds));
  }
}
