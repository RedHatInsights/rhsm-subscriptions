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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.capacity.files.ProductWhitelist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityKey;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.hamcrest.MockitoHamcrest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class CapacityReconciliationControllerTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired CapacityReconciliationController capacityReconciliationController;

  @MockBean ProductWhitelist whitelist;

  @MockBean OfferingRepository offeringRepository;

  @MockBean SubscriptionRepository subscriptionRepository;

  @MockBean SubscriptionCapacityRepository subscriptionCapacityRepository;

  @MockBean CapacityProductExtractor capacityProductExtractor;

  @MockBean
  KafkaTemplate<String, ReconcileCapacityByOfferingTask> reconcileCapacityByOfferingKafkaTemplate;

  @Autowired
  @Qualifier("reconcileCapacityTasks")
  private TaskQueueProperties taskQueueProperties;

  @AfterEach
  void afterEach() {
    reset(subscriptionCapacityRepository, capacityProductExtractor, offeringRepository, whitelist);
  }

  @Test
  void shouldAddNewCapacitiesIfNotAlreadyExisting() {

    List<String> productIds = List.of("RHEL");
    Offering offering = Offering.builder().productIds(Set.of(45)).sku("MCT3718").build();

    Subscription newSubscription = createSubscription("456", 10);
    Collection<SubscriptionCapacity> capacities =
        productIds.stream()
            .map(productId -> SubscriptionCapacity.from(newSubscription, offering, productId))
            .collect(Collectors.toList());

    when(whitelist.productIdMatches(any())).thenReturn(true);
    when(capacityProductExtractor.getProducts(offering)).thenReturn(new HashSet<>(productIds));
    when(offeringRepository.getById("MCT3718")).thenReturn(offering);
    when(subscriptionCapacityRepository.findByKeySubscriptionId("456"))
        .thenReturn(Collections.emptyList());

    capacityReconciliationController.reconcileCapacityForSubscription(newSubscription);

    verify(subscriptionCapacityRepository).saveAll(capacities);
    verify(subscriptionCapacityRepository).deleteAll(Collections.emptyList());
  }

  @Test
  void shouldUpdateCapacitiesIfThereAreChanges() {

    Set<String> productIds = Set.of("RHEL", "RHEL Workstation");
    Offering updatedOffering =
        Offering.builder()
            .productIds(Set.of(45, 25))
            .sku("MCT3718")
            .physicalCores(20)
            .physicalSockets(40)
            .build();

    Subscription updatedSubscription = createSubscription("456", 10);

    List<SubscriptionCapacity> existingCapacities =
        List.of(
            SubscriptionCapacity.builder()
                .key(
                    SubscriptionCapacityKey.builder()
                        .subscriptionId("456")
                        .ownerId("123")
                        .productId("RHEL")
                        .build())
                .physicalCores(10)
                .physicalSockets(15)
                .build(),
            SubscriptionCapacity.builder()
                .key(
                    SubscriptionCapacityKey.builder()
                        .subscriptionId("456")
                        .ownerId("123")
                        .productId("RHEL Workstation")
                        .build())
                .physicalCores(10)
                .physicalSockets(15)
                .build());

    List<SubscriptionCapacity> updatedCapacities =
        productIds.stream()
            .map(
                productId ->
                    SubscriptionCapacity.from(updatedSubscription, updatedOffering, productId))
            .collect(Collectors.toList());

    when(whitelist.productIdMatches(any())).thenReturn(true);
    when(capacityProductExtractor.getProducts(updatedOffering)).thenReturn(productIds);
    when(offeringRepository.getById("MCT3718")).thenReturn(updatedOffering);
    when(subscriptionCapacityRepository.findByKeySubscriptionId("456"))
        .thenReturn(existingCapacities);

    capacityReconciliationController.reconcileCapacityForSubscription(updatedSubscription);

    verify(subscriptionCapacityRepository).saveAll(updatedCapacities);
    verify(subscriptionCapacityRepository).deleteAll(Collections.emptyList());
  }

  @Test
  void shouldNotAddNewCapacitiesWhenProductIsNotOnWhitelist() {

    Offering offering = Offering.builder().productIds(Set.of(45, 25)).sku("MCT3718").build();
    Subscription subscription = createSubscription("456", 10);

    List<SubscriptionCapacity> existingCapacities =
        List.of(
            SubscriptionCapacity.builder()
                .key(
                    SubscriptionCapacityKey.builder()
                        .subscriptionId("456")
                        .ownerId("123")
                        .productId("RHEL")
                        .build())
                .physicalCores(10)
                .physicalSockets(15)
                .build(),
            SubscriptionCapacity.builder()
                .key(
                    SubscriptionCapacityKey.builder()
                        .subscriptionId("456")
                        .ownerId("123")
                        .productId("RHEL Workstation")
                        .build())
                .physicalCores(10)
                .physicalSockets(15)
                .build());

    when(whitelist.productIdMatches(any())).thenReturn(false);
    when(capacityProductExtractor.getProducts(offering))
        .thenReturn(Set.of("RHEL", "RHEL Workstation"));
    when(offeringRepository.getById("MCT3718")).thenReturn(offering);
    when(subscriptionCapacityRepository.findByKeySubscriptionId("456"))
        .thenReturn(existingCapacities);

    capacityReconciliationController.reconcileCapacityForSubscription(subscription);
    verify(subscriptionCapacityRepository)
        .deleteAll(
            MockitoHamcrest.argThat(
                Matchers.containsInAnyOrder(existingCapacities.get(0), existingCapacities.get(1))));
  }

  @Test
  void shouldRemoveAllCapacitiesWhenProductIsNotOnWhitelist() {

    Offering offering = Offering.builder().productIds(Set.of(45, 25)).sku("MCT3718").build();
    Subscription subscription = createSubscription("456", 10);

    when(whitelist.productIdMatches(any())).thenReturn(false);
    when(capacityProductExtractor.getProducts(offering)).thenReturn(Set.of("RHEL1", "RHEL2"));
    when(offeringRepository.getById("MCT3718")).thenReturn(offering);
    when(subscriptionCapacityRepository.findByKeySubscriptionId("456"))
        .thenReturn(Collections.emptyList());

    capacityReconciliationController.reconcileCapacityForSubscription(subscription);
    verify(subscriptionCapacityRepository, never()).saveAll(any());
  }

  @Test
  void shouldAddNewCapacitiesAndRemoveAllStaleCapacities() {

    Set<String> productIds = Set.of("RHEL");
    Offering offering = Offering.builder().productIds(Set.of(45)).sku("MCT3718").build();
    Subscription subscription = createSubscription("456", 10);

    List<SubscriptionCapacity> newCapacities =
        List.of(SubscriptionCapacity.from(subscription, offering, "RHEL"));

    List<SubscriptionCapacity> staleCapacities =
        List.of(
            SubscriptionCapacity.builder()
                .key(
                    SubscriptionCapacityKey.builder()
                        .subscriptionId("456")
                        .ownerId("123")
                        .productId("STALE RHEL")
                        .build())
                .physicalCores(10)
                .physicalSockets(15)
                .build(),
            SubscriptionCapacity.builder()
                .key(
                    SubscriptionCapacityKey.builder()
                        .subscriptionId("456")
                        .ownerId("123")
                        .productId("STALE RHEL Workstation")
                        .build())
                .physicalCores(10)
                .physicalSockets(15)
                .build());

    when(whitelist.productIdMatches(any())).thenReturn(true);
    when(capacityProductExtractor.getProducts(offering)).thenReturn(productIds);
    when(offeringRepository.getById("MCT3718")).thenReturn(offering);
    when(subscriptionCapacityRepository.findByKeySubscriptionId("456")).thenReturn(staleCapacities);

    capacityReconciliationController.reconcileCapacityForSubscription(subscription);
    verify(subscriptionCapacityRepository).saveAll(newCapacities);
    verify(subscriptionCapacityRepository)
        .deleteAll(
            MockitoHamcrest.argThat(
                Matchers.containsInAnyOrder(staleCapacities.get(0), staleCapacities.get(1))));
  }

  @Test
  void shouldReconcileCapacityWithinLimitForOrgAndQueueTaskForNext() {

    Offering offering = Offering.builder().productIds(Set.of(45)).sku("MCT3718").build();

    Page<Subscription> subscriptions = mock(Page.class);
    when(subscriptions.hasNext()).thenReturn(true);

    when(subscriptionRepository.findBySku(
            "MCT3718", ResourceUtils.getPageable(0, 2, Sort.by("subscriptionId"))))
        .thenReturn(subscriptions);
    capacityReconciliationController.reconcileCapacityForOffering(offering.getSku(), 0, 2);
    verify(reconcileCapacityByOfferingKafkaTemplate)
        .send(
            "platform.rhsm-subscriptions.capacity.reconcile",
            ReconcileCapacityByOfferingTask.builder().sku("MCT3718").offset(2).limit(2).build());
  }

  private Subscription createSubscription(String subId, int quantity) {

    return Subscription.builder()
        .ownerId("123")
        .subscriptionId(subId)
        .quantity(quantity)
        .startDate(NOW)
        .endDate(NOW.plusDays(30))
        .sku("MCT3718")
        .build();
  }
}
