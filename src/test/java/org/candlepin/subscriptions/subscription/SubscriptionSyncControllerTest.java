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
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.capacity.files.ProductDenylist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.MissingOfferingException;
import org.candlepin.subscriptions.product.OfferingSyncController;
import org.candlepin.subscriptions.product.SyncResult;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.subscription.api.model.ExternalReference;
import org.candlepin.subscriptions.subscription.api.model.SubscriptionProduct;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.admin.api.model.OfferingProductTags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
  public static final String BILLING_ACCOUNT_ID_ANY = "_ANY";
  public static final String PAYG_PRODUCT_NAME = "OpenShift Dedicated";

  @Autowired SubscriptionSyncController subscriptionSyncController;

  @Autowired private ApplicationClock clock;

  @MockBean ProductDenylist denylist;

  @MockBean OfferingRepository offeringRepository;

  @MockBean OfferingSyncController offeringSyncController;

  @MockBean SubscriptionRepository subscriptionRepository;

  @MockBean SubscriptionCapacityRepository subscriptionCapacityRepository;

  @MockBean OrgConfigRepository orgConfigRepository;

  @MockBean CapacityReconciliationController capacityReconciliationController;

  @MockBean SubscriptionService subscriptionService;

  @MockBean KafkaTemplate<String, SyncSubscriptionsTask> subscriptionsKafkaTemplate;

  @MockBean private TagProfile mockProfile;

  @Captor ArgumentCaptor<Iterable<Subscription>> subscriptionsCaptor;

  @Captor ArgumentCaptor<SubscriptionCapacity> capacityCaptor;

  private OffsetDateTime rangeStart = OffsetDateTime.now().minusDays(5);
  private OffsetDateTime rangeEnd = OffsetDateTime.now().plusDays(5);

  @Autowired
  @Qualifier("syncSubscriptionTasks")
  private TaskQueueProperties taskQueueProperties;

  @BeforeEach
  void setUp() {
    Mockito.when(offeringRepository.findById(Mockito.anyString()))
        .thenReturn(
            Optional.of(Offering.builder().productIds(new HashSet<>(Arrays.asList(68))).build()));
  }

  @Test
  void shouldCreateNewRecordOnQuantityChange() {
    Mockito.when(subscriptionRepository.findActiveSubscription(Mockito.anyString()))
        .thenReturn(Optional.of(createSubscription("123", "testsku", "456")));
    Mockito.when(offeringRepository.existsById("testsku")).thenReturn(true);
    when(denylist.productIdMatches(any())).thenReturn(false);
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
    when(denylist.productIdMatches(any())).thenReturn(false);
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
    when(denylist.productIdMatches(any())).thenReturn(false);
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
  void shouldSkipSyncSubscriptionIfSkuIsOnDenyList() {
    when(denylist.productIdMatches(any())).thenReturn(true);
    var dto = createDto("456", 10);
    Mockito.when(subscriptionService.getSubscriptionById("456")).thenReturn(dto);
    subscriptionSyncController.syncSubscription(dto.getId().toString());
    verify(subscriptionRepository, never()).save(any());
  }

  @Test
  void shouldSkipSyncSubscriptionIfSkuIsNotInOfferingRepository() {
    when(denylist.productIdMatches(any())).thenReturn(false);
    var dto = createDto("456", 10);
    Mockito.when(subscriptionService.getSubscriptionById("456")).thenReturn(dto);
    subscriptionSyncController.syncSubscription(dto.getId().toString());
    verify(subscriptionRepository, never()).save(any());
  }

  @Test
  void shouldSyncSubscriptionsWithinLimitForOrgAndQueueTaskForNext() {
    when(denylist.productIdMatches(any())).thenReturn(false);
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
    when(denylist.productIdMatches(any())).thenReturn(false);
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
    verifyNoInteractions(denylist, offeringRepository, subscriptionRepository);
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
    verifyNoInteractions(denylist, offeringRepository, subscriptionRepository);
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
    verifyNoInteractions(denylist, offeringRepository, subscriptionRepository);
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
    verify(subscriptionRepository, times(1)).save(any());
  }

  @Test
  void shouldForceSubscriptionSyncForOrgWithExistingSubs() {
    var dto1 = createDto("234", 3);
    var dto2 = createDto("345", 3);
    var subList = Arrays.asList(dto1, dto2);
    var subDaoList = Arrays.asList(convertDto(dto1), convertDto(dto2));
    when(subscriptionService.getSubscriptionsByOrgId("123")).thenReturn(subList);
    when(subscriptionRepository.findByOrgIdAndEndDateAfter(eq("123"), any()))
        .thenReturn(subDaoList);
    subscriptionSyncController.forceSyncSubscriptionsForOrg("123", false);
    verify(subscriptionRepository).findByOrgIdAndEndDateAfter(eq("123"), any());
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
        () ->
            subscriptionSyncController.findSubscriptionsAndSyncIfNeeded(
                "1000", orgId, key1, rangeStart, rangeEnd, false));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            subscriptionSyncController.findSubscriptionsAndSyncIfNeeded(
                "1000", orgId, key2, rangeStart, rangeEnd, false));
  }

  @Test
  void findsSubscriptionId_WhenBothOrgIdAndAccountNumberPresent() {
    UsageCalculation.Key key =
        new Key(
            String.valueOf(1),
            ServiceLevel.STANDARD,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "xyz");
    Subscription s = new Subscription();
    s.setStartDate(OffsetDateTime.now().minusDays(7));
    s.setEndDate(OffsetDateTime.now().plusDays(7));
    s.setBillingProvider(BillingProvider.RED_HAT);
    s.setBillingProviderId("xyz");
    List<Subscription> result = Collections.singletonList(s);

    Set<String> productNames = Set.of("OpenShift Container Platform");
    when(mockProfile.getOfferingProductNamesForTag(any())).thenReturn(productNames);
    when(subscriptionRepository.findByCriteria(any(), any()))
        .thenReturn(new ArrayList<>())
        .thenReturn(result);

    List<Subscription> actual =
        subscriptionSyncController.findSubscriptionsAndSyncIfNeeded(
            "1000", Optional.of("org1000"), key, rangeStart, rangeEnd, false);
    assertEquals(1, actual.size());
    assertEquals("xyz", actual.get(0).getBillingProviderId());
  }

  @Test
  void findsSubscriptionId_WhenOrgIdPresentAndAccountNumberAbsent() {
    UsageCalculation.Key key =
        new Key(
            String.valueOf(1),
            ServiceLevel.STANDARD,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "xyz");
    Subscription s = new Subscription();
    s.setStartDate(OffsetDateTime.now().minusDays(7));
    s.setEndDate(OffsetDateTime.now().plusDays(7));
    s.setBillingProvider(BillingProvider.RED_HAT);
    s.setBillingProviderId("xyz");
    List<Subscription> result = Collections.singletonList(s);

    Set<String> productNames = Set.of("OpenShift Container Platform");
    when(mockProfile.getOfferingProductNamesForTag(any())).thenReturn(productNames);
    when(subscriptionRepository.findByCriteria(any(), any()))
        .thenReturn(new ArrayList<>())
        .thenReturn(result);

    List<Subscription> actual =
        subscriptionSyncController.findSubscriptionsAndSyncIfNeeded(
            null, Optional.of("org1000"), key, rangeStart, rangeEnd, false);
    assertEquals(1, actual.size());
    assertEquals("xyz", actual.get(0).getBillingProviderId());
  }

  @Test
  void findsSubscriptionId_WhenAccountNumberPresentAndOrgIdAbsent() {
    UsageCalculation.Key key =
        new Key(
            String.valueOf(1),
            ServiceLevel.STANDARD,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "xyz");
    Subscription s = new Subscription();
    s.setStartDate(OffsetDateTime.now().minusDays(7));
    s.setEndDate(OffsetDateTime.now().plusDays(7));
    s.setBillingProvider(BillingProvider.RED_HAT);
    s.setBillingProviderId("xyz");
    List<Subscription> result = Collections.singletonList(s);

    Set<String> productNames = Set.of("OpenShift Container Platform");
    when(mockProfile.getOfferingProductNamesForTag(any())).thenReturn(productNames);
    when(subscriptionRepository.findByCriteria(any(), any()))
        .thenReturn(new ArrayList<>())
        .thenReturn(result);

    List<Subscription> actual =
        subscriptionSyncController.findSubscriptionsAndSyncIfNeeded(
            "account123", Optional.empty(), key, rangeStart, rangeEnd, false);
    assertEquals(1, actual.size());
    assertEquals("xyz", actual.get(0).getBillingProviderId());
  }

  @Test
  void findProductTagsBySku_WhenSkuPresent() {
    when(offeringRepository.findProductNameBySku("sku")).thenReturn(Optional.of("productname"));
    when(mockProfile.tagForOfferingProductName("productname")).thenReturn("producttag");

    OfferingProductTags productTags = subscriptionSyncController.findProductTags("sku");
    assertEquals(1, productTags.getData().size());
    assertEquals("producttag", productTags.getData().get(0));
  }

  @Test
  void findProductTagsBySku_WhenSkuNotPresent() {
    when(offeringRepository.findProductNameBySku("sku")).thenReturn(Optional.empty());
    when(mockProfile.tagForOfferingProductName("productname1")).thenReturn("producttag");
    RuntimeException e =
        assertThrows(
            MissingOfferingException.class,
            () -> subscriptionSyncController.findProductTags("sku"));
    assertEquals("Sku sku not found in Offering", e.getMessage());

    when(offeringRepository.findProductNameBySku("sku")).thenReturn(Optional.of("productname"));
    when(mockProfile.tagForOfferingProductName("productname")).thenReturn(null);
    OfferingProductTags productTags2 = subscriptionSyncController.findProductTags("sku");
    assertNull(productTags2.getData());
  }

  @Test
  void memoizesSubscriptionId() {
    UsageCalculation.Key key =
        new Key(
            String.valueOf(1),
            ServiceLevel.STANDARD,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "abc");
    Subscription s = new Subscription();
    s.setStartDate(OffsetDateTime.now().minusDays(7));
    s.setEndDate(OffsetDateTime.now().plusDays(7));
    s.setBillingProvider(BillingProvider.RED_HAT);
    s.setBillingProviderId("abc");
    List<Subscription> result = Collections.singletonList(s);

    Set<String> productNames = Set.of("OpenShift Container Platform");
    when(mockProfile.getOfferingProductNamesForTag(anyString())).thenReturn(productNames);
    when(subscriptionRepository.findByCriteria(any(), any()))
        .thenReturn(new ArrayList<>())
        .thenReturn(result);

    List<Subscription> actual =
        subscriptionSyncController.findSubscriptionsAndSyncIfNeeded(
            "1000", Optional.of("org1000"), key, rangeStart, rangeEnd, false);
    assertEquals(1, actual.size());
    assertEquals("abc", actual.get(0).getBillingProviderId());
    verify(subscriptionService, times(1)).getSubscriptionsByOrgId("org1000");
  }

  @Test
  void terminateActivePAYGSubscriptionTest() {
    Subscription s = createSubscription("123", "testsku", "456");
    Offering o = new Offering();
    o.setProductName(PAYG_PRODUCT_NAME);
    when(offeringRepository.findById("testsku")).thenReturn(Optional.of(o));
    when(subscriptionRepository.findActiveSubscription("456")).thenReturn(Optional.of(s));
    when(mockProfile.isProductPAYGEligible("testsku")).thenReturn(true);

    var termination = OffsetDateTime.now();
    var result = subscriptionSyncController.terminateSubscription("456", termination);
    assertThat(result, matchesPattern("Subscription 456 terminated at .*\\."));
    assertEquals(termination, s.getEndDate());
  }

  @Test
  void lateTerminateActivePAYGSubscriptionTest() {
    Subscription s = createSubscription("123", "testsku", "456");
    Offering o = new Offering();
    o.setProductName(PAYG_PRODUCT_NAME);
    when(offeringRepository.findById("testsku")).thenReturn(Optional.of(o));
    when(subscriptionRepository.findActiveSubscription("456")).thenReturn(Optional.of(s));
    when(mockProfile.tagForOfferingProductName(PAYG_PRODUCT_NAME))
        .thenReturn("OpenShift-dedicated-metrics");
    when(mockProfile.isProductPAYGEligible("OpenShift-dedicated-metrics")).thenReturn(true);

    var termination = OffsetDateTime.now().minusDays(1);
    var result = subscriptionSyncController.terminateSubscription("456", termination);
    assertThat(
        result,
        matchesPattern("Subscription 456 terminated at .* with out of range termination date .*"));
    assertEquals(termination, s.getEndDate());
  }

  @Test
  void terminateInTheFutureActivePAYGSubscriptionTest() {
    Subscription s = createSubscription("123", "testsku", "456");
    Offering o = new Offering();
    o.setProductName(PAYG_PRODUCT_NAME);
    when(offeringRepository.findById("testsku")).thenReturn(Optional.of(o));
    when(subscriptionRepository.findActiveSubscription("456")).thenReturn(Optional.of(s));

    when(mockProfile.tagForOfferingProductName(PAYG_PRODUCT_NAME))
        .thenReturn("OpenShift-dedicated-metrics");
    when(mockProfile.isProductPAYGEligible("OpenShift-dedicated-metrics")).thenReturn(true);

    var termination = OffsetDateTime.now().plusDays(1);
    var result = subscriptionSyncController.terminateSubscription("456", termination);
    assertThat(
        result,
        matchesPattern("Subscription 456 terminated at .* with out of range termination date .*"));
    assertEquals(termination, s.getEndDate());
  }

  @Test
  void terminateActiveNonPAYGSubscriptionTest() {
    Subscription s = createSubscription("123", "testsku", "456");
    Offering o = new Offering();
    o.setProductName("Random Product");
    when(offeringRepository.findById("testsku")).thenReturn(Optional.of(o));
    when(subscriptionRepository.findActiveSubscription("456")).thenReturn(Optional.of(s));
    when(mockProfile.tagForOfferingProductName("Random Product")).thenReturn("random");
    when(mockProfile.isProductPAYGEligible("random")).thenReturn(false);

    var termination = OffsetDateTime.now();
    var result = subscriptionSyncController.terminateSubscription("456", termination);
    assertThat(result, matchesPattern("Subscription 456 terminated at .*\\."));
    assertEquals(termination, s.getEndDate());
  }

  @Test
  void testSubscriptionEnrichedFromSubscriptionServiceWhenDbRecordAbsentAndSubscriptionIdMissing() {
    Subscription incoming = createSubscription("123", "testsku", null);
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
    when(offeringRepository.existsById("testsku")).thenReturn(true);
    when(subscriptionService.getSubscriptionBySubscriptionNumber("subnum"))
        .thenReturn(serviceResponse);

    subscriptionSyncController.syncSubscription(incoming, Optional.empty());
    verify(subscriptionService).getSubscriptionBySubscriptionNumber("subnum");
    assertEquals("billingAccountId", incoming.getBillingAccountId());
    assertEquals(BillingProvider.AWS, incoming.getBillingProvider());
    assertEquals("p;c;s", incoming.getBillingProviderId());
    assertEquals("456", incoming.getSubscriptionId());
  }

  @Test
  void testSubscriptionEnrichedFromDbWhenSubscriptionIdMissing() {
    Subscription incoming = createSubscription("123", "testsku", null);
    Subscription existing = createSubscription("123", "testsku", "456");
    existing.setBillingAccountId("billingAccountId");
    existing.setBillingProvider(BillingProvider.RED_HAT);
    existing.setBillingProviderId("billingProviderId");

    when(denylist.productIdMatches(any())).thenReturn(false);
    when(offeringRepository.existsById("testsku")).thenReturn(true);

    subscriptionSyncController.syncSubscription(incoming, Optional.of(existing));

    verifyNoInteractions(subscriptionService);
    assertEquals("billingAccountId", incoming.getBillingAccountId());
    assertEquals(BillingProvider.RED_HAT, incoming.getBillingProvider());
    assertEquals("billingProviderId", incoming.getBillingProviderId());
    assertEquals("456", incoming.getSubscriptionId());
  }

  @Test
  void testSubscriptionNotEnrichedWhenSubscriptionIdPresent() {
    Subscription incoming = createSubscription("123", "testsku", "456");
    Subscription existing = createSubscription("123", "testsku", "456");
    existing.setBillingAccountId("billingAccountId");

    when(denylist.productIdMatches(any())).thenReturn(false);
    when(offeringRepository.existsById("testsku")).thenReturn(true);

    subscriptionSyncController.syncSubscription(incoming, Optional.of(existing));

    verifyNoInteractions(subscriptionService);
    verify(subscriptionRepository).save(any());
    assertNull(incoming.getBillingAccountId());
  }

  @Test
  void testBillingFieldsUpdatedOnChange() {
    Subscription incoming = createSubscription("123", "testsku", "456");
    incoming.setBillingProvider(BillingProvider.RED_HAT);
    incoming.setBillingProviderId("newBillingProviderId");
    incoming.setBillingAccountId("newBillingAccountId");
    incoming.setAccountNumber("account123");
    Subscription existing = createSubscription("123", "testsku", "456");
    existing.setBillingProvider(BillingProvider.AWS);
    existing.setBillingAccountId("oldBillingAccountId");
    existing.setBillingProviderId("oldBillinProviderId");

    when(denylist.productIdMatches(any())).thenReturn(false);
    when(offeringRepository.existsById("testsku")).thenReturn(true);

    subscriptionSyncController.syncSubscription(incoming, Optional.of(existing));

    verifyNoInteractions(subscriptionService);
    verify(subscriptionRepository).save(existing);
    assertEquals(BillingProvider.RED_HAT, existing.getBillingProvider());
    assertEquals("newBillingProviderId", existing.getBillingProviderId());
    assertEquals("newBillingAccountId", existing.getBillingAccountId());
    assertEquals("account123", existing.getAccountNumber());
  }

  @Test
  void testOfferingSyncedWhenMissing() {
    Subscription incoming = createSubscription("123", "testsku", "456");

    when(denylist.productIdMatches(any())).thenReturn(false);
    when(offeringRepository.existsById("testsku")).thenReturn(false);
    when(offeringSyncController.syncOffering("testsku")).thenReturn(SyncResult.SKIPPED_MATCHING);

    subscriptionSyncController.syncSubscription(incoming, Optional.empty());

    verify(offeringSyncController).syncOffering("testsku");
    verify(subscriptionRepository).save(incoming);
    assertNull(incoming.getBillingAccountId());
  }

  @Test
  void testOfferingSyncFailsAndProcessingStops() {
    Subscription incoming = createSubscription("123", "testsku", "456");

    when(denylist.productIdMatches(any())).thenReturn(false);
    when(offeringRepository.existsById("testsku")).thenReturn(false);
    when(offeringSyncController.syncOffering("testsku")).thenReturn(SyncResult.FAILED);

    subscriptionSyncController.syncSubscription(incoming, Optional.empty());

    verify(offeringSyncController).syncOffering("testsku");
    verify(subscriptionRepository, times(0)).save(incoming);
  }

  @Test
  void testShouldRemoveStaleSubscriptionsNotPresentInSubscriptionService() {
    var subscription = createSubscription("123", "testsku", "456");
    when(subscriptionRepository.findByOrgId(any())).thenReturn(Stream.of(subscription));
    subscriptionSyncController.reconcileSubscriptionsWithSubscriptionService("org123");
    verify(subscriptionRepository).deleteAll(subscriptionsCaptor.capture());
    assertThat(subscriptionsCaptor.getValue(), contains(subscription));
  }

  @Test
  void testShouldRemoveStaleSubscriptionsPresentInSubscriptionServiceButDenylisted() {
    var subscription = createSubscription("123", "testsku", "456");
    var subServiceSub = createDto("456", 1);
    when(subscriptionRepository.findByOrgId(any())).thenReturn(Stream.of(subscription));
    when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(subServiceSub));
    when(denylist.productIdMatches(any())).thenReturn(true);
    subscriptionSyncController.reconcileSubscriptionsWithSubscriptionService("org123");
    verify(subscriptionRepository).deleteAll(subscriptionsCaptor.capture());
    assertThat(subscriptionsCaptor.getValue(), contains(subscription));
  }

  @Test
  void testShouldNotRemovePresentSub() {
    var subscription = createSubscription("123", "testsku", "456");
    var subServiceSub = createDto("456", 1);
    when(subscriptionRepository.findByOrgId(any())).thenReturn(Stream.of(subscription));
    when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(subServiceSub));
    when(denylist.productIdMatches(any())).thenReturn(false);
    subscriptionSyncController.reconcileSubscriptionsWithSubscriptionService("org123");
    verify(subscriptionRepository).deleteAll(subscriptionsCaptor.capture());
    assertFalse(subscriptionsCaptor.getValue().iterator().hasNext());
  }

  @Test
  void testShouldRemoveStaleCapacityNotPresentInSubscriptionService() {
    var capacity = new SubscriptionCapacity();
    when(subscriptionCapacityRepository.findByKeyOrgId(any())).thenReturn(Stream.of(capacity));
    subscriptionSyncController.reconcileSubscriptionsWithSubscriptionService("org123");
    verify(subscriptionCapacityRepository).delete(capacityCaptor.capture());
    assertEquals(capacityCaptor.getValue(), capacity);
  }

  @Test
  void testShouldRemoveStaleCapacityPresentInSubscriptionServiceButDenylisted() {
    var subServiceSub = createDto("456", 1);
    var capacity = new SubscriptionCapacity();
    capacity.setSubscriptionId("456");
    when(subscriptionCapacityRepository.findByKeyOrgId(any())).thenReturn(Stream.of(capacity));
    when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(subServiceSub));
    when(denylist.productIdMatches(any())).thenReturn(true);
    subscriptionSyncController.reconcileSubscriptionsWithSubscriptionService("org123");
    verify(subscriptionCapacityRepository).delete(capacityCaptor.capture());
    assertEquals(capacityCaptor.getValue(), capacity);
  }

  @Test
  void testShouldNotRemovePresentCapacity() {
    var subServiceSub = createDto("456", 1);
    var capacity = new SubscriptionCapacity();
    capacity.setSubscriptionId("456");
    when(subscriptionCapacityRepository.findByKeyOrgId(any())).thenReturn(Stream.of(capacity));
    when(subscriptionService.getSubscriptionsByOrgId(any())).thenReturn(List.of(subServiceSub));
    when(denylist.productIdMatches(any())).thenReturn(false);
    subscriptionSyncController.reconcileSubscriptionsWithSubscriptionService("org123");
    verify(subscriptionCapacityRepository, times(0)).delete(capacityCaptor.capture());
  }

  private Subscription createSubscription(String orgId, String sku, String subId) {
    final Subscription subscription = new Subscription();
    subscription.setSubscriptionId(subId);
    subscription.setOrgId(orgId);
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
        .orgId(subscription.getWebCustomerId().toString())
        .accountNumber(String.valueOf(subscription.getOracleAccountNumber()))
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
