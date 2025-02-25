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
package com.redhat.swatch.contract.repository;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import io.quarkus.panache.common.Sort;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.candlepin.clock.ApplicationClock;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SubscriptionRepositoryTest {

  private static final String BILLING_ACCOUNT_ID = "sellerAcctId";
  private static final String PRODUCT_TAG = "rosa";
  private OffsetDateTime now;

  @Inject SubscriptionRepository subscriptionRepo;
  @Inject OfferingRepository offeringRepo;
  @Inject ApplicationClock clock;

  @BeforeEach
  void setup() {
    now = clock.now();
  }

  @TestTransaction
  @Test
  void findsAllSubscriptionsForAGivenSku() {
    OfferingEntity mct3718 = createOffering("MCT3718", 1066);
    OfferingEntity rh00798 = createOffering("RH00798", 1512);
    offeringRepo.persist(List.of(mct3718, rh00798));
    offeringRepo.flush();

    for (int i = 0; i < 5; i++) {
      SubscriptionEntity subscription1 = createSubscription("1");
      subscription1.setOffering(mct3718);

      SubscriptionEntity subscription2 = createSubscription("1");
      subscription2.setOffering(rh00798);

      SubscriptionEntity subscription3 = createSubscription("2");
      subscription3.setOffering(mct3718);
      subscriptionRepo.persist(List.of(subscription1, subscription2, subscription3));
    }

    var result = subscriptionRepo.findByOfferingSku("MCT3718", 0, 5);
    assertEquals(5, result.size());

    result = subscriptionRepo.findByOfferingSku("MCT3718", 0, 1000);
    assertEquals(10, result.size());
  }

  @TestTransaction
  @Test
  void canInsertAndRetrieveSubscriptions() {
    // Because the findActiveSubscription query uses CURRENT_TIMESTAMP,
    // reset NOW so that it is current and not fixed.
    now = OffsetDateTime.now();
    SubscriptionEntity subscription = createSubscription("1", "123", "sellerAcctId");
    OfferingEntity offering = createOffering("testSku", "rosa", 1066, null, null, null);
    subscription.setOffering(offering);
    offeringRepo.persist(offering);
    subscriptionRepo.persistAndFlush(subscription);

    SubscriptionEntity retrieved =
        subscriptionRepo.findActiveSubscription("123").stream().findFirst().orElse(null);

    // because of an issue with precision related to findActiveSubscription passing the entity
    // cache, we'll have to check fields
    assertEquals(subscription.getSubscriptionId(), retrieved.getSubscriptionId());
    assertEquals(subscription.getOffering().getSku(), retrieved.getOffering().getSku());
    assertEquals(subscription.getOrgId(), retrieved.getOrgId());
    assertEquals(subscription.getQuantity(), retrieved.getQuantity());
    assertTrue(
        Duration.between(subscription.getStartDate(), retrieved.getStartDate()).abs().getSeconds()
            < 1L);
    assertTrue(
        Duration.between(subscription.getEndDate(), retrieved.getEndDate()).abs().getSeconds()
            < 1L);
  }

  @TestTransaction
  @Test
  void canMatchOfferings() {
    SubscriptionEntity subscription = createSubscription("1", "123", "sellerAcctId");
    OfferingEntity o1 =
        createOffering("testSku1", "rosa", 1, ServiceLevel.STANDARD, Usage.PRODUCTION, "ocp");
    subscription.setOffering(o1);
    offeringRepo.persist(o1);
    subscriptionRepo.persist(subscription);

    OfferingEntity o2 =
        createOffering("testSku2", "rosa", 1, ServiceLevel.PREMIUM, Usage.PRODUCTION, "ocp");
    offeringRepo.persistAndFlush(o2);

    String productTag = "rosa";
    var resultList =
        subscriptionRepo.findByCriteria(
            DbReportCriteria.builder()
                .productTag(productTag)
                .serviceLevel(ServiceLevel.STANDARD)
                .usage(Usage.PRODUCTION)
                .billingProvider(BillingProvider._ANY)
                .billingAccountId("sellerAcctId")
                .beginning(now)
                .ending(now)
                .build(),
            Sort.by(SubscriptionEntity_.START_DATE).descending());
    assertEquals(1, resultList.size());

    var result = resultList.get(0);
    assertEquals("testSku1", result.getOffering().getSku());
  }

  @TestTransaction
  @Test
  void doesNotMatchMismatchedSkusOfferings() {
    OfferingEntity offering = createOffering("testSku", "rosa", 1066, null, null, null);
    offeringRepo.persist(offering);

    SubscriptionEntity subscription = createSubscription("1", "123", "sellerAcctId");
    subscription.setOffering(offering);
    subscriptionRepo.persistAndFlush(subscription);

    OfferingEntity o1 =
        createOffering("otherSku1", "rosa", 1, ServiceLevel.STANDARD, Usage.PRODUCTION, "ocp");
    offeringRepo.persistAndFlush(o1);
    OfferingEntity o2 =
        createOffering("otherSku2", "rosa", 1, ServiceLevel.PREMIUM, Usage.PRODUCTION, "ocp");
    offeringRepo.persistAndFlush(o2);

    String productTag = "rosa";
    var result =
        subscriptionRepo.findByCriteria(
            DbReportCriteria.builder()
                .productTag(productTag)
                .serviceLevel(ServiceLevel.STANDARD)
                .usage(Usage.PRODUCTION)
                .billingProvider(BillingProvider._ANY)
                .billingAccountId("sellerAcctId")
                .beginning(now)
                .ending(now)
                .build(),
            Sort.by(SubscriptionEntity_.START_DATE).descending());
    assertEquals(0, result.size());
  }

  @TestTransaction
  @Test
  void doesNotMatchMismatchedBillingAccountId() {
    OfferingEntity offering = createOffering("testSku", "rosa", 1066, null, null, null);
    offeringRepo.persist(offering);

    SubscriptionEntity subscription = createSubscription("1", "123", "sellerAcctId");
    subscription.setOffering(offering);
    subscriptionRepo.persistAndFlush(subscription);

    OfferingEntity o1 =
        createOffering("testSku1", "rosa", 1, ServiceLevel.STANDARD, Usage.PRODUCTION, "ocp");
    offeringRepo.persist(o1);
    OfferingEntity o2 =
        createOffering("testSku2", "rosa", 1, ServiceLevel.PREMIUM, Usage.PRODUCTION, "ocp");
    offeringRepo.persistAndFlush(o2);

    String productTag = "rosa";
    var resultList =
        subscriptionRepo.findByCriteria(
            DbReportCriteria.builder()
                .productTag(productTag)
                .serviceLevel(ServiceLevel.STANDARD)
                .usage(Usage.PRODUCTION)
                .billingProvider(BillingProvider._ANY)
                .billingAccountId("wrongSellerAccount")
                .beginning(now)
                .ending(now)
                .build(),
            Sort.by(SubscriptionEntity_.START_DATE).descending());

    assertEquals(0, resultList.size());
  }

  @TestTransaction
  @Test
  void removeAllButMostRecentMarketplaceSubscriptions() {
    SubscriptionEntity subscription1 =
        createSubscription("1", "123", "sellerAcctId", now.minusDays(30), now.plusDays(10));
    SubscriptionEntity subscription2 =
        createSubscription("1", "234", "sellerAcctId", now.minusHours(1), now.plusDays(30));
    OfferingEntity offering =
        createOffering("testSku1", "rosa", 1, ServiceLevel.STANDARD, Usage.PRODUCTION, "ocp");
    List<SubscriptionEntity> subscriptions = List.of(subscription1, subscription2);
    subscriptions.forEach(x -> x.setOffering(offering));
    offeringRepo.persist(offering);
    subscriptions.forEach(subscriptionRepo::persistAndFlush);

    String productTag = "rosa";

    var resultList =
        subscriptionRepo.findByCriteria(
            DbReportCriteria.builder()
                .productTag(productTag)
                .serviceLevel(ServiceLevel.STANDARD)
                .usage(Usage.PRODUCTION)
                .billingProvider(BillingProvider._ANY)
                .billingAccountId("sellerAcctId")
                .beginning(now)
                .ending(now)
                .build(),
            Sort.by(SubscriptionEntity_.START_DATE).descending());

    assertEquals(2, resultList.size());

    var result1 = resultList.get(0);
    var result2 = resultList.get(1);

    assertTrue(result1.getStartDate().isAfter(result2.getStartDate()));
  }

  @TestTransaction
  @Test
  void findsAllSubscriptionsForSla() {
    OfferingEntity mct3718 =
        createOffering(
            "MCT3718", "rosa", 1066, ServiceLevel.SELF_SUPPORT, Usage.PRODUCTION, "ROLE");
    offeringRepo.persist(mct3718);

    for (int i = 0; i < 5; i++) {
      SubscriptionEntity subscription =
          createSubscription("1", String.valueOf(new Random().nextInt()), "sellerAcctId");
      subscription.setOffering(mct3718);
      subscriptionRepo.persistAndFlush(subscription);
    }
    var criteria = DbReportCriteria.builder().serviceLevel(ServiceLevel.SELF_SUPPORT).build();

    var result = subscriptionRepo.findByCriteria(criteria);
    assertEquals(5, result.size());
  }

  @TestTransaction
  @Test
  void findsUnlimitedSubscriptions() {
    var s1 = createSubscription("org123", "sub123", "seller123");
    var s2 = createSubscription("org123", "sub321", "seller123");
    var offering1 = createOffering("testSkuUnlimited", "rosa", 1066, null, null, null);
    offering1.setHasUnlimitedUsage(true);
    List.of(s1, s2).forEach(x -> x.setOffering(offering1));

    var s3 = createSubscription("org123", "sub456", "seller123");
    var s4 = createSubscription("org123", "sub678", "seller123");
    var offering2 = createOffering("testSkuLimited", "rosa", 1066, null, null, null);
    offering2.setHasUnlimitedUsage(false);
    List.of(s3, s4).forEach(x -> x.setOffering(offering2));

    offeringRepo.persist(offering1);
    offeringRepo.persist(offering2);
    List.of(s1, s2, s3, s4).forEach(subscriptionRepo::persistAndFlush);

    var criteria = DbReportCriteria.builder().orgId("org123").build();
    var result = subscriptionRepo.findUnlimited(criteria);
    assertThat(result, Matchers.containsInAnyOrder(s1, s2));
  }

  @TestTransaction
  @Test
  void testSubscriptionIsActive() {
    // Because the findActiveSubscription query uses CURRENT_TIMESTAMP,
    // reset NOW so that it is current and not fixed.
    now = OffsetDateTime.now();

    var s1 = createSubscription("org123", "sub123", "seller123");
    var s2 = createSubscription("org123", "sub321", "seller123");
    s2.setEndDate(null);

    var offering1 = createOffering("testSkuUnlimited", "rosa", 1066, null, null, null);
    List.of(s1, s2).forEach(x -> x.setOffering(offering1));

    offeringRepo.persist(offering1);
    List.of(s1, s2).forEach(subscriptionRepo::persistAndFlush);

    assertFalse(subscriptionRepo.findActiveSubscription(s1.getSubscriptionId()).isEmpty());
    assertFalse(subscriptionRepo.findActiveSubscription(s2.getSubscriptionId()).isEmpty());
  }

  @TestTransaction
  @Test
  void testFindBySpecificationWhenSubscriptionEndDateIsNull() {
    var s1 = createSubscription("org123", "sub123", "seller123");

    var s2 = createSubscription("org123", "sub321", "seller123");
    s2.setEndDate(null);

    var offering1 = createOffering("testSkuUnlimited", "rosa", 1066, null, null, null);
    List.of(s1, s2).forEach(x -> x.setOffering(offering1));

    offeringRepo.persist(offering1);
    List.of(s1, s2).forEach(subscriptionRepo::persistAndFlush);

    var resultList =
        subscriptionRepo.findByCriteria(
            DbReportCriteria.builder()
                .orgId("org123")
                .beginning(now)
                .ending(now.plusDays(1))
                .build(),
            Sort.by(SubscriptionEntity_.START_DATE).descending());
    assertEquals(2, resultList.size());
    assertTrue(resultList.contains(s2));
    assertTrue(resultList.contains(s1));
    assertThat(resultList, Matchers.containsInAnyOrder(s1, s2));
  }

  @TestTransaction
  @Test
  void testMatchesOnFirstPartOfMultipartBillingAccountId() {
    OfferingEntity o1 =
        createOffering("testSku1", "rosa", 1, ServiceLevel.STANDARD, Usage.PRODUCTION, "ocp");
    offeringRepo.persist(o1);

    SubscriptionEntity subscription1 =
        createSubscription("1", "123", "providerTenantId;providerSubscriptionId");
    SubscriptionEntity subscription2 = createSubscription("1", "124", "providerTenantId");
    subscription1.setOffering(o1);
    subscription2.setOffering(o1);
    List.of(subscription1, subscription2).forEach(subscriptionRepo::persistAndFlush);

    String productTag = "rosa";
    var resultList =
        subscriptionRepo.findByCriteria(
            DbReportCriteria.builder()
                .productTag(productTag)
                .serviceLevel(ServiceLevel.STANDARD)
                .usage(Usage.PRODUCTION)
                .billingProvider(BillingProvider._ANY)
                .billingAccountId("providerTenantId")
                .beginning(now)
                .ending(now)
                .build(),
            Sort.by(SubscriptionEntity_.START_DATE).descending());

    assertEquals(2, resultList.size());
  }

  @TestTransaction
  @Test
  void testMatchesOnBothPartsOfMultipartBillingAccountId() {
    OfferingEntity o1 =
        createOffering("testSku1", "rosa", 1, ServiceLevel.STANDARD, Usage.PRODUCTION, "ocp");
    offeringRepo.persist(o1);

    SubscriptionEntity subscription1 =
        createSubscription("1", "123", "providerTenantId;providerSubscriptionId");
    SubscriptionEntity subscription2 = createSubscription("1", "124", "providerTenantId");
    subscription1.setOffering(o1);
    subscription2.setOffering(o1);
    List.of(subscription1, subscription2).forEach(subscriptionRepo::persistAndFlush);

    String productTag = "rosa";
    var resultList =
        subscriptionRepo.findByCriteria(
            DbReportCriteria.builder()
                .productTag(productTag)
                .serviceLevel(ServiceLevel.STANDARD)
                .usage(Usage.PRODUCTION)
                .billingProvider(BillingProvider._ANY)
                .billingAccountId("providerTenantId;providerSubscriptionId")
                .beginning(now)
                .ending(now)
                .build(),
            Sort.by(SubscriptionEntity_.START_DATE).descending());

    assertEquals(1, resultList.size());
    assertEquals(
        "providerTenantId;providerSubscriptionId", resultList.get(0).getBillingAccountId());
  }

  @TestTransaction
  @Test
  void findsAllSubscriptionsForProductTag() {
    var expectedProductTag = "testProductTag";
    OfferingEntity mct3718 =
        createOffering(
            "MCT3718",
            expectedProductTag,
            1066,
            ServiceLevel.SELF_SUPPORT,
            Usage.PRODUCTION,
            "ROLE");
    offeringRepo.persist(mct3718);

    for (int i = 0; i < 5; i++) {
      SubscriptionEntity subscription =
          createSubscription("1", String.valueOf(new Random().nextInt()), "sellerAcctId");
      subscription.setOffering(mct3718);
      subscriptionRepo.persistAndFlush(subscription);
    }
    var criteria = DbReportCriteria.builder().productTag(expectedProductTag).build();

    var result = subscriptionRepo.findByCriteria(criteria);
    assertEquals(5, result.size());
  }

  @TestTransaction
  @Test
  void testFindBillingAccountInfo() {
    var expectedOrgId = "123";
    OfferingEntity rosa =
        createOffering(
            "MCT3718", "rosa", 1066, ServiceLevel.SELF_SUPPORT, Usage.PRODUCTION, "ROLE");
    offeringRepo.persist(rosa);

    OfferingEntity openshift =
        createOffering(
            "MW3362",
            "OpenShift-dedicated-metrics",
            236,
            ServiceLevel.SELF_SUPPORT,
            Usage.PRODUCTION,
            "ROLE");
    offeringRepo.persist(openshift);

    var expectedBillingAccountIds = new ArrayList<String>();
    // Create subscriptions for our expected orgId and product_tag
    for (int i = 0; i < 5; i++) {
      SubscriptionEntity subscription =
          createSubscription(
              expectedOrgId, String.valueOf(new Random().nextInt()), "expectedSellerAcctId" + i);
      subscription.setOffering(rosa);
      subscriptionRepo.persistAndFlush(subscription);
      expectedBillingAccountIds.add(subscription.getBillingAccountId());
    }
    // Create subscriptions for our expected orgId and different product_tag
    for (int i = 0; i < 5; i++) {
      SubscriptionEntity subscription =
          createSubscription(
              expectedOrgId, String.valueOf(new Random().nextInt()), "unexpectedSellerAcctId" + i);
      subscription.setOffering(openshift);
      subscriptionRepo.persistAndFlush(subscription);
    }
    // Create subscriptions for a different orgId and the expected product_tag
    for (int i = 0; i < 5; i++) {
      SubscriptionEntity subscription =
          createSubscription("2", String.valueOf(new Random().nextInt()), "NotMySellerAcctId" + i);
      subscription.setOffering(rosa);
      subscriptionRepo.persistAndFlush(subscription);
    }

    var result = subscriptionRepo.findBillingAccountInfo(expectedOrgId, Optional.of("rosa"));
    assertEquals(5, result.size());
    var resultBillingAccountIds =
        result.stream().map(BillingAccountInfoDTO::billingAccountId).collect(Collectors.toSet());
    assertTrue(resultBillingAccountIds.containsAll(expectedBillingAccountIds));
  }

  @TestTransaction
  @Test
  void findBillingAccountInfoWithoutProductTag() {
    var expectedOrgId = "123";
    OfferingEntity rosa =
        createOffering(
            "MCT3718", "rosa", 1066, ServiceLevel.SELF_SUPPORT, Usage.PRODUCTION, "ROLE");
    offeringRepo.persist(rosa);

    OfferingEntity openshift =
        createOffering(
            "MW3362",
            "OpenShift-dedicated-metrics",
            236,
            ServiceLevel.SELF_SUPPORT,
            Usage.PRODUCTION,
            "ROLE");
    offeringRepo.persist(openshift);

    var expectedBillingAccountIds = new ArrayList<String>();
    // Create subscriptions for our expected orgId and rosa tag
    for (int i = 0; i < 2; i++) {
      SubscriptionEntity subscription =
          createSubscription(
              expectedOrgId, String.valueOf(new Random().nextInt()), "expectedSellerAcctId" + i);
      subscription.setOffering(rosa);
      subscriptionRepo.persistAndFlush(subscription);
      expectedBillingAccountIds.add(subscription.getBillingAccountId());
    }
    // Create subscriptions for our expected orgId and openshift tag
    for (int i = 0; i < 2; i++) {
      SubscriptionEntity subscription =
          createSubscription(
              expectedOrgId,
              String.valueOf(new Random().nextInt()),
              "otherExpectedSellerAcctId" + i);
      subscription.setOffering(openshift);
      subscriptionRepo.persistAndFlush(subscription);
      expectedBillingAccountIds.add(subscription.getBillingAccountId());
    }

    var result = subscriptionRepo.findBillingAccountInfo(expectedOrgId, Optional.empty());
    assertEquals(4, result.size());
    var resultBillingAccountIds =
        result.stream().map(BillingAccountInfoDTO::billingAccountId).collect(Collectors.toSet());
    assertTrue(resultBillingAccountIds.containsAll(expectedBillingAccountIds));
  }

  private OfferingEntity createOffering(String sku, int productId) {
    return OfferingEntity.builder()
        .sku(sku)
        .productIds(Set.of(productId))
        .productTags(Set.of(PRODUCT_TAG))
        .build();
  }

  private OfferingEntity createOffering(
      String sku, String productTag, int productId, ServiceLevel sla, Usage usage, String role) {
    return OfferingEntity.builder()
        .sku(sku)
        .productIds(Set.of(productId))
        .serviceLevel(sla)
        .usage(usage)
        .role(role)
        .productTags(Set.of(productTag))
        .build();
  }

  private SubscriptionEntity createSubscription(String orgId) {
    String subId = String.valueOf(new Random().nextInt());
    SubscriptionEntity subscription = new SubscriptionEntity();
    subscription.setBillingProviderId("bananas");
    subscription.setSubscriptionId(subId);
    subscription.setOrgId(orgId);
    subscription.setQuantity(4L);
    subscription.setStartDate(OffsetDateTime.now());
    subscription.setEndDate(OffsetDateTime.now().plusDays(30));
    subscription.setSubscriptionNumber(subId + "1");
    subscription.setBillingProvider(BillingProvider.RED_HAT);
    subscription.setBillingAccountId(BILLING_ACCOUNT_ID);

    return subscription;
  }

  private SubscriptionEntity createSubscription(
      String orgId, String subId, String billingAccountId) {

    // Truncate to avoid issues around nanosecond mismatches -- HSQLDB doesn't store timestamps
    // at the same resolution as the JVM
    OffsetDateTime startDate = now.truncatedTo(ChronoUnit.SECONDS);
    return createSubscription(orgId, subId, billingAccountId, startDate, startDate.plusDays(30));
  }

  private SubscriptionEntity createSubscription(
      String orgId,
      String subId,
      String billingAccountId,
      OffsetDateTime startDate,
      OffsetDateTime endDate) {

    SubscriptionEntity subscription = new SubscriptionEntity();
    subscription.setBillingProviderId("bananas");
    subscription.setSubscriptionId(subId);
    subscription.setOrgId(orgId);
    subscription.setQuantity(4L);
    subscription.setStartDate(startDate);
    subscription.setEndDate(endDate);
    subscription.setSubscriptionNumber(subId + "1");
    subscription.setBillingProvider(BillingProvider.RED_HAT);
    subscription.setBillingAccountId(billingAccountId);

    return subscription;
  }
}
