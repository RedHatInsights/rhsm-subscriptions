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
package com.redhat.swatch.contract.service;

import static com.redhat.swatch.contract.service.CapacityReconciliationService.CORES;
import static com.redhat.swatch.contract.service.CapacityReconciliationService.HYPERVISOR;
import static com.redhat.swatch.contract.service.CapacityReconciliationService.PHYSICAL;
import static com.redhat.swatch.contract.service.CapacityReconciliationService.SOCKETS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.redhat.swatch.contract.config.Channels;
import com.redhat.swatch.contract.config.ProductDenylist;
import com.redhat.swatch.contract.model.ReconcileCapacityByOfferingTask;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionMeasurementEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.test.resources.InMemoryMessageBrokerKafkaResource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
class CapacityReconciliationServiceTest {
  private static final String SUBSCRIPTION_ID = "456";
  private static final String SKU = "MCT3718";
  private static final String ROSA = "rosa";
  private static final String RHEL = "RHEL";
  private static final String RHEL_WORKSTATION = "RHEL Workstation";
  private static final Map<String, Integer> PRODUCTS =
      Map.of(RHEL, 45, "RHEL Workstation", 25, ROSA, 1066);

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Inject CapacityReconciliationService capacityReconciliationController;

  @InjectMock ProductDenylist denyList;
  @InjectMock SubscriptionRepository subscriptionRepository;
  @Inject @Any InMemoryConnector connector;

  InMemorySink<ReconcileCapacityByOfferingTask> reconcileCapacityByOfferingSink;

  private SubscriptionEntity subscription;

  @BeforeEach
  void setup() {
    reconcileCapacityByOfferingSink = connector.sink(Channels.CAPACITY_RECONCILE);
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
  }

  @Test
  void shouldUpdateCapacitiesIfThereAreChanges() {
    givenSubscriptionMeasurement(PHYSICAL, SOCKETS, 15.0);
    givenSubscriptionMeasurement(PHYSICAL, CORES, 10);
    int expectedCores = 20;
    int expectedSockets = 40;
    givenOffering(expectedCores, expectedSockets, RHEL, RHEL_WORKSTATION);

    whenReconcileCapacityForSubscription();

    thenSubscriptionMeasurementsContains(PHYSICAL, CORES, expectedCores);
    thenSubscriptionMeasurementsContains(PHYSICAL, SOCKETS, expectedSockets);
  }

  @Test
  void shouldRemoveAllCapacitiesWhenProductIsOnDenyList() {
    givenOffering(RHEL);
    givenSubscriptionMeasurement(PHYSICAL, SOCKETS, 15.0);
    givenSubscriptionMeasurement(HYPERVISOR, CORES, 10);

    when(denyList.productIdMatches(any())).thenReturn(true);
    whenReconcileCapacityForSubscription();

    assertTrue(subscription.getSubscriptionMeasurements().isEmpty());
  }

  @Test
  void shouldAddNewCapacitiesAndRemoveAllStaleCapacities() {
    int expectedCores = 20;
    givenOffering(expectedCores, RHEL);
    givenSubscriptionMeasurement(HYPERVISOR, CORES, 10);
    givenSubscriptionMeasurement(PHYSICAL, SOCKETS, 15.0);

    whenReconcileCapacityForSubscription();

    thenSubscriptionMeasurementsOnlyContains(PHYSICAL, CORES, expectedCores);
  }

  @Test
  void shouldReconcileCapacityWithinLimitForOrgAndQueueTaskForNext() {
    OfferingEntity offering = OfferingEntity.builder().productIds(Set.of(45)).sku(SKU).build();

    List<SubscriptionEntity> subscriptions = new ArrayList<>();
    // add 2 subscriptions because the limit is 2, so the logic will trigger another event.
    subscriptions.add(SubscriptionEntity.builder().offering(offering).build());
    subscriptions.add(SubscriptionEntity.builder().offering(offering).build());
    when(subscriptionRepository.findByOfferingSku("MCT3718", 0, 2)).thenReturn(subscriptions);
    capacityReconciliationController.reconcileCapacityForOffering(offering.getSku(), 0, 2);
    assertEquals(1, reconcileCapacityByOfferingSink.received().size());
    var task = reconcileCapacityByOfferingSink.received().get(0).getPayload();
    assertEquals(SKU, task.getSku());
    assertEquals(2, task.getOffset());
    assertEquals(2, task.getLimit());
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
    OfferingEntity offering =
        OfferingEntity.builder()
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
        .add(
            SubscriptionMeasurementEntity.builder()
                .measurementType(measurementType)
                .metricId(metricId)
                .value(value * subscription.getQuantity())
                .subscription(subscription)
                .build());
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
    var existing = subscription.getSubscriptionMeasurement(metricId, measurementType);
    assertTrue(existing.isPresent(), "measurement not found: " + metricId);
    assertEquals(value * subscription.getQuantity(), existing.get().getValue());
  }

  private SubscriptionEntity createSubscription() {
    return SubscriptionEntity.builder()
        .orgId("123")
        .subscriptionId(SUBSCRIPTION_ID)
        .quantity(10)
        .startDate(NOW)
        .endDate(NOW.plusDays(30))
        .subscriptionMeasurements(new ArrayList<>())
        .build();
  }
}
