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
package org.candlepin.subscriptions.capacity;

import static org.candlepin.subscriptions.resource.CapacityResource.HYPERVISOR;
import static org.candlepin.subscriptions.resource.CapacityResource.PHYSICAL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.capacity.files.ProductDenylist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurementKey;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"capacity-ingress", "test"})
class CapacityReconciliationControllerTest {
  private static final String SOCKETS = "Sockets";
  private static final String CORES = "Cores";
  private static final String SUBSCRIPTION_ID = "456";
  private static final String SKU = "MCT3718";
  private static final String ROSA = "rosa";
  private static final String RHEL = "RHEL";
  private static final String RHEL_WORKSTATION = "RHEL Workstation";
  private static final Map<String, Integer> PRODUCTS =
      Map.of(RHEL, 45, "RHEL Workstation", 25, ROSA, 1066);

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired CapacityReconciliationController capacityReconciliationController;

  @MockBean ProductDenylist denyList;
  @MockBean OfferingRepository offeringRepository;
  @MockBean SubscriptionRepository subscriptionRepository;

  @MockBean
  KafkaTemplate<String, ReconcileCapacityByOfferingTask> reconcileCapacityByOfferingKafkaTemplate;

  private Subscription subscription;

  @BeforeEach
  void setup() {
    subscription = createSubscription();
    when(denyList.productIdMatches(any())).thenReturn(false);
  }

  @Test
  void shouldAddNewMeasurementsAndProductIdsIfNotAlreadyExisting() {
    int expectedCores = 42;
    int expectedHypervisorCores = 43;
    int expectedSockets = 44;
    int expectedHypervisorSockets = 45;

    givenOffering(
        expectedCores, expectedHypervisorCores, expectedSockets, expectedHypervisorSockets, RHEL);

    whenReconcileCapacityForSubscription();

    thenSubscriptionMeasurementsContains(PHYSICAL, CORES, expectedCores);
    thenSubscriptionMeasurementsContains(HYPERVISOR, CORES, expectedHypervisorCores);
    thenSubscriptionMeasurementsContains(PHYSICAL, SOCKETS, expectedSockets);
    thenSubscriptionMeasurementsContains(HYPERVISOR, SOCKETS, expectedHypervisorSockets);
    assertThat(subscription.getSubscriptionProductIds(), containsInAnyOrder(RHEL));
  }

  @Test
  void shouldUpdateCapacitiesIfThereAreChanges() {
    givenSubscriptionMeasurement(PHYSICAL, SOCKETS, 15.0);
    givenSubscriptionMeasurement(PHYSICAL, CORES, 10);
    givenSubscriptionProduct(RHEL);
    int expectedCores = 20;
    int expectedSockets = 40;
    givenOffering(expectedCores, expectedSockets, RHEL, RHEL_WORKSTATION);

    whenReconcileCapacityForSubscription();

    thenSubscriptionMeasurementsContains(PHYSICAL, CORES, expectedCores);
    thenSubscriptionMeasurementsContains(PHYSICAL, SOCKETS, expectedSockets);
    assertEquals(subscription.getSubscriptionProductIds(), Set.of(RHEL, RHEL_WORKSTATION));
  }

  @Test
  void shouldRemoveAllCapacitiesWhenProductIsOnDenyList() {
    givenOffering(RHEL);
    givenSubscriptionMeasurement(PHYSICAL, SOCKETS, 15.0);
    givenSubscriptionMeasurement(HYPERVISOR, CORES, 10);
    givenSubscriptionProduct(RHEL);

    when(denyList.productIdMatches(any())).thenReturn(true);
    whenReconcileCapacityForSubscription();

    assertTrue(subscription.getSubscriptionMeasurements().isEmpty());
    assertTrue(subscription.getSubscriptionProductIds().isEmpty());
  }

  @Test
  void shouldAddNewCapacitiesAndRemoveAllStaleCapacities() {
    int expectedCores = 20;
    givenOffering(expectedCores, RHEL);
    givenSubscriptionProduct("STALE RHEL");
    givenSubscriptionProduct("STALE RHEL Workstation");
    givenSubscriptionMeasurement(HYPERVISOR, CORES, 10);
    givenSubscriptionMeasurement(PHYSICAL, SOCKETS, 15.0);

    whenReconcileCapacityForSubscription();

    thenSubscriptionMeasurementsOnlyContains(PHYSICAL, CORES, expectedCores);
    assertEquals(subscription.getSubscriptionProductIds(), Set.of(RHEL));
  }

