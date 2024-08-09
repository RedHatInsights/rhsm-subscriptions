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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.clients.subscription.api.model.ExternalReference;
import com.redhat.swatch.clients.subscription.api.model.Subscription;
import com.redhat.swatch.clients.subscription.api.model.SubscriptionProduct;
import com.redhat.swatch.contract.config.ProductDenylist;
import com.redhat.swatch.contract.model.SyncResult;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.utils.SubscriptionDtoUtil;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

@QuarkusTest
class SubscriptionSyncServiceTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();
  private static final String SKU = "testsku";

  @InjectMock OfferingRepository offeringRepository;
  @InjectMock SubscriptionRepository subscriptionRepository;
  @InjectMock ProductDenylist denylist;
  @InjectMock SubscriptionService subscriptionService;
  @InjectMock CapacityReconciliationService capacityReconciliationService;
  @InjectMock OfferingSyncService offeringSyncService;
  @Inject ApplicationClock clock;
  @Inject SubscriptionSyncService subscriptionSyncService;

  @Test
  void shouldCreateNewRecordOnQuantityChange() {
    OfferingEntity offering = OfferingEntity.builder().sku(SKU).build();
    Mockito.when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.of(offering));
    when(offeringRepository.findById(SKU)).thenReturn(offering);
    var dto = createDto("456", 10);
    var subscription = createSubscription();

    when(denylist.productIdMatches(any())).thenReturn(false);
    subscriptionSyncService.syncSubscription(dto, Optional.of(subscription));
    // for existing subscription:
    verify(subscriptionRepository).persist(any(SubscriptionEntity.class));
    // for the new one:
    verify(subscriptionRepository).merge(any(SubscriptionEntity.class));
    verify(capacityReconciliationService, Mockito.times(2))
        .reconcileCapacityForSubscription(Mockito.any(SubscriptionEntity.class));
  }

  /** Test for SWATCH-2579 */
  @Test
  void shouldSegmentSubscriptionOnQuantityChangeOnlyOnce() {
    var initialSub = createSubscription();
    // Change quantity from 4 to 10
    var dto = createDto("456", 10);

    var aWeekAgo = toEpochMillis(NOW.minusDays(7L));
    var twoDaysAgo = NOW.minusDays(2L);
    dto.setEffectiveStartDate(aWeekAgo);
    initialSub.setStartDate(clock.dateFromMilliseconds(aWeekAgo));
    initialSub.setEndDate(twoDaysAgo);

    // Simulate the brief delay between when a subscription is terminated and when the
    // continuation subscription is written to the database.  In reality, this should be just a
    // millisecond or less
    var continuationStartTime = twoDaysAgo.plusSeconds(1L);

    // This simulates a subscription that has been segmented due to quantity change
    var quantityChangedSub =
        SubscriptionEntity.builder()
            .subscriptionId(initialSub.getSubscriptionId())
            .subscriptionNumber(initialSub.getSubscriptionNumber())
            .orgId(initialSub.getOrgId())
            .quantity(10) // same as DTO quantity
            .offering(initialSub.getOffering())
            .startDate(continuationStartTime)
            .endDate(initialSub.getEndDate())
            .build();

    // Order the subscriptions by start date descending as the database does
    var initialSubSpy = spy(initialSub);
    var subscriptions = List.of(quantityChangedSub, initialSubSpy);

    // Subscription watch should not perform any termination since the quantity has not changed
    // from the value in the quantityChangedSub.
    when(denylist.productIdMatches(any())).thenReturn(false);
    when(subscriptionService.getSubscriptionsByOrgId("123")).thenReturn(List.of(dto));
    when(subscriptionRepository.streamByOrgId("123")).thenReturn(subscriptions.stream());
    subscriptionSyncService.reconcileSubscriptionsWithSubscriptionService("123", false);

    verify(initialSubSpy, never()).endSubscription();
  }

  @Test
  void shouldHandleMultipleQuantityChanges() {
    var initialSub = createSubscription();
    // Change quantity from 4 to 10
    var dto = createDto("456", 10);

    var aWeekAgo = toEpochMillis(NOW.minusDays(7L));
    var twoDaysAgo = NOW.minusDays(2L);
    dto.setEffectiveStartDate(aWeekAgo);
    initialSub.setStartDate(clock.dateFromMilliseconds(aWeekAgo));
    initialSub.setEndDate(twoDaysAgo);

    // Simulate the brief delay between when a subscription is terminated and when the
    // continuation subscription is written to the database.  In reality, this should be just a
    // millisecond or less
    var firstContinuationStartTime = twoDaysAgo.plusSeconds(1L);

    var firstQuantityChangedSub =
        SubscriptionEntity.builder()
            .subscriptionId(initialSub.getSubscriptionId())
            .subscriptionNumber(initialSub.getSubscriptionNumber())
            .orgId(initialSub.getOrgId())
            .quantity(10) // same as DTO quantity
            .offering(initialSub.getOffering())
            .startDate(firstContinuationStartTime)
            .endDate(initialSub.getEndDate())
            .build();

    // Order the subscriptions by start date descending as the database does
    var initialSubSpy = spy(initialSub);
    var firstQuantityChangedSubSpy = spy(firstQuantityChangedSub);
    var subscriptions = List.of(firstQuantityChangedSubSpy, initialSubSpy);

    // Change the quantity AGAIN
    dto.setQuantity(20);

    // Subscription watch should terminate firstQuantityChangedSub and create a new segment since
    // the quantity has changed for a second time
    when(denylist.productIdMatches(any())).thenReturn(false);
    when(subscriptionService.getSubscriptionsByOrgId("123")).thenReturn(List.of(dto));
    when(subscriptionRepository.streamByOrgId("123")).thenReturn(subscriptions.stream());
    subscriptionSyncService.reconcileSubscriptionsWithSubscriptionService("123", false);

    // We don't want to modify the original subscription that has already been ended.  We want to
    // end the current operative subscription and create a new segment.
    verify(initialSubSpy, never()).endSubscription();
    verify(firstQuantityChangedSubSpy, times(1)).endSubscription();
  }

  @Test
  void shouldUpdateRecordOnNoQuantityChange() {
    OfferingEntity offering = OfferingEntity.builder().sku(SKU).build();
    Mockito.when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.of(offering));
    when(offeringRepository.findById(SKU)).thenReturn(offering);
    when(denylist.productIdMatches(any())).thenReturn(false);
    var dto = createDto("456", 4);
    subscriptionSyncService.syncSubscription(dto, Optional.of(createSubscription()));
    verify(subscriptionRepository).persist(any(SubscriptionEntity.class));
    verify(capacityReconciliationService, Mockito.times(2))
        .reconcileCapacityForSubscription(any(SubscriptionEntity.class));
  }

  @Test
  void shouldCreateNewRecordOnNotFound() {
    OfferingEntity offering = OfferingEntity.builder().sku(SKU).build();
    Mockito.when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.of(offering));
    when(offeringRepository.findById(SKU)).thenReturn(offering);
    when(denylist.productIdMatches(any())).thenReturn(false);
    var dto = createDto("456", 10);
    subscriptionSyncService.syncSubscription(dto, Optional.empty());
    verify(subscriptionRepository, Mockito.times(1)).merge(any(SubscriptionEntity.class));
    verify(capacityReconciliationService)
        .reconcileCapacityForSubscription(any(SubscriptionEntity.class));
  }

  @Test
  void shouldSkipSyncSubscriptionIfSkuIsOnDenyList() {
    String sku = "MW0001";
    when(denylist.productIdMatches(sku)).thenReturn(true);
    subscriptionSyncService.syncSubscription(sku, new SubscriptionEntity(), Optional.empty());
    verify(subscriptionRepository, never()).persist(any(SubscriptionEntity.class));
  }

  @Test
  void shouldSkipSyncSubscriptionIfSkuIsNotInOfferingRepository() {
    String sku = "MW0001";
    when(denylist.productIdMatches(sku)).thenReturn(false);
    when(offeringRepository.findByIdOptional(sku)).thenReturn(Optional.empty());
    when(offeringSyncService.syncOffering(sku)).thenReturn(SyncResult.SKIPPED_NOT_FOUND);
    subscriptionSyncService.syncSubscription(sku, new SubscriptionEntity(), Optional.empty());
    verify(subscriptionRepository, never()).persist(any(SubscriptionEntity.class));
  }

  @Test
  void shouldUpdateSubscriptionRecordForMeteredOffering() {
    OfferingEntity offering = OfferingEntity.builder().sku(SKU).metered(true).build();
    when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.of(offering));
    when(offeringRepository.findById(SKU)).thenReturn(offering);
    when(denylist.productIdMatches(any())).thenReturn(false);
    var existingSubscription = createSubscription();
    existingSubscription.setBillingProvider(BillingProvider.AZURE);
    existingSubscription.setBillingProviderId("testProviderId");
    existingSubscription.setBillingAccountId("testAccountId");
    existingSubscription.setQuantity(10);
    var dto = createDto("456", 10);
    subscriptionSyncService.syncSubscription(dto, Optional.of(existingSubscription));
    verify(subscriptionRepository).persist(any(SubscriptionEntity.class));
    verify(capacityReconciliationService, Mockito.times(2))
        .reconcileCapacityForSubscription(any(SubscriptionEntity.class));
  }

  @Test
  void shouldSkipSyncIfMeteredOfferingSubscriptionNotAlreadyCreatedByContractService() {
    OfferingEntity offering = OfferingEntity.builder().sku(SKU).metered(true).build();
    when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.of(offering));
    when(offeringRepository.findById(SKU)).thenReturn(offering);
    when(denylist.productIdMatches(any())).thenReturn(false);
    var dto = createDto("456", 10);
    subscriptionSyncService.syncSubscription(dto, Optional.empty());
    verify(subscriptionRepository, never()).persist(any(SubscriptionEntity.class));
  }

  @Test
  void shouldUpdateSubscriptionWhenUpdateProductIds() {
    var dto = createDto(123, "456", "890", 4);
    givenOfferingWithProductIds(290);
    subscriptionSyncService.syncSubscription(dto, Optional.empty());
    verify(subscriptionRepository).merge(any(SubscriptionEntity.class));
    verify(capacityReconciliationService).reconcileCapacityForSubscription(any());

    // let's update only the product IDs
    givenOfferingWithProductIds(290, 69);
    reset(subscriptionRepository, capacityReconciliationService);
    subscriptionSyncService.syncSubscription(dto, Optional.empty());
    verify(subscriptionRepository).merge(any(SubscriptionEntity.class));
    verify(capacityReconciliationService).reconcileCapacityForSubscription(any());
  }

  @Test
  void shouldSyncSubscriptionsSyncSubIfRecent() {
    when(denylist.productIdMatches(any())).thenReturn(false);
    OfferingEntity offering = OfferingEntity.builder().sku(SKU).build();
    when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.of(offering));
    when(offeringRepository.findById(SKU)).thenReturn(offering);

    // When syncing an org's subs, a sub should be synced if it is within effective*Dates
    var dto = createDto("456", 10);
    dto.setEffectiveStartDate(toEpochMillis(NOW.minusMonths(6)));
    dto.setEffectiveEndDate(toEpochMillis(NOW.plusMonths(6)));
    Mockito.when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(dto));

    subscriptionSyncService.reconcileSubscriptionsWithSubscriptionService("100", false);

    verify(subscriptionService).getSubscriptionsByOrgId("100");
    verify(subscriptionRepository).merge(any(SubscriptionEntity.class));
  }

  @Test
  void shouldSyncSubscriptionsSkipSubIfTooExpired() {
    // When syncing an org's subs, don't sync subscriptions that expired more than 2 months ago
    var dto = createDto("456", 10);
    dto.setEffectiveStartDate(toEpochMillis(NOW.minusMonths(14)));
    dto.setEffectiveEndDate(toEpochMillis(NOW.minusMonths(2)));
    Mockito.when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(dto));

    subscriptionSyncService.reconcileSubscriptionsWithSubscriptionService("100", false);

    verify(subscriptionService).getSubscriptionsByOrgId("100");
    verifyNoInteractions(denylist, offeringRepository);
    verify(subscriptionRepository, times(0)).persist(any(SubscriptionEntity.class));
  }

  @Test
  void shouldSyncSubscriptionsSkipSubIfTooFutureDated() {
    // When syncing an org's subs, don't sync subscriptions future-dated more than 2 months from now
    var dto = createDto("456", 10);
    dto.setEffectiveStartDate(toEpochMillis(NOW.plusMonths(2).plusDays(1)));
    dto.setEffectiveEndDate(toEpochMillis(NOW.plusMonths(14).plusDays(1)));
    Mockito.when(subscriptionService.getSubscriptionById("456")).thenReturn(dto);

    Mockito.when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(dto));
    subscriptionSyncService.reconcileSubscriptionsWithSubscriptionService("100", false);

    verify(subscriptionService).getSubscriptionsByOrgId("100");
    verifyNoInteractions(denylist, offeringRepository);
    verify(subscriptionRepository, times(0)).persist(any(SubscriptionEntity.class));
  }

  @Test
  void shouldSyncSubscriptionsSkipSubIfNullDates() {
    // It is rare for effective dates from upstream to be null. It could mean a data issue upstream.
    // Because of this, consider the sub to be invalid, and don't sync its info with swatch.
    var dto = createDto("456", 10);
    dto.setEffectiveStartDate(null);
    dto.setEffectiveEndDate(null);
    Mockito.when(subscriptionService.getSubscriptionById("456")).thenReturn(dto);

    Mockito.when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(dto));
    subscriptionSyncService.reconcileSubscriptionsWithSubscriptionService("100", false);

    verify(subscriptionService).getSubscriptionsByOrgId("100");
    verifyNoInteractions(denylist, offeringRepository);
    verify(subscriptionRepository, times(0)).persist(any(SubscriptionEntity.class));
  }

  @Test
  void shouldUpdateSubscriptionForMatchingSubscriptionNumber() {
    when(denylist.productIdMatches(any())).thenReturn(false);
    OfferingEntity offering = OfferingEntity.builder().sku(SKU).build();
    when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.of(offering));
    when(offeringRepository.findById(SKU)).thenReturn(offering);

    var dto = createDto("456", 10);
    Mockito.when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(dto));
    var existingSub = this.convertDto(dto);
    // Contract provided subscription will have different start time and should be updated
    existingSub.setStartDate(existingSub.getStartDate().plusHours(3));
    dto.setExternalReferences(
        Map.of(
            SubscriptionDtoUtil.AWS_MARKETPLACE,
            new ExternalReference().customerAccountID("new1BillingAccountId")));
    Mockito.when(subscriptionRepository.findBySubscriptionNumber(dto.getSubscriptionNumber()))
        .thenReturn(List.of(existingSub));
    subscriptionSyncService.reconcileSubscriptionsWithSubscriptionService("100", false);
    verify(subscriptionService).getSubscriptionsByOrgId("100");
    verify(subscriptionRepository)
        .persist(
            argThat(
                (ArgumentMatcher<SubscriptionEntity>)
                    s ->
                        s.getStartDate().equals(existingSub.getStartDate())
                            && "new1BillingAccountId".equals(s.getBillingAccountId())));
  }

  @Test
  void shouldSaveSubscriptionToDatabaseAndReconcile() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    var subscription = createDto("123", 1);
    OfferingEntity offering = OfferingEntity.builder().sku(SKU).build();
    when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.of(offering));
    when(offeringRepository.findById(SKU)).thenReturn(offering);
    String subscriptionsJson = mapper.writeValueAsString(new Subscription[] {subscription});
    subscriptionSyncService.saveSubscriptions(subscriptionsJson, true);
    verify(subscriptionRepository).persist(any(SubscriptionEntity.class));
    verify(capacityReconciliationService).reconcileCapacityForSubscription(any());
  }

  @Test
  void shouldHandleSubscriptionWithoutOffering() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    var subscription = createDto("123", 1);
    String subscriptionsJson = mapper.writeValueAsString(new Subscription[] {subscription});
    var thrown =
        assertThrows(
            BadRequestException.class,
            () -> {
              subscriptionSyncService.saveSubscriptions(subscriptionsJson, true);
            });
    assertEquals("Error offering doesn't exist", thrown.getMessage());
  }

  @Test
  void shouldSaveSubscriptionToDatabaseWithoutReconcile() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    var subscription = createDto("123", 1);
    String subscriptionsJson = mapper.writeValueAsString(new Subscription[] {subscription});
    subscriptionSyncService.saveSubscriptions(subscriptionsJson, false);
    verify(subscriptionRepository).persist(any(SubscriptionEntity.class));
    verifyNoInteractions(capacityReconciliationService);
  }

  @Test
  void shouldForceSubscriptionSyncForOrg() {
    var dto1 = createDto("234", 3);
    var dto2 = createDto("345", 3);
    var subList = Arrays.asList(dto1, dto2);
    when(subscriptionService.getSubscriptionsByOrgId("123")).thenReturn(subList);
    when(denylist.productIdMatches(any())).thenReturn(false);

    OfferingEntity offering = OfferingEntity.builder().sku(SKU).build();
    when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.of(offering));
    when(offeringRepository.findById(SKU)).thenReturn(offering);
    subscriptionSyncService.forceSyncSubscriptionsForOrg("123", false);
    verify(subscriptionRepository, times(2)).merge(any(SubscriptionEntity.class));
  }

  @Test
  void shouldForcePAYGSubscriptionsOnlySyncForOrg() {
    var dto1 = createDto("234", 3);
    var dto2 = createDto("345", 3);
    var externalReferences = new HashMap<String, ExternalReference>();
    var externalReference = new ExternalReference();
    externalReference.setSubscriptionID("testBillingProvider");
    externalReferences.put(SubscriptionDtoUtil.IBMMARKETPLACE, externalReference);
    dto2.setExternalReferences(externalReferences);
    var subList = Arrays.asList(dto1, dto2);
    when(subscriptionService.getSubscriptionsByOrgId("123")).thenReturn(subList);
    when(denylist.productIdMatches(any())).thenReturn(false);
    OfferingEntity offering = OfferingEntity.builder().sku(SKU).build();
    when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.of(offering));
    when(offeringRepository.findById(SKU)).thenReturn(offering);
    subscriptionSyncService.forceSyncSubscriptionsForOrg("123", true);
    verify(subscriptionRepository, atLeastOnce()).merge(any(SubscriptionEntity.class));
  }

  @Test
  void shouldForceSubscriptionSyncForOrgWithExistingSubs() {
    var dto1 = createDto("234", 3);
    var dto2 = createDto("345", 3);
    var subList = Arrays.asList(dto1, dto2);

    var dao1 = convertDto(dto1);
    var offering1 = OfferingEntity.builder().sku(SubscriptionDtoUtil.extractSku(dto1)).build();
    dao1.setOffering(offering1);
    var dao2 = convertDto(dto2);
    var offering2 = OfferingEntity.builder().sku(SubscriptionDtoUtil.extractSku(dto2)).build();
    dao2.setOffering(offering2);

    when(subscriptionService.getSubscriptionsByOrgId("123")).thenReturn(subList);
    when(subscriptionRepository.stream("123")).thenReturn(Stream.of(dao1, dao2));
    subscriptionSyncService.forceSyncSubscriptionsForOrg("123", false);
    verify(subscriptionRepository).streamByOrgId("123");
  }

  @Test
  void testSubscriptionEnrichedFromSubscriptionServiceWhenDbRecordAbsentAndSubscriptionIdMissing() {
    SubscriptionEntity incoming = createConvertedDtoSubscription("123", null, null);
    incoming.setSubscriptionNumber("subnum");
    var serviceResponse = createDto("456", 1);
    serviceResponse.setExternalReferences(
        Map.of(
            SubscriptionDtoUtil.AWS_MARKETPLACE,
            new ExternalReference()
                .customerAccountID("billingAccountId")
                .productCode("p")
                .customerID("c")
                .sellerAccount("s")));

    when(denylist.productIdMatches(any())).thenReturn(false);
    OfferingEntity offering = OfferingEntity.builder().sku(SKU).build();
    when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.of(offering));
    when(offeringRepository.findById(SKU)).thenReturn(offering);
    when(subscriptionService.getSubscriptionBySubscriptionNumber("subnum"))
        .thenReturn(serviceResponse);

    subscriptionSyncService.syncSubscription(SKU, incoming, Optional.empty());
    verify(subscriptionService).getSubscriptionBySubscriptionNumber("subnum");
    assertEquals("billingAccountId", incoming.getBillingAccountId());
    assertEquals(BillingProvider.AWS, incoming.getBillingProvider());
    assertEquals("p;c;s", incoming.getBillingProviderId());
    assertEquals("456", incoming.getSubscriptionId());
  }

  @Test
  void testSubscriptionEnrichedFromDbWhenSubscriptionIdMissing() {
    SubscriptionEntity incoming = createConvertedDtoSubscription("123", null, null);
    SubscriptionEntity existing = createSubscription();
    existing.setBillingAccountId("billingAccountId");
    existing.setBillingProvider(BillingProvider.RED_HAT);
    existing.setBillingProviderId("billingProviderId");

    when(denylist.productIdMatches(any())).thenReturn(false);
    OfferingEntity offering = OfferingEntity.builder().sku(SKU).build();
    when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.of(offering));
    when(offeringRepository.findById(SKU)).thenReturn(offering);

    subscriptionSyncService.syncSubscription(SKU, incoming, Optional.of(existing));

    verifyNoInteractions(subscriptionService);
    assertEquals("billingAccountId", incoming.getBillingAccountId());
    assertEquals(BillingProvider.RED_HAT, incoming.getBillingProvider());
    assertEquals("billingProviderId", incoming.getBillingProviderId());
    assertEquals("456", incoming.getSubscriptionId());
  }

  @Test
  void testSubscriptionNotEnrichedWhenSubscriptionIdPresent() {
    SubscriptionEntity incoming = createConvertedDtoSubscription("123", "456", "890");
    SubscriptionEntity existing = createSubscription();
    existing.setBillingAccountId("billingAccountId");

    when(denylist.productIdMatches(any())).thenReturn(false);
    OfferingEntity offering = OfferingEntity.builder().sku(SKU).build();
    when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.of(offering));
    when(offeringRepository.findById(SKU)).thenReturn(offering);

    subscriptionSyncService.syncSubscription(SKU, incoming, Optional.of(existing));

    verifyNoInteractions(subscriptionService);
    verify(subscriptionRepository).persist(any(SubscriptionEntity.class));
    assertNull(incoming.getBillingAccountId());
  }

  @Test
  void testBillingFieldsUpdatedOnChange() {
    SubscriptionEntity incoming = createConvertedDtoSubscription("123", "456", "890");
    incoming.setBillingProvider(BillingProvider.RED_HAT);
    incoming.setBillingProviderId("newBillingProviderId");
    incoming.setBillingAccountId("newBillingAccountId");
    SubscriptionEntity existing = createSubscription();
    existing.setBillingProvider(BillingProvider.AWS);
    existing.setBillingAccountId("oldBillingAccountId");
    existing.setBillingProviderId("oldBillinProviderId");

    when(denylist.productIdMatches(any())).thenReturn(false);
    OfferingEntity offering = OfferingEntity.builder().sku(SKU).build();
    when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.of(offering));
    when(offeringRepository.findById(SKU)).thenReturn(offering);

    subscriptionSyncService.syncSubscription(SKU, incoming, Optional.of(existing));

    verifyNoInteractions(subscriptionService);
    verify(subscriptionRepository).persist(existing);
    assertEquals(BillingProvider.RED_HAT, existing.getBillingProvider());
    assertEquals("newBillingProviderId", existing.getBillingProviderId());
    assertEquals("newBillingAccountId", existing.getBillingAccountId());
  }

  @Test
  void testOfferingSyncedWhenMissing() {
    SubscriptionEntity incoming = createConvertedDtoSubscription("123", "456", "890");

    when(denylist.productIdMatches(any())).thenReturn(false);
    when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.empty());
    when(offeringSyncService.syncOffering(SKU)).thenReturn(SyncResult.FETCHED_AND_SYNCED);
    OfferingEntity offering = OfferingEntity.builder().sku(SKU).build();
    when(offeringRepository.findById(SKU)).thenReturn(offering);

    subscriptionSyncService.syncSubscription(SKU, incoming, Optional.empty());

    verify(offeringSyncService).syncOffering(SKU);
    verify(subscriptionRepository).merge(incoming);
    assertNull(incoming.getBillingAccountId());
  }

  @Test
  void testOfferingSyncFailsAndProcessingStops() {
    SubscriptionEntity incoming = createConvertedDtoSubscription("123", "456", "890");

    when(denylist.productIdMatches(any())).thenReturn(false);
    when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.empty());
    when(offeringSyncService.syncOffering(SKU)).thenReturn(SyncResult.FAILED);
    OfferingEntity offering = OfferingEntity.builder().sku(SKU).build();
    when(offeringRepository.findById(SKU)).thenReturn(offering);

    subscriptionSyncService.syncSubscription(SKU, incoming, Optional.empty());

    verify(offeringSyncService).syncOffering(SKU);
    verify(subscriptionRepository, times(0)).persist(incoming);
  }

  @Test
  void testShouldRemoveStaleSubscriptionsNotPresentInSubscriptionService() {
    var subscription = createSubscription();
    when(subscriptionRepository.streamByOrgId(any())).thenReturn(Stream.of(subscription));
    subscriptionSyncService.reconcileSubscriptionsWithSubscriptionService("org123", false);
    verify(subscriptionRepository).delete(subscription);
  }

  @Test
  void testShouldRemoveStaleSubscriptionsPresentInSubscriptionServiceButDenylisted() {
    var subscription = createSubscription();
    var subServiceSub = createDto("456", 1);
    when(subscriptionRepository.streamByOrgId(any())).thenReturn(Stream.of(subscription));
    when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(subServiceSub));
    when(denylist.productIdMatches(any())).thenReturn(true);
    subscriptionSyncService.reconcileSubscriptionsWithSubscriptionService("org123", false);
    verify(subscriptionRepository).delete(subscription);
  }

  @Test
  void testShouldNotRemovePresentSub() {
    var subscription = createSubscription();
    var subServiceSub = createDto("456", 1);
    subscription.setStartDate(clock.dateFromMilliseconds(subServiceSub.getEffectiveStartDate()));
    when(subscriptionRepository.streamByOrgId(any())).thenReturn(Stream.of(subscription));
    when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(subServiceSub));
    when(denylist.productIdMatches(any())).thenReturn(false);
    subscriptionSyncService.reconcileSubscriptionsWithSubscriptionService("org124", false);
    verify(subscriptionRepository, times(0)).delete(subscription);
  }

  @Test
  void testShouldKeepRecordsWithSameIdAndDifferentStartDates() {
    var subscription1 = createSubscription();
    var subscription2 = createSubscription();
    subscription2.setEndDate(subscription1.getEndDate().plusDays(2));
    var subServiceSub = createDto("456", 1);
    subscription2.setStartDate(clock.dateFromMilliseconds(subServiceSub.getEffectiveStartDate()));
    when(subscriptionRepository.streamByOrgId(any()))
        .thenReturn(Stream.of(subscription1, subscription2));
    when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(subServiceSub));
    when(denylist.productIdMatches(any())).thenReturn(false);
    subscriptionSyncService.reconcileSubscriptionsWithSubscriptionService("org123", false);
    verify(subscriptionRepository, times(0)).delete(any());
  }

  @Test
  void shouldMaintainAzureBillingInfoDuringSync() {
    OfferingEntity offering = OfferingEntity.builder().sku(SKU).metered(true).build();
    when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.of(offering));
    when(offeringRepository.findById(SKU)).thenReturn(offering);
    when(denylist.productIdMatches(any())).thenReturn(false);
    var existingSubscription = createSubscription();
    existingSubscription.setBillingProvider(BillingProvider.AZURE);
    existingSubscription.setBillingProviderId("testProviderId");
    existingSubscription.setBillingAccountId("testAccountId");
    existingSubscription.setQuantity(10);
    var dto = createDto("456", 10);
    subscriptionSyncService.syncSubscription(dto, Optional.of(existingSubscription));
    verify(subscriptionRepository)
        .persist(
            argThat(
                (ArgumentMatcher<SubscriptionEntity>)
                    s ->
                        "testAccountId".equals(s.getBillingAccountId())
                            && "testProviderId".equals(s.getBillingProviderId())
                            && BillingProvider.AZURE.equals(s.getBillingProvider())));
  }

  private OfferingEntity givenOfferingWithProductIds(Integer... productIds) {
    OfferingEntity offering = new OfferingEntity();
    offering.setSku(SKU);
    offering.setProductIds(Set.of(productIds));
    when(offeringRepository.findByIdOptional(SKU)).thenReturn(Optional.of(offering));
    when(offeringRepository.findById(SKU)).thenReturn(offering);
    when(offeringSyncService.syncOffering(SKU)).thenReturn(SyncResult.FETCHED_AND_SYNCED);
    return offering;
  }

  private SubscriptionEntity createSubscription() {
    return createSubscription("123", SKU, "456", "890");
  }

  private SubscriptionEntity createSubscription(
      String orgId, String sku, String subId, String subNum) {
    OfferingEntity offering = OfferingEntity.builder().sku(sku).build();

    return SubscriptionEntity.builder()
        .subscriptionId(subId)
        .subscriptionNumber(subNum)
        .orgId(orgId)
        .quantity(4L)
        .offering(offering)
        .startDate(NOW)
        .endDate(NOW.plusDays(30))
        .build();
  }

  private OffsetDateTime toMillisecondPrecision(OffsetDateTime time) {
    return clock.dateFromMilliseconds(toEpochMillis(time));
  }

  /** Converted DTOs will not have an offering set */
  private SubscriptionEntity createConvertedDtoSubscription(
      String orgId, String subId, String subNum) {
    return SubscriptionEntity.builder()
        .subscriptionId(subId)
        .subscriptionNumber(subNum)
        .orgId(orgId)
        .quantity(4L)
        // Converted DTOs will only ever have millisecond precision because that's what we get from
        // the subscription service
        .startDate(toMillisecondPrecision(NOW))
        .endDate(toMillisecondPrecision(NOW.plusDays(30)))
        .build();
  }

  private Subscription createDto(String subId, int quantity) {

    return createDto(123, subId, "890", quantity);
  }

  private Subscription createDto(Integer orgId, String subId, String subNum, int quantity) {
    final var dto = new Subscription();
    dto.setQuantity(quantity);
    dto.setId(Integer.valueOf(subId));
    dto.setSubscriptionNumber(subNum);
    dto.setEffectiveStartDate(toEpochMillis(NOW));
    dto.setEffectiveEndDate(toEpochMillis(NOW.plusDays(30)));
    dto.setWebCustomerId(orgId);

    var product = new SubscriptionProduct().parentSubscriptionProductId(null).sku(SKU);
    List<SubscriptionProduct> products = Collections.singletonList(product);
    dto.setSubscriptionProducts(products);

    return dto;
  }

  private SubscriptionEntity convertDto(Subscription subscription) {
    OfferingEntity offering = OfferingEntity.builder().metered(true).build();

    return SubscriptionEntity.builder()
        .subscriptionId(String.valueOf(subscription.getId()))
        .orgId(subscription.getWebCustomerId().toString())
        .quantity(subscription.getQuantity())
        .startDate(clock.dateFromMilliseconds(subscription.getEffectiveStartDate()))
        .endDate(clock.dateFromMilliseconds(subscription.getEffectiveEndDate()))
        .billingProviderId(SubscriptionDtoUtil.extractBillingProviderId(subscription))
        .billingProvider(SubscriptionDtoUtil.populateBillingProvider(subscription))
        .offering(offering)
        .build();
  }

  private static Long toEpochMillis(OffsetDateTime offsetDateTime) {
    if (offsetDateTime == null) {
      return null;
    }
    return offsetDateTime.toEpochSecond() * 1000L;
  }
}
