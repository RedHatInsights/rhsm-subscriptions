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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.capacity.files.ProductWhitelist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@DirtiesContext
@ActiveProfiles({"capacity-ingress", "test"})
class SubscriptionSyncControllerTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired SubscriptionSyncController subscriptionSyncController;

  @Autowired private ApplicationClock clock;

  @MockBean ProductWhitelist whitelist;

  @MockBean OfferingRepository offeringRepository;

  @MockBean SubscriptionRepository subscriptionRepository;

  @MockBean OrgConfigRepository orgConfigRepository;

  @MockBean CapacityReconciliationController capacityReconciliationController;

  @MockBean SubscriptionService subscriptionService;

  @MockBean KafkaTemplate<String, SyncSubscriptionsTask> subscriptionsKafkaTemplate;

  @Autowired
  @Qualifier("syncSubscriptionTasks")
  private TaskQueueProperties taskQueueProperties;

  @Test
  void shouldCreateNewRecordOnQuantityChange() {
    Mockito.when(subscriptionRepository.findActiveSubscription(Mockito.anyString()))
        .thenReturn(Optional.of(createSubscription("123", "testsku", "456")));
    Mockito.when(offeringRepository.existsById("testsku")).thenReturn(true);
    when(whitelist.productIdMatches(any())).thenReturn(true);
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
    Mockito.when(offeringRepository.existsById("testsku")).thenReturn(true);
    when(whitelist.productIdMatches(any())).thenReturn(true);
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
    Mockito.when(offeringRepository.existsById("testsku")).thenReturn(true);
    when(whitelist.productIdMatches(any())).thenReturn(true);
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
    Mockito.when(offeringRepository.findById(anyString()))
        .thenReturn(Optional.of(Offering.builder().sku("testSku").build()));
    var dto = createDto("456", 10);
    Mockito.when(subscriptionService.getSubscriptionById("456")).thenReturn(dto);
    subscriptionSyncController.syncSubscription(dto.getId().toString());
    verify(subscriptionService).getSubscriptionById(Mockito.anyString());
  }

  @Test
  void shouldSkipSyncSubscriptionIfSkuIsNotOnAllowList() {
    when(whitelist.productIdMatches(any())).thenReturn(false);
    var dto = createDto("456", 10);
    Mockito.when(subscriptionService.getSubscriptionById("456")).thenReturn(dto);
    subscriptionSyncController.syncSubscription(dto.getId().toString());
    verifyNoInteractions(subscriptionRepository);
  }

  @Test
  void shouldSkipSyncSubscriptionIfSkuIsNotInOfferingRepository() {
    when(whitelist.productIdMatches(any())).thenReturn(true);
    var dto = createDto("456", 10);
    Mockito.when(subscriptionService.getSubscriptionById("456")).thenReturn(dto);
    subscriptionSyncController.syncSubscription(dto.getId().toString());
    verifyNoInteractions(subscriptionRepository);
  }

  @Test
  void shouldSyncSubscriptionsWithinLimitForOrgAndQueueTaskForNext() {
    when(whitelist.productIdMatches(any())).thenReturn(true);
    Mockito.when(offeringRepository.existsById("testsku")).thenReturn(true);

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
    verify(subscriptionRepository, times(3)).save(any());
    verify(subscriptionsKafkaTemplate)
        .send(
            "platform.rhsm-subscriptions.subscription-sync",
            SyncSubscriptionsTask.builder().orgId("100").offset(2).limit(2).build());
  }

  @Test
  void shouldSyncSubscriptionsSyncSubIfRecent() {
    when(whitelist.productIdMatches(any())).thenReturn(true);
    Mockito.when(offeringRepository.existsById(any())).thenReturn(true);

    // When syncing an org's subs, a sub should be synced if it is within effective*Dates
    var dto = createDto("456", 10);
    dto.setEffectiveStartDate(toEpochMillis(NOW.minusMonths(6)));
    dto.setEffectiveEndDate(toEpochMillis(NOW.plusMonths(6)));
    Mockito.when(subscriptionService.getSubscriptionsByOrgId(any(), anyInt(), anyInt()))
        .thenReturn(List.of(dto));

    subscriptionSyncController.syncSubscriptions("100", 0, 1);

    verify(subscriptionService).getSubscriptionsByOrgId("100", 0, 2);
    verify(subscriptionRepository).save(any());
  }

  @Test
  void shouldSyncSubscriptionsSkipSubIfTooExpired() {
    // When syncing an org's subs, don't sync subscriptions that expired more than 2 months ago
    var dto = createDto("456", 10);
    dto.setEffectiveStartDate(toEpochMillis(NOW.minusMonths(14)));
    dto.setEffectiveEndDate(toEpochMillis(NOW.minusMonths(2)));
    Mockito.when(subscriptionService.getSubscriptionsByOrgId(any(), anyInt(), anyInt()))
        .thenReturn(List.of(dto));

    subscriptionSyncController.syncSubscriptions("100", 0, 1);

    verify(subscriptionService).getSubscriptionsByOrgId("100", 0, 2);
    verifyNoInteractions(whitelist, offeringRepository, subscriptionRepository);
  }

  @Test
  void shouldSyncSubscriptionsSkipSubIfTooFutureDated() {
    // When syncing an org's subs, don't sync subscriptions future-dated more than 2 months from now
    var dto = createDto("456", 10);
    dto.setEffectiveStartDate(toEpochMillis(NOW.plusMonths(2).plusDays(1)));
    dto.setEffectiveEndDate(toEpochMillis(NOW.plusMonths(14).plusDays(1)));
    Mockito.when(subscriptionService.getSubscriptionById("456")).thenReturn(dto);

    Mockito.when(subscriptionService.getSubscriptionsByOrgId(any(), anyInt(), anyInt()))
        .thenReturn(List.of(dto));
    subscriptionSyncController.syncSubscriptions("100", 0, 1);

    verify(subscriptionService).getSubscriptionsByOrgId("100", 0, 2);
    verifyNoInteractions(whitelist, offeringRepository, subscriptionRepository);
  }

  @Test
  void shouldSyncSubscriptionsSkipSubIfNullDates() {
    // It is rare for effective dates from upstream to be null. It could mean a data issue upstream.
    // Because of this, consider the sub to be invalid, and don't sync its info with swatch.
    var dto = createDto("456", 10);
    dto.setEffectiveStartDate(null);
    dto.setEffectiveEndDate(null);
    Mockito.when(subscriptionService.getSubscriptionById("456")).thenReturn(dto);

    Mockito.when(subscriptionService.getSubscriptionsByOrgId(any(), anyInt(), anyInt()))
        .thenReturn(List.of(dto));
    subscriptionSyncController.syncSubscriptions("100", 0, 1);

    verify(subscriptionService).getSubscriptionsByOrgId("100", 0, 2);
    verifyNoInteractions(whitelist, offeringRepository, subscriptionRepository);
  }

  @Test
  void shouldEnqueueAllOrgsFromOrgConfigRepository() {
    Mockito.when(orgConfigRepository.findSyncEnabledOrgs())
        .thenReturn(IntStream.range(1, 10).mapToObj(String::valueOf));

    subscriptionSyncController.syncAllSubscriptionsForAllOrgs();

    verify(subscriptionsKafkaTemplate, times(9))
        .send(anyString(), any(SyncSubscriptionsTask.class));
  }

  @Test
  void shouldSaveSubscriptionToDatabaseAndReconcile() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    var subscription = createDto("123", 1);
    String subscriptionsJson =
        mapper.writeValueAsString(
            new org.candlepin.subscriptions.subscription.api.model.Subscription[] {subscription});
    subscriptionSyncController.saveSubscriptions(subscriptionsJson, true);
    verify(subscriptionRepository).save(any());
    verify(capacityReconciliationController).reconcileCapacityForSubscription(any());
  }

  @Test
  void shouldSaveSubscriptionToDatabaseWithoutReconcile() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    var subscription = createDto("123", 1);
    String subscriptionsJson =
        mapper.writeValueAsString(
            new org.candlepin.subscriptions.subscription.api.model.Subscription[] {subscription});
    subscriptionSyncController.saveSubscriptions(subscriptionsJson, false);
    verify(subscriptionRepository).save(any());
    verifyNoInteractions(capacityReconciliationController);
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
    dto.setEffectiveStartDate(toEpochMillis(NOW));
    dto.setEffectiveEndDate(toEpochMillis(NOW.plusDays(30)));
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
        .billingProviderId(SubscriptionDtoUtil.extractRhMarketplaceId(subscription))
        .billingProvider(SubscriptionDtoUtil.populateBillingProvider(subscription))
        .build();
  }

  private static Long toEpochMillis(OffsetDateTime offsetDateTime) {
    if (offsetDateTime == null) {
      return null;
    }
    return offsetDateTime.toEpochSecond() * 1000L;
  }
}
