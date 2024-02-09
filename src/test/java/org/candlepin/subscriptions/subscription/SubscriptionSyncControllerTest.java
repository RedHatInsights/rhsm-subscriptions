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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.BadRequestException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.capacity.files.ProductDenylist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.MissingOfferingException;
import org.candlepin.subscriptions.product.OfferingSyncController;
import org.candlepin.subscriptions.product.SyncResult;
import org.candlepin.subscriptions.subscription.api.model.ExternalReference;
import org.candlepin.subscriptions.subscription.api.model.SubscriptionProduct;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.candlepin.subscriptions.utilization.admin.api.model.OfferingProductTags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
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
  private static final String SKU = "testsku";
  private static final String BILLING_ACCOUNT_ID_ANY = "_ANY";
  private static final String PAYG_PRODUCT_NAME = "OpenShift Dedicated";

  @Autowired SubscriptionSyncController subscriptionSyncController;

  @Autowired private ApplicationClock clock;

  @MockBean ProductDenylist denylist;

  @MockBean OfferingRepository offeringRepository;

  @MockBean OfferingSyncController offeringSyncController;

  @MockBean SubscriptionRepository subscriptionRepository;

  @MockBean OrgConfigRepository orgConfigRepository;

  @MockBean CapacityReconciliationController capacityReconciliationController;

  @MockBean SubscriptionService subscriptionService;

  @MockBean KafkaTemplate<String, SyncSubscriptionsTask> subscriptionsKafkaTemplate;

  @Captor ArgumentCaptor<Iterable<Subscription>> subscriptionsCaptor;

  private OffsetDateTime rangeStart = OffsetDateTime.now().minusDays(5);
  private OffsetDateTime rangeEnd = OffsetDateTime.now().plusDays(5);

  @BeforeEach
  void setUp() {
    var offering =
        Offering.builder()
            .sku(SKU)
            .productName("RHEL")
            .productIds(new HashSet<>(List.of(68)))
            .build();
    Mockito.when(offeringRepository.getReferenceById(SKU)).thenReturn(offering);
  }

  @Test
  void shouldCreateNewRecordOnQuantityChange() {
    Mockito.when(offeringRepository.existsById(SKU)).thenReturn(true);
    Offering offering = Offering.builder().sku(SKU).build();
    when(offeringRepository.getReferenceById(SKU)).thenReturn(offering);
    when(denylist.productIdMatches(any())).thenReturn(false);
    var dto = createDto("456", 10);
    subscriptionSyncController.syncSubscription(dto, Optional.of(createSubscription()));
    verify(subscriptionRepository, Mockito.times(2)).save(Mockito.any(Subscription.class));
    verify(capacityReconciliationController, Mockito.times(2))
        .reconcileCapacityForSubscription(Mockito.any(Subscription.class));
  }

  @Test
  void shouldUpdateRecordOnNoQuantityChange() {
    Mockito.when(offeringRepository.existsById(SKU)).thenReturn(true);
    Offering offering = Offering.builder().sku(SKU).build();
    when(offeringRepository.getReferenceById(SKU)).thenReturn(offering);
    when(denylist.productIdMatches(any())).thenReturn(false);
    var dto = createDto("456", 4);
    subscriptionSyncController.syncSubscription(dto, Optional.of(createSubscription()));
    verify(subscriptionRepository, Mockito.times(1)).save(Mockito.any(Subscription.class));
    verify(capacityReconciliationController, Mockito.times(2))
        .reconcileCapacityForSubscription(Mockito.any(Subscription.class));
  }

  @Test
  void shouldCreateNewRecordOnNotFound() {
    Mockito.when(offeringRepository.existsById(SKU)).thenReturn(true);
    Offering offering = Offering.builder().sku(SKU).build();
    when(offeringRepository.getReferenceById(SKU)).thenReturn(offering);
    when(denylist.productIdMatches(any())).thenReturn(false);
    var dto = createDto("456", 10);
    subscriptionSyncController.syncSubscription(dto, Optional.empty());
    verify(subscriptionRepository, Mockito.times(1)).save(Mockito.any(Subscription.class));
    verify(capacityReconciliationController)
        .reconcileCapacityForSubscription(Mockito.any(Subscription.class));
  }

  @Test
  void shouldSkipSyncSubscriptionIfSkuIsOnDenyList() {
    String sku = "MW0001";
    when(denylist.productIdMatches(sku)).thenReturn(true);
    subscriptionSyncController.syncSubscription(sku, new Subscription(), Optional.empty());
    verify(subscriptionRepository, never()).save(any());
  }

  @Test
  void shouldSkipSyncSubscriptionIfSkuIsNotInOfferingRepository() {
    String sku = "MW0001";
    when(denylist.productIdMatches(sku)).thenReturn(false);
    when(offeringRepository.existsById(sku)).thenReturn(false);
    subscriptionSyncController.syncSubscription(sku, new Subscription(), Optional.empty());
    verify(subscriptionRepository, never()).save(any());
  }

  @Test
  void shouldUpdateSubscriptionWhenUpdateProductIds() {
    var dto = createDto(123, "456", 4);
    givenOfferingWithProductIds(290);
    subscriptionSyncController.syncSubscription(dto, Optional.empty());
    verify(subscriptionRepository).save(any());
    verify(capacityReconciliationController).reconcileCapacityForSubscription(any());

    // let's update only the product IDs
    givenOfferingWithProductIds(290, 69);
    reset(subscriptionRepository, capacityReconciliationController);
    subscriptionSyncController.syncSubscription(dto, Optional.empty());
    verify(subscriptionRepository).save(any());
    verify(capacityReconciliationController).reconcileCapacityForSubscription(any());
  }

  @Test
  void shouldSyncSubscriptionsSyncSubIfRecent() {
    when(denylist.productIdMatches(any())).thenReturn(false);
    Mockito.when(offeringRepository.existsById(any())).thenReturn(true);
    Mockito.when(offeringRepository.getReferenceById(SKU))
        .thenReturn(Offering.builder().sku(SKU).productName("RHEL").build());

    // When syncing an org's subs, a sub should be synced if it is within effective*Dates
    var dto = createDto("456", 10);
    dto.setEffectiveStartDate(toEpochMillis(NOW.minusMonths(6)));
    dto.setEffectiveEndDate(toEpochMillis(NOW.plusMonths(6)));
    Mockito.when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(dto));

    subscriptionSyncController.reconcileSubscriptionsWithSubscriptionService("100", false);

    verify(subscriptionService).getSubscriptionsByOrgId("100");
    verify(subscriptionRepository).save(any());
  }

  @Test
  void shouldSyncSubscriptionsSkipSubIfTooExpired() {
    // When syncing an org's subs, don't sync subscriptions that expired more than 2 months ago
    var dto = createDto("456", 10);
    dto.setEffectiveStartDate(toEpochMillis(NOW.minusMonths(14)));
    dto.setEffectiveEndDate(toEpochMillis(NOW.minusMonths(2)));
    Mockito.when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(dto));

    subscriptionSyncController.reconcileSubscriptionsWithSubscriptionService("100", false);

    verify(subscriptionService).getSubscriptionsByOrgId("100");
    verifyNoInteractions(denylist, offeringRepository);
    verify(subscriptionRepository, times(0)).save(any());
    verify(subscriptionRepository, times(0)).saveAll(any());
  }

  @Test
  void shouldSyncSubscriptionsSkipSubIfTooFutureDated() {
    // When syncing an org's subs, don't sync subscriptions future-dated more than 2 months from now
    var dto = createDto("456", 10);
    dto.setEffectiveStartDate(toEpochMillis(NOW.plusMonths(2).plusDays(1)));
    dto.setEffectiveEndDate(toEpochMillis(NOW.plusMonths(14).plusDays(1)));
    Mockito.when(subscriptionService.getSubscriptionById("456")).thenReturn(dto);

    Mockito.when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(dto));
    subscriptionSyncController.reconcileSubscriptionsWithSubscriptionService("100", false);

    verify(subscriptionService).getSubscriptionsByOrgId("100");
    verifyNoInteractions(denylist, offeringRepository);
    verify(subscriptionRepository, times(0)).save(any());
    verify(subscriptionRepository, times(0)).saveAll(any());
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
    subscriptionSyncController.reconcileSubscriptionsWithSubscriptionService("100", false);

    verify(subscriptionService).getSubscriptionsByOrgId("100");
    verifyNoInteractions(denylist, offeringRepository);
    verify(subscriptionRepository, times(0)).save(any());
    verify(subscriptionRepository, times(0)).saveAll(any());
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
    var offer = Offering.builder().sku("test").build();
    String subscriptionsJson =
        mapper.writeValueAsString(
            new org.candlepin.subscriptions.subscription.api.model.Subscription[] {subscription});
    when(offeringRepository.findOfferingBySku(any())).thenReturn(offer);
    subscriptionSyncController.saveSubscriptions(subscriptionsJson, true);
    verify(subscriptionRepository).save(any());
    verify(capacityReconciliationController).reconcileCapacityForSubscription(any());
  }

  @Test
  void shouldHandleSubscriptionWithoutOffering() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    var subscription = createDto("123", 1);
    String subscriptionsJson =
        mapper.writeValueAsString(
            new org.candlepin.subscriptions.subscription.api.model.Subscription[] {subscription});
    var thrown =
        assertThrows(
            BadRequestException.class,
            () -> {
              subscriptionSyncController.saveSubscriptions(subscriptionsJson, true);
            });
    assertEquals("Error offering doesn't exist", thrown.getMessage());
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

  @Test
  void shouldForceSubscriptionSyncForOrg() {
    var dto1 = createDto("234", 3);
    var dto2 = createDto("345", 3);
    var subList = Arrays.asList(dto1, dto2);
    when(subscriptionService.getSubscriptionsByOrgId("123")).thenReturn(subList);
    when(denylist.productIdMatches(any())).thenReturn(false);
    when(offeringRepository.existsById(any())).thenReturn(true);
    subscriptionSyncController.forceSyncSubscriptionsForOrg("123", false);
    verify(subscriptionRepository, times(2)).save(any());
  }

  @Test
  void shouldForceSubscriptionSyncForOrgWithSameIdButDifferentStartDates() {
    var dto1 = createDto("234", 3);
    var dto2 = createDto("234", 3);
    dto2.setEffectiveStartDate(dto1.getEffectiveStartDate() - (24 * 60 * 60 * 1000));

    var dtoList = Arrays.asList(dto1, dto2);
    var subList = dtoList.stream().map(this::convertDto).toList();
    subList.forEach(
        x -> {
          x.setQuantity(9999L);
          x.setOffering(new Offering());
        }); // Change the quantity so the sync will actually do something

    when(subscriptionService.getSubscriptionsByOrgId("123")).thenReturn(dtoList);
    when(subscriptionRepository.findByOrgId(anyString())).thenReturn(subList.stream());
    when(denylist.productIdMatches(any())).thenReturn(false);
    when(offeringRepository.existsById(any())).thenReturn(true);
    subscriptionSyncController.forceSyncSubscriptionsForOrg("123", false);
    verify(subscriptionRepository, times(4)).save(any());
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
    when(offeringRepository.existsById(any())).thenReturn(true);
    subscriptionSyncController.forceSyncSubscriptionsForOrg("123", true);
    verify(subscriptionRepository, atLeastOnce()).save(any());
  }

  @Test
  void shouldForceSubscriptionSyncForOrgWithExistingSubs() {
    var dto1 = createDto("234", 3);
    var dto2 = createDto("345", 3);
    var subList = Arrays.asList(dto1, dto2);

    var dao1 = convertDto(dto1);
    var offering1 = Offering.builder().sku(SubscriptionDtoUtil.extractSku(dto1)).build();
    dao1.setOffering(offering1);
    var dao2 = convertDto(dto2);
    var offering2 = Offering.builder().sku(SubscriptionDtoUtil.extractSku(dto2)).build();
    dao2.setOffering(offering2);

    when(subscriptionService.getSubscriptionsByOrgId("123")).thenReturn(subList);
    when(subscriptionRepository.findByOrgId("123")).thenReturn(Stream.of(dao1, dao2));
    subscriptionSyncController.forceSyncSubscriptionsForOrg("123", false);
    verify(subscriptionRepository).findByOrgId("123");
    verify(subscriptionRepository, never()).findActiveSubscription(any());
  }

  @Test
  void doesNotAllowReservedValuesInKey() {
    UsageCalculation.Key key1 =
        new Key(
            String.valueOf(1),
            ServiceLevel._ANY,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            BILLING_ACCOUNT_ID_ANY);
    UsageCalculation.Key key2 =
        new Key(
            String.valueOf(1),
            ServiceLevel.STANDARD,
            Usage._ANY,
            BillingProvider._ANY,
            BILLING_ACCOUNT_ID_ANY);
    Optional<String> orgId = Optional.of("org1000");

    assertThrows(
        IllegalArgumentException.class,
        () -> subscriptionSyncController.findSubscriptions(orgId, key1, rangeStart, rangeEnd));
    assertThrows(
        IllegalArgumentException.class,
        () -> subscriptionSyncController.findSubscriptions(orgId, key2, rangeStart, rangeEnd));
  }

  @Test
  void findsSubscriptionId_WhenOrgIdPresent() {
    UsageCalculation.Key key =
        new Key(
            "OpenShift-metrics",
            ServiceLevel.STANDARD,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "xyz");
    Subscription s = createSubscription("org123", "sku", "foo");
    s.getOffering().setProductName("OpenShift Container Platform");
    s.setStartDate(OffsetDateTime.now().minusDays(7));
    s.setEndDate(OffsetDateTime.now().plusDays(7));
    s.setBillingProvider(BillingProvider.RED_HAT);
    s.setBillingProviderId("xyz");
    List<Subscription> result = Collections.singletonList(s);

    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(result);

    List<Subscription> actual =
        subscriptionSyncController.findSubscriptions(
            Optional.of("org1000"), key, rangeStart, rangeEnd);
    assertEquals(1, actual.size());
    assertEquals("xyz", actual.get(0).getBillingProviderId());
  }

  @Test
  void findProductTagsBySku_WhenSkuPresent() {
    Offering offering = new Offering();
    offering.setRole("ocp");
    when(offeringRepository.findOfferingBySku("sku")).thenReturn(offering);

    OfferingProductTags productTags = subscriptionSyncController.findProductTags("sku");
    assertEquals(1, productTags.getData().size());
    assertEquals("OpenShift-metrics", productTags.getData().get(0));
  }

  @Test
  void
      findProductTagsBySku_WhenSkuPresentWithNoRoleOrEngIDsThenItShouldUseProductNameWhenMeteredFlagIsTrue() {
    Offering offering = new Offering();
    offering.setProductName("OpenShift Online");
    offering.setRole(null);
    offering.setProductIds(null);
    offering.setMetered(true);
    when(offeringRepository.findOfferingBySku("sku")).thenReturn(offering);

    OfferingProductTags productTags = subscriptionSyncController.findProductTags("sku");
    assertEquals(1, productTags.getData().size());
    assertEquals("rosa", productTags.getData().get(0));
  }

  @Test
  void
      findProductTagsBySku_WhenSkuPresentWithNoRoleOrEngIDsThenItShouldNotUseProductNameWhenMeteredFlagIsFalse() {
    Offering offering = new Offering();
    offering.setProductName("OpenShift Online");
    offering.setRole(null);
    offering.setProductIds(null);
    offering.setMetered(false);
    when(offeringRepository.findOfferingBySku("sku")).thenReturn(offering);

    OfferingProductTags productTags = subscriptionSyncController.findProductTags("sku");
    assertNull(productTags.getData());
  }

  @Test
  void findProductTagsBySku_WhenSkuNotPresent() {
    when(offeringRepository.findOfferingBySku("sku")).thenReturn(null);
    RuntimeException e =
        assertThrows(
            MissingOfferingException.class,
            () -> subscriptionSyncController.findProductTags("sku"));
    assertEquals("Sku sku not found in Offering", e.getMessage());

    when(offeringRepository.findOfferingBySku("sku")).thenReturn(new Offering());
    OfferingProductTags productTags2 = subscriptionSyncController.findProductTags("sku");
    assertNull(productTags2.getData());
  }

  @Test
  void terminateActivePAYGSubscriptionTest() {
    Subscription s = createSubscription();
    Offering o = new Offering();
    o.setProductName(PAYG_PRODUCT_NAME);
    o.setMetered(true);
    when(offeringRepository.findById(SKU)).thenReturn(Optional.of(o));
    when(subscriptionRepository.findActiveSubscription("456")).thenReturn(List.of(s));

    var termination = OffsetDateTime.now();
    var result = subscriptionSyncController.terminateSubscription("456", termination);
    assertThat(result, matchesPattern("Subscription 456 terminated at .*\\."));
    assertEquals(termination, s.getEndDate());
  }

  @Test
  void lateTerminateActivePAYGSubscriptionTest() {
    Subscription s = createSubscription();
    Offering offering = Offering.builder().productName(PAYG_PRODUCT_NAME).metered(true).build();
    s.setOffering(offering);
    when(offeringRepository.findById(SKU)).thenReturn(Optional.of(offering));
    when(subscriptionRepository.findActiveSubscription("456")).thenReturn(List.of(s));

    var termination = OffsetDateTime.now().minusDays(1);
    var result = subscriptionSyncController.terminateSubscription("456", termination);
    assertThat(
        result,
        matchesPattern("Subscription 456 terminated at .* with out of range termination date .*"));
    assertEquals(termination, s.getEndDate());
  }

  @Test
  void terminateInTheFutureActivePAYGSubscriptionTest() {
    Subscription s = createSubscription();
    s.getOffering().setProductName(PAYG_PRODUCT_NAME);
    s.getOffering().setMetered(true);
    when(subscriptionRepository.findActiveSubscription("456")).thenReturn(List.of(s));

    var termination = OffsetDateTime.now().plusDays(1);
    var result = subscriptionSyncController.terminateSubscription("456", termination);
    assertThat(
        result,
        matchesPattern("Subscription 456 terminated at .* with out of range termination date .*"));
    assertEquals(termination, s.getEndDate());
  }

  @Test
  void terminateActiveNonPAYGSubscriptionTest() {
    Subscription s = createSubscription();
    s.getOffering().setProductName("Random Product");
    when(subscriptionRepository.findActiveSubscription("456")).thenReturn(List.of(s));

    var termination = OffsetDateTime.now();
    var result = subscriptionSyncController.terminateSubscription("456", termination);
    assertThat(result, matchesPattern("Subscription 456 terminated at .*\\."));
    assertEquals(termination, s.getEndDate());
  }

  @Test
  void testSubscriptionEnrichedFromSubscriptionServiceWhenDbRecordAbsentAndSubscriptionIdMissing() {
    Subscription incoming = createConvertedDtoSubscription("123", null);
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
    when(offeringRepository.existsById(SKU)).thenReturn(true);

    Offering offering = Offering.builder().sku(SKU).build();
    when(offeringRepository.getReferenceById(SKU)).thenReturn(offering);
    when(subscriptionService.getSubscriptionBySubscriptionNumber("subnum"))
        .thenReturn(serviceResponse);

    subscriptionSyncController.syncSubscription(SKU, incoming, Optional.empty());
    verify(subscriptionService).getSubscriptionBySubscriptionNumber("subnum");
    assertEquals("billingAccountId", incoming.getBillingAccountId());
    assertEquals(BillingProvider.AWS, incoming.getBillingProvider());
    assertEquals("p;c;s", incoming.getBillingProviderId());
    assertEquals("456", incoming.getSubscriptionId());
  }

  @Test
  void testSubscriptionEnrichedFromDbWhenSubscriptionIdMissing() {
    Subscription incoming = createConvertedDtoSubscription("123", null);
    Subscription existing = createSubscription();
    existing.setBillingAccountId("billingAccountId");
    existing.setBillingProvider(BillingProvider.RED_HAT);
    existing.setBillingProviderId("billingProviderId");

    when(denylist.productIdMatches(any())).thenReturn(false);
    when(offeringRepository.existsById(SKU)).thenReturn(true);
    Offering offering = Offering.builder().sku(SKU).build();
    when(offeringRepository.getReferenceById(SKU)).thenReturn(offering);

    subscriptionSyncController.syncSubscription(SKU, incoming, Optional.of(existing));

    verifyNoInteractions(subscriptionService);
    assertEquals("billingAccountId", incoming.getBillingAccountId());
    assertEquals(BillingProvider.RED_HAT, incoming.getBillingProvider());
    assertEquals("billingProviderId", incoming.getBillingProviderId());
    assertEquals("456", incoming.getSubscriptionId());
  }

  @Test
  void testSubscriptionNotEnrichedWhenSubscriptionIdPresent() {
    Subscription incoming = createConvertedDtoSubscription("123", "456");
    Subscription existing = createSubscription();
    existing.setBillingAccountId("billingAccountId");

    when(denylist.productIdMatches(any())).thenReturn(false);
    when(offeringRepository.existsById(SKU)).thenReturn(true);

    subscriptionSyncController.syncSubscription(SKU, incoming, Optional.of(existing));

    verifyNoInteractions(subscriptionService);
    verify(subscriptionRepository).save(any());
    assertNull(incoming.getBillingAccountId());
  }

  @Test
  void testBillingFieldsUpdatedOnChange() {
    Subscription incoming = createConvertedDtoSubscription("123", "456");
    incoming.setBillingProvider(BillingProvider.RED_HAT);
    incoming.setBillingProviderId("newBillingProviderId");
    incoming.setBillingAccountId("newBillingAccountId");
    Subscription existing = createSubscription();
    existing.setBillingProvider(BillingProvider.AWS);
    existing.setBillingAccountId("oldBillingAccountId");
    existing.setBillingProviderId("oldBillinProviderId");

    when(denylist.productIdMatches(any())).thenReturn(false);
    when(offeringRepository.existsById(SKU)).thenReturn(true);
    Offering offering = Offering.builder().sku(SKU).build();
    when(offeringRepository.getReferenceById(SKU)).thenReturn(offering);

    subscriptionSyncController.syncSubscription(SKU, incoming, Optional.of(existing));

    verifyNoInteractions(subscriptionService);
    verify(subscriptionRepository).save(existing);
    assertEquals(BillingProvider.RED_HAT, existing.getBillingProvider());
    assertEquals("newBillingProviderId", existing.getBillingProviderId());
    assertEquals("newBillingAccountId", existing.getBillingAccountId());
  }

  @Test
  void testOfferingSyncedWhenMissing() {
    Subscription incoming = createConvertedDtoSubscription("123", "456");

    when(denylist.productIdMatches(any())).thenReturn(false);
    when(offeringRepository.existsById(SKU)).thenReturn(false);
    when(offeringSyncController.syncOffering(SKU)).thenReturn(SyncResult.SKIPPED_MATCHING);
    Offering offering = Offering.builder().sku(SKU).build();
    when(offeringRepository.getReferenceById(SKU)).thenReturn(offering);

    subscriptionSyncController.syncSubscription(SKU, incoming, Optional.empty());

    verify(offeringSyncController).syncOffering(SKU);
    verify(subscriptionRepository).save(incoming);
    assertNull(incoming.getBillingAccountId());
  }

  @Test
  void testOfferingSyncFailsAndProcessingStops() {
    Subscription incoming = createConvertedDtoSubscription("123", "456");

    when(denylist.productIdMatches(any())).thenReturn(false);
    when(offeringRepository.existsById(SKU)).thenReturn(false);
    when(offeringSyncController.syncOffering(SKU)).thenReturn(SyncResult.FAILED);
    Offering offering = Offering.builder().sku(SKU).build();
    when(offeringRepository.getReferenceById(SKU)).thenReturn(offering);

    subscriptionSyncController.syncSubscription(SKU, incoming, Optional.empty());

    verify(offeringSyncController).syncOffering(SKU);
    verify(subscriptionRepository, times(0)).save(incoming);
  }

  @Test
  void testShouldRemoveStaleSubscriptionsNotPresentInSubscriptionService() {
    var subscription = createSubscription();
    when(subscriptionRepository.findByOrgId(any())).thenReturn(Stream.of(subscription));
    subscriptionSyncController.reconcileSubscriptionsWithSubscriptionService("org123", false);
    verify(subscriptionRepository).deleteAll(subscriptionsCaptor.capture());
    assertThat(subscriptionsCaptor.getValue(), contains(subscription));
  }

  @Test
  void testShouldRemoveStaleSubscriptionsPresentInSubscriptionServiceButDenylisted() {
    var subscription = createSubscription();
    var subServiceSub = createDto("456", 1);
    when(subscriptionRepository.findByOrgId(any())).thenReturn(Stream.of(subscription));
    when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(subServiceSub));
    when(denylist.productIdMatches(any())).thenReturn(true);
    subscriptionSyncController.reconcileSubscriptionsWithSubscriptionService("org123", false);
    verify(subscriptionRepository).deleteAll(subscriptionsCaptor.capture());
    assertThat(subscriptionsCaptor.getValue(), contains(subscription));
  }

  @Test
  void testShouldNotRemovePresentSub() {
    var subscription = createSubscription();
    var subServiceSub = createDto("456", 1);
    subscription.setStartDate(clock.dateFromMilliseconds(subServiceSub.getEffectiveStartDate()));
    when(subscriptionRepository.findByOrgId(any())).thenReturn(Stream.of(subscription));
    when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(subServiceSub));
    when(denylist.productIdMatches(any())).thenReturn(false);
    subscriptionSyncController.reconcileSubscriptionsWithSubscriptionService("org123", false);
    verify(subscriptionRepository).deleteAll(subscriptionsCaptor.capture());
    assertFalse(subscriptionsCaptor.getValue().iterator().hasNext());
  }

  @Test
  void testShouldKeepRecordsWithSameIdAndDifferentStartDates() {
    var subscription1 = createSubscription();
    var subscription2 = createSubscription();
    subscription2.setEndDate(subscription1.getEndDate().plusDays(2));
    var subServiceSub = createDto("456", 1);
    subscription2.setStartDate(clock.dateFromMilliseconds(subServiceSub.getEffectiveStartDate()));
    when(subscriptionRepository.findByOrgId(any()))
        .thenReturn(Stream.of(subscription1, subscription2));
    when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(subServiceSub));
    when(denylist.productIdMatches(any())).thenReturn(false);
    subscriptionSyncController.reconcileSubscriptionsWithSubscriptionService("org123", false);
    verify(subscriptionRepository).deleteAll(subscriptionsCaptor.capture());
    assertFalse(subscriptionsCaptor.getValue().iterator().hasNext());
  }

  private Offering givenOfferingWithProductIds(Integer... productIds) {
    Offering offering = new Offering();
    offering.setSku(SKU);
    offering.setProductIds(Set.of(productIds));
    when(offeringRepository.getReferenceById(SKU)).thenReturn(offering);
    when(offeringSyncController.syncOffering(SKU)).thenReturn(SyncResult.FETCHED_AND_SYNCED);
    return offering;
  }

  private Subscription createSubscription() {
    return createSubscription("123", SKU, "456");
  }

  private Subscription createSubscriptionFrom(
      Offering offering, org.candlepin.subscriptions.subscription.api.model.Subscription dto) {
    return Subscription.builder()
        .subscriptionId("" + dto.getId())
        .subscriptionNumber(dto.getSubscriptionNumber())
        .orgId("" + dto.getWebCustomerId())
        .quantity(dto.getQuantity())
        .offering(offering)
        .startDate(clock.dateFromMilliseconds(dto.getEffectiveStartDate()))
        .endDate(clock.dateFromMilliseconds(dto.getEffectiveEndDate()))
        .build();
  }

  private Subscription createSubscription(String orgId, String sku, String subId) {
    Offering offering = Offering.builder().sku(sku).build();

    return Subscription.builder()
        .subscriptionId(subId)
        .orgId(orgId)
        .quantity(4L)
        .offering(offering)
        .startDate(NOW)
        .endDate(NOW.plusDays(30))
        .build();
  }

  /** Converted DTOs will not have an offering set */
  private Subscription createConvertedDtoSubscription(String orgId, String subId) {
    return Subscription.builder()
        .subscriptionId(subId)
        .orgId(orgId)
        .quantity(4L)
        .startDate(NOW)
        .endDate(NOW.plusDays(30))
        .build();
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

    var product = new SubscriptionProduct().parentSubscriptionProductId(null).sku(SKU);
    List<SubscriptionProduct> products = Collections.singletonList(product);
    dto.setSubscriptionProducts(products);

    return dto;
  }

  private org.candlepin.subscriptions.db.model.Subscription convertDto(
      org.candlepin.subscriptions.subscription.api.model.Subscription subscription) {

    return org.candlepin.subscriptions.db.model.Subscription.builder()
        .subscriptionId(String.valueOf(subscription.getId()))
        .orgId(subscription.getWebCustomerId().toString())
        .quantity(subscription.getQuantity())
        .startDate(clock.dateFromMilliseconds(subscription.getEffectiveStartDate()))
        .endDate(clock.dateFromMilliseconds(subscription.getEffectiveEndDate()))
        .billingProviderId(SubscriptionDtoUtil.extractBillingProviderId(subscription))
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
