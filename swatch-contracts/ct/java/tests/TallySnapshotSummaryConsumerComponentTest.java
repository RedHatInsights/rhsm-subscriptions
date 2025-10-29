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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import api.ContractsTestHelper;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TallySnapshotSummaryConsumerComponentTest extends BaseContractComponentTest {
  private static final double DEFAULT_CAPACITY = 10.0;
  private static final double TALLY_VALUE = 1.0;

  @BeforeAll
  static void subscribeToUtilizationTopic() {
    kafkaBridge.subscribeToTopic(UTILIZATION);
  }

  @Test
  void whenTallySummaryWithMatchingContractThenCapacityIsEnriched() {
    // Given contract with capacity in database
    var contract = givenRosaSubscription();

    // Given tally snapshot
    var tallySnapshot = givenTallySnapshot(contract, CORES, TALLY_VALUE);

    // When tally summary is processed
    whenTallySummaryMessageIsSent(tallySnapshot);

    // Then capacity should be enriched and subscription found
    UtilizationSummary utilizationSummary = thenUtilizationMessageIsProduced(tallySnapshot);
    thenUtilizationSummaryMatches(utilizationSummary, contract, CORES);
  }

  @Test
  void whenTallySummaryWithMatchingSubscriptionThenCapacityIsEnriched() {
    // Given subscription with capacity in database
    var subscription = givenRhelSubscription();

    // Given tally snapshot
    var tallySnapshot = givenTallySnapshot(subscription, SOCKETS, TALLY_VALUE);

    // When tally summary is processed
    whenTallySummaryMessageIsSent(tallySnapshot);

    // Then capacity should be enriched and subscription found
    UtilizationSummary utilizationSummary = thenUtilizationMessageIsProduced(tallySnapshot);
    thenUtilizationSummaryMatches(utilizationSummary, subscription, SOCKETS);
  }

  @Test
  void whenTallySummaryWithNoMatchingSubscriptionThenSubscriptionNotFound() {
    // Given tally snapshot with criteria that don't match any subscription
    var subscription = givenNonExistingSubscription();
    var tallySnapshot = givenTallySnapshot(subscription, CORES, TALLY_VALUE);

    // When tally summary is processed
    whenTallySummaryMessageIsSent(tallySnapshot);

    // Then should process but subscription not found
    UtilizationSummary utilizationSummary = thenUtilizationMessageIsProduced(tallySnapshot);
    assertNotNull(utilizationSummary, "Utilization summary should be produced");
    assertFalse(utilizationSummary.getSubscriptionFound(), "Should not find matching subscription");
  }

  @Test
  void whenTallySummaryWithMultipleMatchingSubscriptionsThenCapacityIsAggregated() {
    // Given multiple subscriptions with same SKU
    var subscriptions = givenMultipleSubscriptionsWithSameSku();

    // Given tally snapshot
    var tallySnapshots = givenTallySnapshots(subscriptions, SOCKETS, TALLY_VALUE);

    // When tally summary is processed
    whenTallySummaryMessageIsSent(tallySnapshots);

    // Then capacity should be aggregated
    UtilizationSummary utilizationSummary = thenUtilizationMessageIsProduced(tallySnapshots);
    assertNotNull(utilizationSummary, "Utilization summary should be produced");
    assertTrue(utilizationSummary.getSubscriptionFound(), "Should find matching subscriptions");
    assertNotNull(utilizationSummary.getMeasurements(), "Should have measurements");
    assertFalse(
        utilizationSummary.getMeasurements().isEmpty(), "Should have at least one measurement");
    // Verify that capacity is aggregated from multiple subscriptions
    var sockets =
        utilizationSummary.getMeasurements().stream()
            .filter(m -> SOCKETS.getValue().equals(m.getMetricId()))
            .findFirst();
    assertTrue(sockets.isPresent(), "Should have sockets measurement");
    double expectedCapacity =
        subscriptions.stream().mapToDouble(s -> s.getSubscriptionMeasurements().get(SOCKETS)).sum();
    assertEquals(expectedCapacity, sockets.get().getCapacity(), "Should have aggregated capacity");
  }

  @Test
  void whenTallySummaryWithMultipleDifferentSubscriptionsThenCapacityIsNotAggregated() {
    // Given multiple subscriptions with other billing account
    var subscriptions = givenMultipleSubscriptionsWithDifferentBillingAccount();

    // Given tally snapshot
    var tallySnapshots = givenTallySnapshots(subscriptions, CORES, TALLY_VALUE);

    // When tally summary is processed
    whenTallySummaryMessageIsSent(tallySnapshots);

    // Then capacity should NOT be aggregated
    List<UtilizationSummary> utilizationSummaries =
        thenUtilizationMessagesAreProduced(tallySnapshots);
    assertEquals(subscriptions.size(), utilizationSummaries.size());
  }

  @Test
  void whenTallySummaryWithMixedContractAndSubscriptionThenTwoUtilizationSummariesAreProduced() {
    // Given one rosa contract and rhel subscription
    var contract = givenRosaSubscription();
    var subscription = givenRhelSubscription();
    var tallySnapshotForContract = givenTallySnapshot(contract, CORES, TALLY_VALUE);
    var tallySnapshotForSubscription = givenTallySnapshot(subscription, SOCKETS, TALLY_VALUE);

    // When tally summary is processed individually
    whenTallySummaryMessageIsSent(tallySnapshotForContract, tallySnapshotForSubscription);

    // Then capacity should be aggregated
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

  private Subscription givenNonExistingSubscription() {
    // New contract, not created using service.createContract().
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

  private void givenSubscriptionIsCreated(Subscription subscription) {
    wiremock.forProductAPI().stubOfferingData(subscription.getOffering());
    Response syncOfferingResponse = service.syncOffering(subscription.getOffering().getSku());
    assertThat("Sync offering call should succeed", syncOfferingResponse.statusCode(), is(200));

    Response createSubscriptionResponse = service.saveSubscriptions(subscription);
    assertThat(
        "Subscription creation should succeed", createSubscriptionResponse.statusCode(), is(200));
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
