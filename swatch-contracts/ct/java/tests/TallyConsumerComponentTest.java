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

import static api.MessageValidators.isUtilizationSummaryByTallySnapshots;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static com.redhat.swatch.component.tests.utils.Topics.UTILIZATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import api.ContractsTestHelper;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.contract.test.model.TallySnapshot;
import com.redhat.swatch.contract.test.model.UtilizationSummary;
import domain.BillingProvider;
import domain.Contract;
import domain.Subscription;
import domain.SubscriptionEvent;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TallyConsumerComponentTest extends BaseContractComponentTest {
  private static final double DEFAULT_CAPACITY = 10.0;
  private static final double TALLY_VALUE = 1.0;

  @BeforeAll
  static void subscribeToUtilizationTopic() {
    kafkaBridge.subscribeToTopic(UTILIZATION);
  }

  @TestPlanName("tally-consumer-TC001")
  @Test
  void shouldEnrichCapacityWhenTallySummaryMatchesContract() {
    // Given: A ROSA contract with capacity in the database
    var contract = givenRosaSubscription();
    var tallySnapshot = givenTallySnapshot(contract, CORES, TALLY_VALUE);

    // When: Tally summary is processed
    whenTallySummaryMessageIsSent(tallySnapshot);

    // Then: Capacity should be enriched and subscription found
    UtilizationSummary utilizationSummary = thenUtilizationMessageIsProduced(tallySnapshot);
    thenUtilizationSummaryMatches(utilizationSummary, contract, CORES);
  }

  @TestPlanName("tally-consumer-TC002")
  @Test
  void shouldEnrichCapacityWhenTallySummaryMatchesSubscription() {
    // Given: A RHEL subscription with capacity in the database
    var subscription = givenRhelSubscription();
    var tallySnapshot = givenTallySnapshot(subscription, SOCKETS, TALLY_VALUE);

    // When: Tally summary is processed
    whenTallySummaryMessageIsSent(tallySnapshot);

    // Then: Capacity should be enriched and subscription found
    UtilizationSummary utilizationSummary = thenUtilizationMessageIsProduced(tallySnapshot);
    thenUtilizationSummaryMatches(utilizationSummary, subscription, SOCKETS);
  }

  @TestPlanName("tally-consumer-TC003")
  @Test
  void shouldSetSubscriptionNotFoundWhenNoMatchingSubscription() {
    // Given: A tally snapshot with criteria that don't match any subscription
    var subscription = givenNonExistingSubscription();
    var tallySnapshot = givenTallySnapshot(subscription, CORES, TALLY_VALUE);

    // When: Tally summary is processed
    whenTallySummaryMessageIsSent(tallySnapshot);

    // Then: Utilization summary produced but subscription not found
    UtilizationSummary utilizationSummary = thenUtilizationMessageIsProduced(tallySnapshot);
    assertNotNull(utilizationSummary, "Utilization summary should be produced");
    assertFalse(utilizationSummary.getSubscriptionFound(), "Should not find matching subscription");
  }

  @TestPlanName("tally-consumer-TC004")
  @Test
  void shouldAggregateCapacityFromMultipleMatchingSubscriptions() {
    // Given: Multiple subscriptions with same SKU
    var subscriptions = givenMultipleSubscriptionsWithSameSku();
    var tallySnapshots = givenTallySnapshots(subscriptions, SOCKETS, TALLY_VALUE);

    // When: Tally summary is processed
    whenTallySummaryMessageIsSent(tallySnapshots);

    // Then: Capacity should be aggregated from all matching subscriptions
    UtilizationSummary utilizationSummary = thenUtilizationMessageIsProduced(tallySnapshots);
    assertNotNull(utilizationSummary, "Utilization summary should be produced");
    assertTrue(utilizationSummary.getSubscriptionFound(), "Should find matching subscriptions");
    assertNotNull(utilizationSummary.getMeasurements(), "Should have measurements");
    assertFalse(
        utilizationSummary.getMeasurements().isEmpty(), "Should have at least one measurement");
    var sockets =
        utilizationSummary.getMeasurements().stream()
            .filter(m -> SOCKETS.getValue().equals(m.getMetricId()))
            .findFirst();
    assertTrue(sockets.isPresent(), "Should have sockets measurement");
    double expectedCapacity =
        subscriptions.stream().mapToDouble(s -> s.getSubscriptionMeasurements().get(SOCKETS)).sum();
    assertEquals(expectedCapacity, sockets.get().getCapacity(), "Should have aggregated capacity");
  }

  @TestPlanName("tally-consumer-TC005")
  @Test
  void shouldProduceSeparateUtilizationSummariesForDifferentBillingAccounts() {
    // Given: Multiple subscriptions with different billing accounts
    var subscriptions = givenMultipleSubscriptionsWithDifferentBillingAccount();
    var tallySnapshots = givenTallySnapshots(subscriptions, CORES, TALLY_VALUE);

    // When: Tally summary is processed
    whenTallySummaryMessageIsSent(tallySnapshots);

    // Then: Separate utilization summaries produced (one per billing account)
    List<UtilizationSummary> utilizationSummaries =
        thenUtilizationMessagesAreProduced(tallySnapshots);
    assertEquals(subscriptions.size(), utilizationSummaries.size());
  }

  @TestPlanName("tally-consumer-TC006")
  @Test
  void shouldProduceTwoUtilizationSummariesForMixedContractAndSubscription() {
    // Given: One ROSA contract and one RHEL subscription
    var contract = givenRosaSubscription();
    var subscription = givenRhelSubscription();
    var tallySnapshotForContract = givenTallySnapshot(contract, CORES, TALLY_VALUE);
    var tallySnapshotForSubscription = givenTallySnapshot(subscription, SOCKETS, TALLY_VALUE);

    // When: Tally summary with both snapshots is processed
    whenTallySummaryMessageIsSent(tallySnapshotForContract, tallySnapshotForSubscription);

    // Then: Two utilization summaries produced, each with correct capacity
    var utilizationForContract = thenUtilizationMessageIsProduced(tallySnapshotForContract);
    thenUtilizationSummaryMatches(utilizationForContract, contract, CORES);

    var utilizationForSubscription = thenUtilizationMessageIsProduced(tallySnapshotForSubscription);
    thenUtilizationSummaryMatches(utilizationForSubscription, subscription, SOCKETS);
  }

  private List<Contract> givenMultipleSubscriptionsWithDifferentBillingAccount() {
    var subscriptions =
        List.of(
            Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY)),
            Contract.buildRosaContract(
                orgId, BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY)));
    subscriptions.forEach(this::givenContractIsCreated);
    return subscriptions;
  }

  private List<Subscription> givenMultipleSubscriptionsWithSameSku() {
    String sku = RandomUtils.generateRandom();
    var subscriptions =
        List.of(
            Subscription.buildRhelSubscriptionUsingSku(
                orgId, Map.of(SOCKETS, DEFAULT_CAPACITY), sku),
            Subscription.buildRhelSubscriptionUsingSku(
                orgId, Map.of(SOCKETS, DEFAULT_CAPACITY), sku));
    subscriptions.forEach(this::givenSubscriptionIsCreated);
    return subscriptions;
  }

  private Subscription givenNonExistingSubscription() {
    return Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));
  }

  private Contract givenRosaSubscription() {
    var contract =
        Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));
    givenContractIsCreated(contract);
    return contract;
  }

  private Subscription givenRhelSubscription() {
    var subscription = Subscription.buildRhelSubscription(orgId, Map.of(SOCKETS, DEFAULT_CAPACITY));
    givenSubscriptionIsCreated(subscription);
    return subscription;
  }

  private TallySnapshot[] givenTallySnapshots(
      List<? extends Subscription> subscriptions, MetricId metricId, double value) {
    return subscriptions.stream()
        .map(s -> givenTallySnapshot(s, metricId, value))
        .toArray(TallySnapshot[]::new);
  }

  private TallySnapshot givenTallySnapshot(
      Subscription subscription, MetricId metricId, double value) {
    return ContractsTestHelper.givenTallySnapshot(
        SubscriptionEvent.eventFor(subscription, metricId, value));
  }

  private void givenSubscriptionIsCreated(Subscription subscription) {
    wiremock.forProductAPI().stubOfferingData(subscription.getOffering());
    Response syncOfferingResponse = service.syncOffering(subscription.getOffering().getSku());
    assertEquals(
        HttpStatus.SC_OK,
        syncOfferingResponse.statusCode(),
        "Sync offering call should succeed");

    Response createSubscriptionResponse = service.saveSubscriptions(subscription);
    assertEquals(
        HttpStatus.SC_OK,
        createSubscriptionResponse.statusCode(),
        "Subscription creation should succeed");
  }

  private void whenTallySummaryMessageIsSent(TallySnapshot... snapshots) {
    var message = ContractsTestHelper.givenTallySummary(orgId, List.of(snapshots));
    kafkaBridge.produceKafkaMessage(TALLY, message);
  }

  private UtilizationSummary thenUtilizationMessageIsProduced(TallySnapshot... tallySnapshots) {
    return kafkaBridge.waitForKafkaMessage(
        UTILIZATION, isUtilizationSummaryByTallySnapshots(List.of(tallySnapshots)));
  }

  private List<UtilizationSummary> thenUtilizationMessagesAreProduced(
      TallySnapshot... tallySnapshots) {
    return kafkaBridge.waitForKafkaMessage(
        UTILIZATION,
        isUtilizationSummaryByTallySnapshots(List.of(tallySnapshots)),
        tallySnapshots.length);
  }

  private void thenUtilizationSummaryMatches(
      UtilizationSummary utilizationSummary, Subscription subscription, MetricId expectedMetric) {
    assertNotNull(utilizationSummary, "Utilization summary should be produced");
    assertEquals(subscription.getOrgId(), utilizationSummary.getOrgId());
    assertEquals(subscription.getProduct().getName(), utilizationSummary.getProductId());
    assertTrue(utilizationSummary.getSubscriptionFound(), "Should find matching subscription");
    assertNotNull(utilizationSummary.getMeasurements());
    assertEquals(1, utilizationSummary.getMeasurements().size());
    var measurement = utilizationSummary.getMeasurements().get(0);
    assertEquals(expectedMetric.getValue(), measurement.getMetricId());
    assertNotNull(
        measurement.getValue(), "Should have value set coming from the tally snapshot message");
    assertEquals(TALLY_VALUE, measurement.getValue(), 0.001);
    assertNotNull(measurement.getCapacity(), "Should have capacity set");
    assertEquals(DEFAULT_CAPACITY, measurement.getCapacity(), 0.001);
  }
}
