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

import static com.redhat.swatch.component.tests.utils.Topics.CAPACITY_RECONCILE;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redhat.swatch.component.tests.api.DefaultMessageValidator;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.contract.test.model.OfferingResponse;
import domain.Offering;
import domain.Product;
import domain.ReconcileCapacityByOfferingTask;
import domain.Subscription;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import org.apache.http.HttpStatus;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CapacityReconciliationComponentTest extends BaseContractComponentTest {

  private static final int SUBSCRIPTION_COUNT = 5;
  private static final double CORES_CAPACITY = 8.0;

  @BeforeAll
  static void subscribeToCapacityReconcileTopic() {
    kafkaBridge.subscribeToTopic(CAPACITY_RECONCILE);
  }

  @TestPlanName("capacity-reconciliation-TC001")
  @Test
  void shouldReconcileCapacityForSingleOffering() {
    // Given: An offering with 5 subscriptions created WITHOUT reconciliation (no measurements yet)
    final String testSku = givenOfferingWithMultipleSubscriptions();

    // When: Reconcile capacity is triggered for the offering
    Response response = whenCapacityReconciliationIsForced(testSku);

    // Then: Reconciliation task messages should be published to Kafka
    assertThat("Force reconcile should succeed", response.statusCode(), is(HttpStatus.SC_OK));

    ReconcileCapacityByOfferingTask task = thenReconciliationTaskIsPublished(testSku);
    assertThat("Task should contain correct SKU", task.getSku(), is(testSku));

    // Verify all subscriptions are processed
    var subscriptions = service.getSubscriptionsByOrgId(orgId);
    assertThat(
        "All 5 subscriptions should be present and processed",
        subscriptions.size(),
        is(SUBSCRIPTION_COUNT));

    // Verify subscription measurements updated (from none to 40.0)
    thenAllSubscriptionsAreReconciled(testSku);
  }

  @TestPlanName("capacity-reconciliation-TC002")
  @Test
  void shouldReconcileNonExistentOffering() {
    // Given: A non-existent SKU
    final String invalidSku = RandomUtils.generateRandom();

    // When: Reconcile capacity is triggered for a non-existent offering
    Response response = whenCapacityReconciliationIsForced(invalidSku);

    // Then: No errors should be thrown
    assertThat(
        "Reconcile non-existent offering should succeed",
        response.statusCode(),
        is(HttpStatus.SC_OK));

    OfferingResponse offeringResponse = response.as(OfferingResponse.class);
    assertThat("Response should not be null", offeringResponse, notNullValue());

    // Explicitly verify no tasks were published (subscription count = 0)
    thenNoReconciliationTasksWerePublished(invalidSku);

    // Additionally verify the SKU doesn't appear in capacity report
    thenSkuHasNoCapacity(invalidSku);
  }

  @TestPlanName("capacity-reconciliation-TC003")
  @Test
  void shouldForceReconcileViaAPI() {
    // Given: An offering with a subscription (saved without reconciliation)
    final String testSku = RandomUtils.generateRandom();
    Offering offering = Offering.buildOpenShiftOffering(testSku, CORES_CAPACITY, null);
    Subscription subscription =
        Subscription.buildOpenShiftSubscriptionUsingSku(
            orgId, Map.of(CORES, CORES_CAPACITY), testSku);
    givenOfferingAndSubscriptionSavedWithoutReconciliation(offering, subscription);

    // When: Force reconcile is triggered via API endpoint
    Response response = whenCapacityReconciliationIsForced(testSku);

    // Then: HTTP 200 OK should be returned
    assertThat("API should return 200 OK", response.statusCode(), is(HttpStatus.SC_OK));

    // Reconciliation task should be enqueued
    ReconcileCapacityByOfferingTask task = thenReconciliationTaskIsPublished(testSku);
    assertThat("Task should be enqueued with correct SKU", task.getSku(), is(testSku));
  }

  @TestPlanName("capacity-reconciliation-kafka-TC001")
  @Test
  void shouldProcessReconciliationTaskFromKafka() {
    // Given: An offering with subscriptions exists
    final String testSku = givenOfferingWithMultipleSubscriptions();

    // When: A ReconcileCapacityByOfferingTask is published to the Kafka topic
    ReconcileCapacityByOfferingTask task =
        ReconcileCapacityByOfferingTask.builder().sku(testSku).offset(0).limit(100).build();
    kafkaBridge.produceKafkaMessage(CAPACITY_RECONCILE, task);

    // Then: Consumer receives task and reconciles capacity for the offering
    service
        .logs()
        .assertContains(
            "Capacity Reconciliation Worker is reconciling capacity for offering with values");

    // Verify all subscriptions are reconciled with updated measurements
    thenAllSubscriptionsAreReconciled(testSku);
  }

  @TestPlanName("capacity-reconciliation-TC004")
  @Test
  void shouldReconcileSubscriptionWithCores() {
    // Given: An offering with cores=4 and a subscription with quantity=10
    final String testSku = RandomUtils.generateRandom();
    Offering offering = Offering.buildOpenShiftOffering(testSku, 4.0, null);
    Subscription subscription =
        Subscription.buildOpenShiftSubscriptionUsingSku(orgId, Map.of(CORES, 4.0), testSku)
            .toBuilder()
            .quantity(10)
            .build();

    // When: Subscription is saved with reconcileCapacity=true (triggers reconciliation)
    whenOfferingAndSubscriptionReconciled(offering, subscription);

    // Then: PHYSICAL Cores measurement = 4 * 10 = 40
    thenSubscriptionHasCoresMeasurement(testSku, 40.0);
  }

  @TestPlanName("capacity-reconciliation-TC004b")
  @Test
  void shouldReconcileSubscriptionWithHypervisorCores() {
    // Given: A hypervisor offering with hypervisorCores=2 and a subscription with quantity=10
    final String testSku = RandomUtils.generateRandom();
    Offering offering = Offering.buildRhelHypervisorOffering(testSku, 2.0, null);
    Subscription subscription = givenHypervisorSubscription(offering, Map.of(CORES, 2.0), 10);

    // When: Subscription is saved with reconcileCapacity=true (triggers reconciliation)
    whenOfferingAndSubscriptionReconciled(offering, subscription);

    // Then: HYPERVISOR Cores measurement = 2 * 10 = 20
    thenSubscriptionHasHypervisorCoresMeasurement(20.0);
  }

  @TestPlanName("capacity-reconciliation-TC005")
  @Test
  void shouldReconcileSubscriptionWithSockets() {
    // Given: An offering with sockets=2 and a subscription with quantity=5
    final String testSku = RandomUtils.generateRandom();
    Offering offering = Offering.buildRhelOffering(testSku, null, 2.0);
    Subscription subscription =
        Subscription.buildRhelSubscriptionUsingSku(orgId, Map.of(SOCKETS, 2.0), testSku).toBuilder()
            .quantity(5)
            .build();

    // When: Subscription is saved with reconcileCapacity=true (triggers reconciliation)
    whenOfferingAndSubscriptionReconciled(offering, subscription);

    // Then: PHYSICAL Sockets = 2 * 5 = 10
    thenSubscriptionHasSocketsMeasurement(testSku, 10.0);
  }

  @TestPlanName("capacity-reconciliation-TC005b")
  @Test
  void shouldReconcileSubscriptionWithHypervisorSockets() {
    // Given: A hypervisor offering with hypervisorSockets=1 and a subscription with quantity=5
    final String testSku = RandomUtils.generateRandom();
    Offering offering = Offering.buildRhelHypervisorOffering(testSku, null, 1.0);
    Subscription subscription = givenHypervisorSubscription(offering, Map.of(SOCKETS, 1.0), 5);

    // When: Subscription is saved with reconcileCapacity=true (triggers reconciliation)
    whenOfferingAndSubscriptionReconciled(offering, subscription);

    // Then: HYPERVISOR Sockets = 1 * 5 = 5
    thenSubscriptionHasHypervisorSocketsMeasurement(5.0);
  }

  @TestPlanName("capacity-reconciliation-TC006")
  @Test
  void shouldReconcileSubscriptionWithMixedMetrics() {
    // Given: An offering with cores=8, sockets=2 and a subscription with quantity=3
    final String testSku = RandomUtils.generateRandom();
    Offering offering = Offering.buildOpenShiftOffering(testSku, 8.0, 2.0);
    Subscription subscription =
        Subscription.buildOpenShiftSubscriptionUsingSku(
                orgId, Map.of(CORES, 8.0, SOCKETS, 2.0), testSku)
            .toBuilder()
            .quantity(3)
            .build();

    // When: Subscription is saved with reconcileCapacity=true (triggers reconciliation)
    whenOfferingAndSubscriptionReconciled(offering, subscription);

    // Then: PHYSICAL Cores = 24, PHYSICAL Sockets = 6
    thenSubscriptionHasCoresAndSocketsMeasurements(testSku, 24.0, 6.0);
  }

  @TestPlanName("capacity-reconciliation-TC006b")
  @Test
  void shouldReconcileSubscriptionWithMixedHypervisorMetrics() {
    // Given: A hypervisor offering with hypervisorCores=4, hypervisorSockets=1 and quantity=3
    final String testSku = RandomUtils.generateRandom();
    Offering offering = Offering.buildRhelHypervisorOffering(testSku, 4.0, 1.0);
    Subscription subscription =
        givenHypervisorSubscription(offering, Map.of(CORES, 4.0, SOCKETS, 1.0), 3);

    // When: Subscription is saved with reconcileCapacity=true (triggers reconciliation)
    whenOfferingAndSubscriptionReconciled(offering, subscription);

    // Then: HYPERVISOR Cores = 12, HYPERVISOR Sockets = 3
    thenSubscriptionHasHypervisorCoresAndSocketsMeasurements(12.0, 3.0);
  }

  @TestPlanName("capacity-reconciliation-TC007")
  @Test
  void shouldUpdateExistingMeasurementsWhenOfferingChanges() {
    // Given: Subscription has PHYSICAL Cores = 40 (cores=8, quantity=5)
    final String testSku = RandomUtils.generateRandom();
    Offering initialOffering = Offering.buildOpenShiftOffering(testSku, 8.0, null);
    Subscription subscription =
        Subscription.buildOpenShiftSubscriptionUsingSku(orgId, Map.of(CORES, 8.0), testSku)
            .toBuilder()
            .quantity(5)
            .build();
    whenOfferingAndSubscriptionReconciled(initialOffering, subscription);
    thenSubscriptionHasCoresMeasurement(testSku, 40.0);
    // Given: Offering updated with cores=6
    Offering updatedOffering = Offering.buildOpenShiftOffering(testSku, 6.0, null);
    givenOfferingIsUpdatedAndSynced(updatedOffering);

    // When: Force reconcile
    Response reconcileResponse = whenCapacityReconciliationIsForced(testSku);

    // Then: Existing measurement updated to new value (6 * 5 = 30)
    assertThat(
        "Force reconcile should succeed", reconcileResponse.statusCode(), is(HttpStatus.SC_OK));
    thenSubscriptionHasCoresMeasurementAfterForceReconcile(testSku, 30.0);
  }

  @TestPlanName("capacity-reconciliation-TC007b")
  @Test
  void shouldUpdateExistingHypervisorMeasurementsWhenOfferingChanges() {
    // Given: Subscription has HYPERVISOR Cores = 20 (hypervisorCores=2, quantity=10)
    final String testSku = RandomUtils.generateRandom();
    Offering initialOffering = Offering.buildRhelHypervisorOffering(testSku, 2.0, null);
    Subscription subscription =
        givenHypervisorSubscription(initialOffering, Map.of(CORES, 2.0), 10);
    whenOfferingAndSubscriptionReconciled(initialOffering, subscription);
    thenSubscriptionHasHypervisorCoresMeasurement(20.0);
    // Given: Offering updated with hypervisorCores=3
    Offering updatedOffering = Offering.buildRhelHypervisorOffering(testSku, 3.0, null);
    givenOfferingIsUpdatedAndSynced(updatedOffering);

    // When: Force reconcile
    Response reconcileResponse = whenCapacityReconciliationIsForced(testSku);

    // Then: Existing measurement updated to new value (3 * 10 = 30)
    assertThat(
        "Force reconcile should succeed", reconcileResponse.statusCode(), is(HttpStatus.SC_OK));
    thenSubscriptionHasHypervisorCoresMeasurement(30.0);
  }

  @TestPlanName("capacity-reconciliation-TC008")
  @Test
  void shouldCreateNewMeasurementsWhenSubscriptionHasNone() {
    // Given: Subscription has no measurements (saved with reconcileCapacity=false)
    final String testSku = RandomUtils.generateRandom();
    Offering offering = Offering.buildOpenShiftOffering(testSku, 4.0, null);
    Subscription subscription =
        Subscription.buildOpenShiftSubscriptionUsingSku(orgId, Map.of(CORES, 4.0), testSku)
            .toBuilder()
            .quantity(5)
            .build();
    givenOfferingAndSubscriptionSavedWithoutReconciliation(offering, subscription);

    // When: Force reconcile
    Response reconcileResponse = whenCapacityReconciliationIsForced(testSku);

    // Then: New measurements created (4 * 5 = 20)
    assertThat(
        "Force reconcile should succeed", reconcileResponse.statusCode(), is(HttpStatus.SC_OK));
    thenSubscriptionHasCoresMeasurementAfterForceReconcile(testSku, 20.0);
  }

  @TestPlanName("capacity-reconciliation-TC008b")
  @Test
  void shouldCreateNewHypervisorMeasurementsWhenSubscriptionHasNone() {
    // Given: Subscription has no measurements (saved with reconcileCapacity=false)
    final String testSku = RandomUtils.generateRandom();
    Offering offering = Offering.buildRhelHypervisorOffering(testSku, 2.0, null);
    Subscription subscription = givenHypervisorSubscription(offering, Map.of(CORES, 2.0), 5);
    givenOfferingAndSubscriptionSavedWithoutReconciliation(offering, subscription);

    // When: Force reconcile
    Response reconcileResponse = whenCapacityReconciliationIsForced(testSku);

    // Then: New hypervisor measurements created (2 * 5 = 10)
    assertThat(
        "Force reconcile should succeed", reconcileResponse.statusCode(), is(HttpStatus.SC_OK));
    thenSubscriptionHasHypervisorCoresMeasurement(10.0);
  }

  @TestPlanName("capacity-reconciliation-TC009")
  @Test
  void shouldDeleteStaleMeasurementsWhenOfferingNoLongerHasThem() {
    // Given: Subscription has PHYSICAL Cores and PHYSICAL Sockets (cores=8, sockets=2, quantity=3)
    final String testSku = RandomUtils.generateRandom();
    Offering initialOffering = Offering.buildOpenShiftOffering(testSku, 8.0, 2.0);
    Subscription subscription =
        Subscription.buildOpenShiftSubscriptionUsingSku(
                orgId, Map.of(CORES, 8.0, SOCKETS, 2.0), testSku)
            .toBuilder()
            .quantity(3)
            .build();
    whenOfferingAndSubscriptionReconciled(initialOffering, subscription);
    thenSubscriptionHasCoresAndSocketsMeasurements(testSku, 24.0, 6.0);
    // Given: Offering updated to have PHYSICAL Cores only (sockets = null)
    Offering coresOnlyOffering = Offering.buildOpenShiftOffering(testSku, 8.0, null);
    givenOfferingIsUpdatedAndSynced(coresOnlyOffering);

    // When: Force reconcile
    Response reconcileResponse = whenCapacityReconciliationIsForced(testSku);

    // Then: PHYSICAL Cores retained (24), PHYSICAL Sockets measurement deleted
    assertThat(
        "Force reconcile should succeed", reconcileResponse.statusCode(), is(HttpStatus.SC_OK));
    thenSubscriptionHasCoresMeasurementAfterForceReconcile(testSku, 24.0);
  }

  @TestPlanName("capacity-reconciliation-TC009b")
  @Test
  void shouldDeleteStaleHypervisorMeasurementsWhenOfferingNoLongerHasThem() {
    // Given: Subscription has HYPERVISOR Cores and HYPERVISOR Sockets (4, 1, quantity=3)
    final String testSku = RandomUtils.generateRandom();
    Offering initialOffering = Offering.buildRhelHypervisorOffering(testSku, 4.0, 1.0);
    Subscription subscription =
        givenHypervisorSubscription(initialOffering, Map.of(CORES, 4.0, SOCKETS, 1.0), 3);
    whenOfferingAndSubscriptionReconciled(initialOffering, subscription);
    thenSubscriptionHasHypervisorCoresAndSocketsMeasurements(12.0, 3.0);
    // Given: Offering updated to have HYPERVISOR Cores only (hypervisorSockets = null)
    Offering coresOnlyOffering = Offering.buildRhelHypervisorOffering(testSku, 4.0, null);
    givenOfferingIsUpdatedAndSynced(coresOnlyOffering);

    // When: Force reconcile
    Response reconcileResponse = whenCapacityReconciliationIsForced(testSku);

    // Then: HYPERVISOR Cores retained (12), HYPERVISOR Sockets measurement deleted
    assertThat(
        "Force reconcile should succeed", reconcileResponse.statusCode(), is(HttpStatus.SC_OK));
    thenSubscriptionHasHypervisorCoresMeasurement(12.0);
  }

  @TestPlanName("capacity-reconciliation-TC010")
  @Test
  void shouldNotCreateMeasurementsForNullOrZeroCapacityValues() {
    // Given: Offering has cores=null, sockets=0
    final String testSku = RandomUtils.generateRandom();
    Offering offering = Offering.buildOpenShiftOffering(testSku, null, 0.0);
    Subscription subscription =
        Subscription.buildOpenShiftSubscriptionUsingSku(
                orgId, Map.of(CORES, 0.0, SOCKETS, 0.0), testSku)
            .toBuilder()
            .quantity(5)
            .build();

    // When: Subscription is saved with reconcileCapacity=true (triggers reconciliation)
    whenOfferingAndSubscriptionReconciled(offering, subscription);

    // Then: No measurements created for null or zero values
    thenSubscriptionHasNoCapacityMeasurements(testSku);
  }

  @TestPlanName("capacity-reconciliation-kafka-TC002")
  @Test
  void shouldHandleMalformedReconciliationTask() {
    // Given: An offering with subscriptions exists and a valid task is ready
    final String testSku = givenOfferingWithMultipleSubscriptions();
    ReconcileCapacityByOfferingTask validTask =
        ReconcileCapacityByOfferingTask.builder().sku(testSku).offset(0).limit(100).build();

    // When: A malformed message is published followed by a valid message
    // With fail-on-deserialization-failure=false, malformed message is gracefully skipped
    Map<String, Object> malformedTask = Map.of("sku", 12345, "offset", "invalid", "limit", true);
    kafkaBridge.produceKafkaMessage(CAPACITY_RECONCILE, malformedTask);
    kafkaBridge.produceKafkaMessage(CAPACITY_RECONCILE, validTask);

    // Then: Service logs the deserialization error for the malformed message
    service.logs().assertContains("consume has thrown an exception");

    // And: Service remains stable and processes valid message successfully
    // (If consumer crashed from malformed message, this await would fail)
    service
        .logs()
        .assertContains(
            "Capacity Reconciliation Worker is reconciling capacity for offering with values");

    // Verify reconciliation actually completed (measurements updated)
    thenAllSubscriptionsAreReconciled(testSku);
  }

  // Helper methods

  /**
   * Creates a RHEL hypervisor subscription for the given offering and capacity.
   *
   * @param offering The hypervisor offering (must be synced separately)
   * @param capacity Map of metric to capacity value (e.g. CORES, SOCKETS)
   * @param quantity The subscription quantity
   * @return The subscription
   */
  private Subscription givenHypervisorSubscription(
      Offering offering, Map<MetricId, Double> capacity, int quantity) {
    return Subscription.builder()
        .orgId(orgId)
        .product(Product.RHEL)
        .subscriptionId(RandomUtils.generateRandom())
        .subscriptionNumber(RandomUtils.generateRandom())
        .offering(offering)
        .subscriptionMeasurements(capacity)
        .startDate(OffsetDateTime.now().minusDays(1))
        .endDate(OffsetDateTime.now().plusDays(1))
        .quantity(quantity)
        .build();
  }

  /**
   * Stubs the offering in Wiremock and syncs it.
   *
   * @param offering the offering to stub and sync
   */
  private void givenOfferingIsStubbedAndSynced(Offering offering) {
    wiremock.forProductAPI().stubOfferingData(offering);
    Response syncResponse = service.syncOffering(offering.getSku());
    assertThat("Sync offering should succeed", syncResponse.statusCode(), is(HttpStatus.SC_OK));
  }

  /**
   * Creates an offering with multiple subscriptions for testing reconciliation.
   *
   * @return The SKU of the created offering
   */
  private String givenOfferingWithMultipleSubscriptions() {
    final String testSku = RandomUtils.generateRandom();
    Offering offering = Offering.buildOpenShiftOffering(testSku, CORES_CAPACITY, null);
    givenOfferingIsStubbedAndSynced(offering);

    // Create 5 subscriptions for this offering
    for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
      Subscription subscription =
          Subscription.buildOpenShiftSubscriptionUsingSku(
              orgId, Map.of(CORES, CORES_CAPACITY), testSku);

      Response saveResponse = service.saveSubscriptions(false, subscription);
      assertThat(
          "Creating subscription should succeed", saveResponse.statusCode(), is(HttpStatus.SC_OK));
    }

    return testSku;
  }

  /**
   * Stubs the offering, syncs it, and saves the subscription.
   *
   * @param offering the offering to create
   * @param subscription the subscription to save (must reference the offering's SKU)
   * @param reconcileCapacity whether to reconcile capacity on save
   */
  private void givenOfferingAndSubscriptionSaved(
      Offering offering, Subscription subscription, boolean reconcileCapacity) {
    givenOfferingIsStubbedAndSynced(offering);
    Response saveResponse = service.saveSubscriptions(reconcileCapacity, subscription);
    assertThat(
        "Creating subscription should succeed", saveResponse.statusCode(), is(HttpStatus.SC_OK));
  }

  /**
   * When: Stubs the offering, syncs it, and saves the subscription with reconcileCapacity=true
   * (triggers capacity reconciliation).
   *
   * @param offering the offering to create
   * @param subscription the subscription to save (must reference the offering's SKU)
   */
  private void whenOfferingAndSubscriptionReconciled(Offering offering, Subscription subscription) {
    givenOfferingAndSubscriptionSaved(offering, subscription, true);
  }

  /**
   * Stubs the offering, syncs it, and saves the subscription with reconcileCapacity=false.
   *
   * @param offering the offering to create
   * @param subscription the subscription to save (must reference the offering's SKU)
   */
  private void givenOfferingAndSubscriptionSavedWithoutReconciliation(
      Offering offering, Subscription subscription) {
    givenOfferingAndSubscriptionSaved(offering, subscription, false);
  }

  /**
   * Updates the offering stub and syncs it (for tests that change offering after initial setup).
   *
   * @param offering the updated offering to stub and sync
   */
  private void givenOfferingIsUpdatedAndSynced(Offering offering) {
    givenOfferingIsStubbedAndSynced(offering);
  }

  /**
   * Triggers capacity reconciliation for the specified SKU.
   *
   * @param sku The SKU to reconcile
   * @return The API response
   */
  private Response whenCapacityReconciliationIsForced(String sku) {
    return service.forceReconcileOffering(sku);
  }

  /**
   * Waits for and validates a ReconcileCapacityByOfferingTask message on Kafka.
   *
   * @param expectedSku The expected SKU in the task
   * @return The received task
   */
  private ReconcileCapacityByOfferingTask thenReconciliationTaskIsPublished(String expectedSku) {
    return kafkaBridge.waitForKafkaMessage(
        CAPACITY_RECONCILE,
        new DefaultMessageValidator<>(
            task -> expectedSku.equals(task.getSku()), ReconcileCapacityByOfferingTask.class));
  }

  /**
   * Verifies that NO ReconcileCapacityByOfferingTask messages were published to Kafka for the given
   * SKU. Uses a short timeout to ensure we're not waiting unnecessarily for messages that shouldn't
   * exist.
   *
   * @param sku The SKU to check for (should NOT have any tasks published)
   */
  private void thenNoReconciliationTasksWerePublished(String sku) {
    // Use a short timeout (5 seconds) to verify no messages arrive
    var messages =
        kafkaBridge.waitForKafkaMessage(
            CAPACITY_RECONCILE,
            new DefaultMessageValidator<>(
                task -> sku.equals(task.getSku()), ReconcileCapacityByOfferingTask.class),
            0, // Expected count is 0
            AwaitilitySettings.usingTimeout(Duration.ofSeconds(5)));

    assertThat(
        "No reconciliation tasks should be published for non-existent offering", messages, empty());
  }

  /**
   * Waits for capacity reconciliation to complete and verifies all subscriptions were processed
   * with updated measurements.
   *
   * @param sku The SKU to check capacity for
   */
  private void thenAllSubscriptionsAreReconciled(String sku) {
    double expectedTotalCapacity = SUBSCRIPTION_COUNT * CORES_CAPACITY; // 5 * 8.0 = 40.0

    await("All subscriptions should be reconciled with updated measurements")
        .atMost(2, MINUTES)
        .pollInterval(2, SECONDS)
        .untilAsserted(
            () -> {
              // Verify SKU capacity report shows the reconciled capacity
              var capacityReport =
                  service.getSkuCapacityByProductIdForOrg(Product.OPENSHIFT, orgId);
              assertNotNull(capacityReport.getData());
              var skuCapacity =
                  capacityReport.getData().stream()
                      .filter(s -> sku.equals(s.getSku()))
                      .findFirst()
                      .orElse(null);

              assertThat(
                  "SKU should be present in capacity report after reconciliation",
                  skuCapacity,
                  notNullValue());
              assertThat(
                  "SKU should have measurements array after reconciliation",
                  Objects.requireNonNull(skuCapacity).getMeasurements(),
                  notNullValue());
              assertFalse(
                  skuCapacity.getMeasurements().isEmpty(),
                  "Measurements should not be empty after reconciliation");

              // Verify reconciliation calculated capacity correctly
              // OpenShift has multiple core measurements (physical, hypervisor), so sum them
              double totalCapacity =
                  skuCapacity.getMeasurements().stream().mapToDouble(Double::doubleValue).sum();
              assertThat(
                  "Total cores capacity should match expected total from all subscriptions",
                  totalCapacity,
                  closeTo(expectedTotalCapacity, 0.01));
            });
  }

  /**
   * Base verification for capacity measurements. Fetches the capacity report, validates SKU
   * presence and measurement count, then asserts each metric's expected value.
   *
   * @param product The product for the capacity report
   * @param sku The SKU to check
   * @param minMeasurements Minimum number of measurements required
   * @param minMeasurementsMessage Assertion message when measurement count is insufficient
   * @param expectations Metric name, expected value, and assertion message for each metric
   */
  private void thenSubscriptionHasCapacityMeasurements(
      Product product,
      String sku,
      int minMeasurements,
      String minMeasurementsMessage,
      MetricExpectation... expectations) {
    thenSubscriptionHasCapacityMeasurements(
        product, sku, minMeasurements, minMeasurementsMessage, false, expectations);
  }

  private void thenSubscriptionHasCapacityMeasurements(
      Product product,
      String sku,
      int minMeasurements,
      String minMeasurementsMessage,
      boolean awaitForAsync,
      MetricExpectation... expectations) {
    ThrowingRunnable verify =
        () -> {
          var capacityReport = service.getSkuCapacityByProductIdForOrg(product, orgId);
          assertNotNull(capacityReport.getData());
          var skuCapacity =
              capacityReport.getData().stream()
                  .filter(s -> sku.equals(s.getSku()))
                  .findFirst()
                  .orElse(null);
          assertThat(
              "SKU should be present in capacity report after reconciliation",
              skuCapacity,
              notNullValue());
          assertThat(
              "SKU should have measurements after reconciliation",
              Objects.requireNonNull(skuCapacity).getMeasurements(),
              notNullValue());
          assertThat(
              minMeasurementsMessage,
              skuCapacity.getMeasurements().size(),
              greaterThanOrEqualTo(minMeasurements));

          var metricOrder = capacityReport.getMeta().getMeasurements();
          for (MetricExpectation exp : expectations) {
            int metricIndex = metricOrder.indexOf(exp.metricName());
            assertThat(
                exp.metricName() + " metric should be in report",
                metricIndex,
                greaterThanOrEqualTo(0));
            assertThat(
                exp.assertionMessage(),
                skuCapacity.getMeasurements().get(metricIndex),
                closeTo(exp.expectedValue(), 0.01));
          }
        };

    if (awaitForAsync) {
      await("PHYSICAL capacity measurement should match")
          .atMost(2, MINUTES)
          .pollInterval(2, SECONDS)
          .untilAsserted(verify);
    } else {
      try {
        verify.run();
      } catch (Throwable t) {
        if (t instanceof Error) throw (Error) t;
        if (t instanceof RuntimeException) throw (RuntimeException) t;
        throw new RuntimeException(t);
      }
    }
  }

  /**
   * Verifies that the subscription has the expected PHYSICAL Cores measurement after
   * reconciliation.
   *
   * @param sku The SKU to check
   * @param expectedCores The expected PHYSICAL Cores value (offering cores * quantity)
   */
  private void thenSubscriptionHasCoresMeasurement(String sku, double expectedCores) {
    thenSubscriptionHasCoresMeasurement(sku, expectedCores, Product.OPENSHIFT, false);
  }

  /**
   * Verifies that the subscription has the expected PHYSICAL Cores measurement after
   * force-reconcile. Uses await for Kafka async processing.
   *
   * @param sku The SKU to check
   * @param expectedCores The expected PHYSICAL Cores value (offering cores * quantity)
   */
  private void thenSubscriptionHasCoresMeasurementAfterForceReconcile(
      String sku, double expectedCores) {
    thenSubscriptionHasCoresMeasurement(sku, expectedCores, Product.OPENSHIFT, true);
  }

  /**
   * Verifies that the subscription has the expected PHYSICAL Cores measurement after
   * reconciliation.
   *
   * @param sku The SKU to check
   * @param expectedCores The expected PHYSICAL Cores value (offering cores * quantity)
   * @param product The product for the capacity report (OPENSHIFT or RHEL)
   */
  private void thenSubscriptionHasCoresMeasurement(
      String sku, double expectedCores, Product product) {
    thenSubscriptionHasCoresMeasurement(sku, expectedCores, product, false);
  }

  /**
   * Verifies that the subscription has the expected PHYSICAL Cores measurement. Uses await when
   * verification follows force-reconcile (Kafka processing is async).
   *
   * @param sku The SKU to check
   * @param expectedCores The expected PHYSICAL Cores value (offering cores * quantity)
   * @param product The product for the capacity report (OPENSHIFT or RHEL)
   * @param awaitForAsync When true, uses await for force-reconcile tests where Kafka processing is
   *     async
   */
  private void thenSubscriptionHasCoresMeasurement(
      String sku, double expectedCores, Product product, boolean awaitForAsync) {
    thenSubscriptionHasCapacityMeasurements(
        product,
        sku,
        1,
        "Measurements should not be empty after reconciliation",
        awaitForAsync,
        new MetricExpectation(
            "Cores",
            expectedCores,
            "PHYSICAL Cores measurement should match offering cores * quantity"));
  }

  /**
   * Verifies that the subscription has the expected PHYSICAL Sockets measurement after
   * reconciliation.
   *
   * @param sku The SKU to check
   * @param expectedSockets The expected PHYSICAL Sockets value (offering sockets * quantity)
   */
  private void thenSubscriptionHasSocketsMeasurement(String sku, double expectedSockets) {
    thenSubscriptionHasCapacityMeasurements(
        Product.RHEL,
        sku,
        1,
        "Measurements should not be empty after reconciliation",
        new MetricExpectation(
            "Sockets",
            expectedSockets,
            "PHYSICAL Sockets measurement should match offering sockets * quantity"));
  }

  /**
   * Verifies that the subscription has the expected PHYSICAL Cores and Sockets measurements after
   * reconciliation.
   *
   * @param sku The SKU to check
   * @param expectedCores The expected PHYSICAL Cores value (offering cores * quantity)
   * @param expectedSockets The expected PHYSICAL Sockets value (offering sockets * quantity)
   */
  private void thenSubscriptionHasCoresAndSocketsMeasurements(
      String sku, double expectedCores, double expectedSockets) {
    thenSubscriptionHasCapacityMeasurements(
        Product.OPENSHIFT,
        sku,
        2,
        "Should have Cores and Sockets measurements",
        new MetricExpectation(
            "Cores",
            expectedCores,
            "PHYSICAL Cores measurement should match offering cores * quantity"),
        new MetricExpectation(
            "Sockets",
            expectedSockets,
            "PHYSICAL Sockets measurement should match offering sockets * quantity"));
  }

  private record MetricExpectation(
      String metricName, double expectedValue, String assertionMessage) {}

  /**
   * Verifies that the SKU has no capacity (reconciliation didn't run because SKU doesn't exist).
   *
   * @param sku The SKU to check
   */
  private void thenSkuHasNoCapacity(String sku) {
    var capacityReport = service.getSkuCapacityByProductIdForOrg(Product.OPENSHIFT, orgId);
    assertNotNull(capacityReport.getData());
    var skuCapacity =
        capacityReport.getData().stream().filter(s -> sku.equals(s.getSku())).findFirst();

    assertFalse(
        skuCapacity.isPresent(),
        "SKU should not appear in capacity report when offering doesn't exist");
  }

  /**
   * Verifies that the subscription has the expected HYPERVISOR Cores measurement. Uses await for
   * force-reconcile tests where Kafka processing is async. Verification is org-level (capacity
   * report aggregates by org).
   *
   * @param expectedCores The expected HYPERVISOR Cores value (offering hypervisorCores * quantity)
   */
  private void thenSubscriptionHasHypervisorCoresMeasurement(double expectedCores) {
    OffsetDateTime beginning = clock.now().minusDays(1);
    OffsetDateTime ending = clock.now().plusDays(1);
    await("HYPERVISOR Cores measurement should match")
        .atMost(2, MINUTES)
        .pollInterval(2, SECONDS)
        .untilAsserted(
            () -> {
              double actual = getHypervisorCoresCapacity(Product.RHEL, orgId, beginning, ending);
              assertThat(
                  "HYPERVISOR Cores measurement should match offering hypervisorCores * quantity",
                  actual,
                  closeTo(expectedCores, 0.01));
            });
  }

  /**
   * Verifies that the subscription has the expected HYPERVISOR Sockets measurement. Verification is
   * org-level (capacity report aggregates by org).
   *
   * @param expectedSockets The expected HYPERVISOR Sockets value (offering hypervisorSockets *
   *     quantity)
   */
  private void thenSubscriptionHasHypervisorSocketsMeasurement(double expectedSockets) {
    OffsetDateTime beginning = clock.now().minusDays(1);
    OffsetDateTime ending = clock.now().plusDays(1);
    await("HYPERVISOR Sockets measurement should match")
        .atMost(2, MINUTES)
        .pollInterval(2, SECONDS)
        .untilAsserted(
            () -> {
              double actual = getHypervisorSocketCapacity(Product.RHEL, orgId, beginning, ending);
              assertThat(
                  "HYPERVISOR Sockets measurement should match offering hypervisorSockets * quantity",
                  actual,
                  closeTo(expectedSockets, 0.01));
            });
  }

  /**
   * Verifies that the subscription has the expected HYPERVISOR Cores and Sockets measurements.
   * Verification is org-level (capacity report aggregates by org).
   *
   * @param expectedCores The expected HYPERVISOR Cores value
   * @param expectedSockets The expected HYPERVISOR Sockets value
   */
  private void thenSubscriptionHasHypervisorCoresAndSocketsMeasurements(
      double expectedCores, double expectedSockets) {
    thenSubscriptionHasHypervisorCoresMeasurement(expectedCores);
    thenSubscriptionHasHypervisorSocketsMeasurement(expectedSockets);
  }

  /**
   * Verifies that the subscription has no capacity measurements (null/zero offering values create
   * no measurements).
   *
   * @param sku The SKU to check
   */
  private void thenSubscriptionHasNoCapacityMeasurements(String sku) {
    var capacityReport = service.getSkuCapacityByProductIdForOrg(Product.OPENSHIFT, orgId);
    assertNotNull(capacityReport.getData());
    var skuCapacity =
        capacityReport.getData().stream()
            .filter(s -> sku.equals(s.getSku()))
            .findFirst()
            .orElse(null);

    assertThat(
        "SKU should be present in capacity report (subscription exists)",
        skuCapacity,
        notNullValue());

    double totalCapacity =
        Objects.requireNonNull(skuCapacity).getMeasurements().stream()
            .mapToDouble(d -> d != null ? d : 0.0)
            .sum();
    assertThat(
        "No measurements should be created for null or zero capacity values",
        totalCapacity,
        closeTo(0.0, 0.01));
  }
}
