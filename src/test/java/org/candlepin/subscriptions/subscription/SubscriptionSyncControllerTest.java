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
package org.candlepin.subscriptions.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.subscription.api.model.SubscriptionProduct;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@DirtiesContext()
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"})
@ActiveProfiles({"worker", "test", "kafka-test", "kafka-queue"})
class SubscriptionSyncControllerTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired SubscriptionSyncController subscriptionSyncController;

  @Autowired private ApplicationClock clock;

  @MockBean SubscriptionRepository subscriptionRepository;

  @MockBean CapacityReconciliationController capacityReconciliationController;

  @MockBean SubscriptionService subscriptionService;

  @Autowired SubscriptionWorker subscriptionWorker;

  @Autowired
  @Qualifier("subscriptionTasks")
  private TaskQueueProperties taskQueueProperties;

  @Test
  void shouldCreateNewRecordOnQuantityChange() {
    Mockito.when(subscriptionRepository.findActiveSubscription(Mockito.anyString()))
        .thenReturn(Optional.of(createSubscription("123", "testsku", "456")));
    var dto = createDto("456", 10);
    subscriptionSyncController.syncSubscription(dto);
    verify(subscriptionRepository, Mockito.times(2)).save(Mockito.any(Subscription.class));
    verify(capacityReconciliationController)
        .reconcileCapacityForSubscription(Mockito.any(Subscription.class));
  }

  @Test
  void shouldUpdateRecordOnNoQuantityChange() {
    Mockito.when(subscriptionRepository.findActiveSubscription(Mockito.anyString()))
        .thenReturn(Optional.of(createSubscription("123", "testsku", "456")));
    var dto = createDto("456", 4);
    subscriptionSyncController.syncSubscription(dto);
    verify(subscriptionRepository, Mockito.times(1)).save(Mockito.any(Subscription.class));
    verify(capacityReconciliationController)
        .reconcileCapacityForSubscription(Mockito.any(Subscription.class));
  }

  @Test
  void shouldCreateNewRecordOnNotFound() {
    Mockito.when(subscriptionRepository.findActiveSubscription(Mockito.anyString()))
        .thenReturn(Optional.empty());
    var dto = createDto("456", 10);
    subscriptionSyncController.syncSubscription(dto);
    verify(subscriptionRepository, Mockito.times(1)).save(Mockito.any(Subscription.class));
    verify(capacityReconciliationController)
        .reconcileCapacityForSubscription(Mockito.any(Subscription.class));
  }

  @Test
  void shouldSyncSubscriptionFromServiceForASubscriptionID() {
    Mockito.when(subscriptionRepository.findActiveSubscription(Mockito.anyString()))
        .thenReturn(Optional.of(createSubscription("123", "testsku", "456")));
    var dto = createDto("456", 10);
    Mockito.when(subscriptionService.getSubscriptionById("456")).thenReturn(dto);
    subscriptionSyncController.syncSubscription(dto.getId().toString());
    verify(subscriptionService).getSubscriptionById(Mockito.anyString());
  }

  @Test
  void shouldSyncSubscriptionsWithinLimitForOrgAndQueueTaskForNext() throws InterruptedException {

    CountDownLatch latch = new CountDownLatch(1);
    List<org.candlepin.subscriptions.subscription.api.model.Subscription> subscriptions =
        List.of(
            createDto(100, "456", 10),
            createDto(100, "457", 10),
            createDto(100, "458", 10),
            createDto(100, "459", 10),
            createDto(100, "500", 10));

    Mockito.when(subscriptionService.getSubscriptionsByOrgId("100", 0, 3))
        .thenReturn(List.of(subscriptions.get(0), subscriptions.get(1), subscriptions.get(2)));
    Mockito.when(subscriptionService.getSubscriptionsByOrgId("100", 2, 3))
        .thenReturn(List.of(subscriptions.get(2), subscriptions.get(3), subscriptions.get(4)));
    Mockito.when(subscriptionService.getSubscriptionsByOrgId("100", 4, 3))
        .thenReturn(List.of(subscriptions.get(4)));
    subscriptions.forEach(
        subscription -> {
          Mockito.when(
                  subscriptionRepository.findActiveSubscription(subscription.getId().toString()))
              .thenReturn(Optional.of(convertDto(subscription)));
        });

    subscriptionSyncController.syncSubscriptions("100", 0, 2);
    latch.await(10L, TimeUnit.SECONDS);
    assertEquals(2, subscriptionWorker.noOfTimesSyncSubsExecuted);
  }

  private Subscription createSubscription(String orgId, String sku, String subId) {
    final Subscription subscription = new Subscription();
    subscription.setSubscriptionId(subId);
    subscription.setOwnerId(orgId);
    subscription.setQuantity(4L);
    subscription.setSku(sku);
    subscription.setStartDate(NOW);
    subscription.setEndDate(NOW.plusDays(30));

    return subscription;
  }

  private org.candlepin.subscriptions.subscription.api.model.Subscription createDto(
      String subId, int quantity) {

    return createDto(1234, subId, quantity);
  }

  private org.candlepin.subscriptions.subscription.api.model.Subscription createDto(
      Integer orgId, String subId, int quantity) {
    final var dto = new org.candlepin.subscriptions.subscription.api.model.Subscription();
    dto.setQuantity(quantity);
    dto.setId(Integer.valueOf(subId));
    dto.setSubscriptionNumber("123");
    dto.setEffectiveStartDate(NOW.toEpochSecond());
    dto.setEffectiveEndDate(NOW.plusDays(30).toEpochSecond());
    dto.setWebCustomerId(orgId);

    var product = new SubscriptionProduct().parentSubscriptionProductId(null).sku("testsku");
    List<SubscriptionProduct> products = Collections.singletonList(product);
    dto.setSubscriptionProducts(products);

    return dto;
  }

  private org.candlepin.subscriptions.db.model.Subscription convertDto(
      org.candlepin.subscriptions.subscription.api.model.Subscription subscription) {

    return org.candlepin.subscriptions.db.model.Subscription.builder()
        .subscriptionId(String.valueOf(subscription.getId()))
        .sku(SubscriptionDtoUtil.extractSku(subscription))
        .ownerId(subscription.getWebCustomerId().toString())
        .accountNumber(String.valueOf(subscription.getOracleAccountNumber()))
        .quantity(subscription.getQuantity())
        .startDate(clock.dateFromMilliseconds(subscription.getEffectiveStartDate()))
        .endDate(clock.dateFromMilliseconds(subscription.getEffectiveEndDate()))
        .marketplaceSubscriptionId(SubscriptionDtoUtil.extractMarketplaceId(subscription))
        .build();
  }
}
