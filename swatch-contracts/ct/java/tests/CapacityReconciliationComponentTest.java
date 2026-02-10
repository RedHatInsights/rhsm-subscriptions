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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redhat.swatch.component.tests.api.DefaultMessageValidator;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.OfferingResponse;
import domain.Offering;
import domain.Product;
import domain.ReconcileCapacityByOfferingTask;
import domain.Subscription;
import io.restassured.response.Response;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.apache.http.HttpStatus;
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
    // Given: An offering with a subscription
    final String testSku = RandomUtils.generateRandom();
    Offering offering = Offering.buildOpenShiftOffering(testSku, CORES_CAPACITY, null);
    wiremock.forProductAPI().stubOfferingData(offering);

    Response syncResponse = service.syncOffering(testSku);
    assertThat("Sync offering should succeed", syncResponse.statusCode(), is(HttpStatus.SC_OK));

    // Create one subscription (minimum needed for task to be published)
    Subscription subscription =
        Subscription.buildOpenShiftSubscriptionUsingSku(
            orgId, Map.of(CORES, CORES_CAPACITY), testSku);
    Response saveResponse = service.saveSubscriptions(false, subscription);
    assertThat(
        "Creating subscription should succeed", saveResponse.statusCode(), is(HttpStatus.SC_OK));

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
   * Creates an offering with multiple subscriptions for testing reconciliation.
   *
   * @return The SKU of the created offering
   */
  private String givenOfferingWithMultipleSubscriptions() {
    final String testSku = RandomUtils.generateRandom();

    // Create offering
    Offering offering = Offering.buildOpenShiftOffering(testSku, CORES_CAPACITY, null);
    wiremock.forProductAPI().stubOfferingData(offering);

    Response syncResponse = service.syncOffering(testSku);
    assertThat("Sync offering should succeed", syncResponse.statusCode(), is(HttpStatus.SC_OK));

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
}