  @Test
  void shouldReconcileCapacityWithinLimitForOrgAndQueueTaskForNext() {
    Offering offering = Offering.builder().productIds(Set.of(45)).sku(SKU).build();

    Page<Subscription> subscriptions = mock(Page.class);
    when(subscriptions.hasNext()).thenReturn(true);

    when(subscriptionRepository.findByOfferingSku(
            "MCT3718", ResourceUtils.getPageable(0, 2, Sort.by("subscriptionId"))))
        .thenReturn(subscriptions);
    capacityReconciliationController.reconcileCapacityForOffering(offering.getSku(), 0, 2);
    verify(reconcileCapacityByOfferingKafkaTemplate)
        .send(
            "platform.rhsm-subscriptions.capacity-reconcile",
            ReconcileCapacityByOfferingTask.builder().sku(SKU).offset(2).limit(2).build());
  }

  @Test
  void enqueueShouldOnlyCreateKafkaMessage() {
    // Some clients (example, OfferingSyncController) should not wait for capacities to reconcile.
    // In that case, the client should be able to enqueue the first capacity reconciliation page,
    // rather than have it be worked on immediately.
    capacityReconciliationController.enqueueReconcileCapacityForOffering(SKU);
    verify(reconcileCapacityByOfferingKafkaTemplate)
        .send(
            "platform.rhsm-subscriptions.capacity-reconcile",
            ReconcileCapacityByOfferingTask.builder().sku(SKU).offset(0).limit(100).build());
    verifyNoInteractions(subscriptionRepository);
  }

  @Test
  void shouldNotAttemptToCreateDuplicateMeasurementsWhenNoChanges() {
    int expectedCores = 42;
    givenOffering(expectedCores, RHEL);
    givenSubscriptionMeasurement(PHYSICAL, CORES, expectedCores);

    whenReconcileCapacityForSubscription();

    thenSubscriptionMeasurementsOnlyContains(PHYSICAL, CORES, expectedCores);
  }

  @Test
  void shouldNotRemoveMeasurementsWhenOfferingIsMeteredAndHasNoCoresOrSockets() {
    givenMeteredOffering();
    givenSubscriptionMeasurement(PHYSICAL, CORES, 40);

    whenReconcileCapacityForSubscription();

    thenSubscriptionMeasurementsOnlyContains(PHYSICAL, CORES, 40);
  }

  private void givenMeteredOffering() {
    givenOffering(0, ROSA);
    subscription.getOffering().setMetered(true);
  }

  private void givenOffering(String... products) {
    givenOffering(10, products);
  }

  private void givenOffering(int expectedCores, String... products) {
    givenOffering(expectedCores, 0, 0, 0, products);
  }

  private void givenOffering(int expectedCores, int expectedSockets, String... products) {
    givenOffering(expectedCores, 0, expectedSockets, 0, products);
  }

  private void givenOffering(
      int expectedCores,
      int expectedHypervisorCores,
      int expectedSockets,
      int expectedHypervisorSockets,
      String... products) {
    Set<String> productIds = Set.of(products);
    Offering offering =
        Offering.builder()
            .productIds(productIds.stream().map(PRODUCTS::get).collect(Collectors.toSet()))
            .cores(expectedCores)
            .hypervisorCores(expectedHypervisorCores)
            .sockets(expectedSockets)
            .hypervisorSockets(expectedHypervisorSockets)
            .sku(SKU)
            .productTags(productIds)
            .build();

    subscription.setOffering(offering);
  }

  private void givenSubscriptionMeasurement(String measurementType, String metricId, double value) {
    subscription
        .getSubscriptionMeasurements()
        .put(
            SubscriptionMeasurementKey.builder()
                .measurementType(measurementType)
                .metricId(metricId)
                .build(),
            value * subscription.getQuantity());
  }

  private void givenSubscriptionProduct(String product) {
    subscription.getSubscriptionProductIds().add(product);
  }

  private void whenReconcileCapacityForSubscription() {
    capacityReconciliationController.reconcileCapacityForSubscription(subscription);
  }

  private void thenSubscriptionMeasurementsOnlyContains(
      String measurementType, String metricId, int value) {
    thenSubscriptionMeasurementsContains(measurementType, metricId, value);
    assertEquals(1, subscription.getSubscriptionMeasurements().size());
  }

  private void thenSubscriptionMeasurementsContains(
      String measurementType, String metricId, int value) {
    SubscriptionMeasurementKey key =
        SubscriptionMeasurementKey.builder()
            .measurementType(measurementType)
            .metricId(metricId)
            .build();
    Double actualValue = subscription.getSubscriptionMeasurements().get(key);
    assertNotNull(actualValue, "measurement not found: " + key);
    assertEquals(value * subscription.getQuantity(), actualValue);
  }

  private Subscription createSubscription() {
    return Subscription.builder()
        .orgId("123")
        .subscriptionId(SUBSCRIPTION_ID)
        .quantity(10)
        .startDate(NOW)
        .endDate(NOW.plusDays(30))
        .subscriptionMeasurements(new HashMap<>())
        .subscriptionProductIds(new HashSet<>())
        .build();
  }
}
