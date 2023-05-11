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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.capacity.files.ProductDenylist;
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
@ActiveProfiles({"capacity-ingress", "test"})
class CapacityReconciliationControllerTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired CapacityReconciliationController capacityReconciliationController;

  @MockBean ProductDenylist denylist;

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
    reset(subscriptionCapacityRepository, capacityProductExtractor, offeringRepository, denylist);
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

    when(denylist.productIdMatches(any())).thenReturn(false);
    when(capacityProductExtractor.getProducts(offering)).thenReturn(new HashSet<>(productIds));
    when(offeringRepository.findById("MCT3718")).thenReturn(Optional.of(offering));
    when(subscriptionCapacityRepository.findByKeyOrgIdAndKeySubscriptionIdIn(
            "123", Collections.singletonList("456")))
        .thenReturn(Collections.emptyList());

    capacityReconciliationController.reconcileCapacityForSubscription(newSubscription);

    verify(subscriptionCapacityRepository).saveAll(capacities);
    verify(subscriptionCapacityRepository).deleteAll(Collections.emptyList());
  }

  @Test
  void shouldUpdateCapacitiesIfThereAreChanges() {

    Set<String> productIds = Set.of("RHEL", "RHEL Workstation");
    Offering updatedOffering =
        Offering.builder().productIds(Set.of(45, 25)).sku("MCT3718").cores(20).sockets(40).build();

    Subscription updatedSubscription = createSubscription("456", 10);

    List<SubscriptionCapacity> existingCapacities =
        List.of(
            SubscriptionCapacity.builder()
                .key(
                    SubscriptionCapacityKey.builder()
                        .subscriptionId("456")
                        .orgId("123")
                        .productId("RHEL")
                        .build())
                .cores(10)
                .sockets(15)
                .build(),
            SubscriptionCapacity.builder()
                .key(
                    SubscriptionCapacityKey.builder()
                        .subscriptionId("456")
                        .orgId("123")
                        .productId("RHEL Workstation")
                        .build())
                .cores(10)
                .sockets(15)
                .build());

    List<SubscriptionCapacity> updatedCapacities =
        productIds.stream()
            .map(
                productId ->
                    SubscriptionCapacity.from(updatedSubscription, updatedOffering, productId))
            .collect(Collectors.toList());

    when(denylist.productIdMatches(any())).thenReturn(false);
    when(capacityProductExtractor.getProducts(updatedOffering)).thenReturn(productIds);
    when(offeringRepository.findById("MCT3718")).thenReturn(Optional.of(updatedOffering));
    when(subscriptionCapacityRepository.findByKeyOrgIdAndKeySubscriptionIdIn(
            "123", Collections.singletonList("456")))
        .thenReturn(existingCapacities);

    capacityReconciliationController.reconcileCapacityForSubscription(updatedSubscription);

    verify(subscriptionCapacityRepository).saveAll(updatedCapacities);
    verify(subscriptionCapacityRepository).deleteAll(Collections.emptyList());
  }

  @Test
  void shouldNotAddNewCapacitiesWhenProductIsOnDenylist() {

    Offering offering = Offering.builder().productIds(Set.of(45, 25)).sku("MCT3718").build();
    Subscription subscription = createSubscription("456", 10);

    List<SubscriptionCapacity> existingCapacities =
        List.of(
            SubscriptionCapacity.builder()
                .key(
                    SubscriptionCapacityKey.builder()
                        .subscriptionId("456")
                        .orgId("123")
                        .productId("RHEL")
                        .build())
                .cores(10)
                .sockets(15)
                .build(),
            SubscriptionCapacity.builder()
                .key(
                    SubscriptionCapacityKey.builder()
                        .subscriptionId("456")
                        .orgId("123")
                        .productId("RHEL Workstation")
                        .build())
                .cores(10)
                .sockets(15)
                .build());

    when(denylist.productIdMatches(any())).thenReturn(true);
    when(capacityProductExtractor.getProducts(offering))
        .thenReturn(Set.of("RHEL", "RHEL Workstation"));
    when(offeringRepository.findById("MCT3718")).thenReturn(Optional.of(offering));
    when(subscriptionCapacityRepository.findByKeyOrgIdAndKeySubscriptionIdIn(
            "123", Collections.singletonList("456")))
        .thenReturn(existingCapacities);

    capacityReconciliationController.reconcileCapacityForSubscription(subscription);
    verify(subscriptionCapacityRepository)
        .deleteAll(
            MockitoHamcrest.argThat(
                Matchers.containsInAnyOrder(existingCapacities.get(0), existingCapacities.get(1))));
  }

  @Test
  void shouldRemoveAllCapacitiesWhenProductIsOnDenylist() {

    Offering offering = Offering.builder().productIds(Set.of(45, 25)).sku("MCT3718").build();
    Subscription subscription = createSubscription("456", 10);

    when(denylist.productIdMatches(any())).thenReturn(true);
    when(capacityProductExtractor.getProducts(offering)).thenReturn(Set.of("RHEL1", "RHEL2"));
    when(offeringRepository.findById("MCT3718")).thenReturn(Optional.of(offering));
    when(subscriptionCapacityRepository.findByKeyOrgIdAndKeySubscriptionIdIn(
            "123", Collections.singletonList("456")))
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
                        .orgId("123")
                        .productId("STALE RHEL")
                        .build())
                .cores(10)
                .sockets(15)
                .build(),
            SubscriptionCapacity.builder()
                .key(
                    SubscriptionCapacityKey.builder()
                        .subscriptionId("456")
                        .orgId("123")
                        .productId("STALE RHEL Workstation")
                        .build())
                .cores(10)
                .sockets(15)
                .build());

    when(denylist.productIdMatches(any())).thenReturn(false);
    when(capacityProductExtractor.getProducts(offering)).thenReturn(productIds);
    when(offeringRepository.findById("MCT3718")).thenReturn(Optional.of(offering));
    when(subscriptionCapacityRepository.findByKeyOrgIdAndKeySubscriptionIdIn(
            "123", Collections.singletonList("456")))
        .thenReturn(staleCapacities);

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
            "platform.rhsm-subscriptions.capacity-reconcile",
            ReconcileCapacityByOfferingTask.builder().sku("MCT3718").offset(2).limit(2).build());
  }

  @Test
  void enqueueShouldOnlyCreateKafkaMessage() {
    // Some clients (example, OfferingSyncController) should not wait for capacities to reconcile.
    // In that case, the client should be able to enqueue the first capacity reconciliation page,
    // rather than have it be worked on immediately.
    capacityReconciliationController.enqueueReconcileCapacityForOffering("MCT3718");
    verify(reconcileCapacityByOfferingKafkaTemplate)
        .send(
            "platform.rhsm-subscriptions.capacity-reconcile",
            ReconcileCapacityByOfferingTask.builder().sku("MCT3718").offset(0).limit(100).build());
    verifyNoInteractions(subscriptionRepository);
  }

  private Subscription createSubscription(String subId, int quantity) {

    return Subscription.builder()
        .orgId("123")
        .subscriptionId(subId)
        .quantity(quantity)
        .startDate(NOW)
        .endDate(NOW.plusDays(30))
        .sku("MCT3718")
        .build();
  }
}
