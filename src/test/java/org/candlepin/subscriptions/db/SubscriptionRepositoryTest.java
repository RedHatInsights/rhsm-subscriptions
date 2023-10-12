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
package org.candlepin.subscriptions.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestClockConfiguration.class)
@ActiveProfiles("test")
class SubscriptionRepositoryTest {

  @Autowired ApplicationClock clock;

  @Autowired SubscriptionRepository subscriptionRepo;

  @Autowired OfferingRepository offeringRepo;

  private OffsetDateTime NOW;

  @BeforeEach
  void setup() {
    NOW = clock.now();
  }

  @Transactional
  @Test
  void canInsertAndRetrieveSubscriptions() {
    // Because the findActiveSubscription query uses CURRENT_TIMESTAMP,
    // reset NOW so that it is current and not fixed.
    NOW = OffsetDateTime.now();
    Subscription subscription = createSubscription("1", "1000", "123", "sellerAcctId");
    Offering offering = createOffering("testSku", "Test SKU", 1066, null, null, null);
    subscription.setOffering(offering);
    offeringRepo.save(offering);
    subscriptionRepo.saveAndFlush(subscription);

    Subscription retrieved = subscriptionRepo.findActiveSubscription("123").orElse(null);

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

  @Transactional
  @Test
  void canMatchOfferings() {
    Subscription subscription = createSubscription("1", "1000", "123", "sellerAcctId");
    Offering o1 =
        createOffering("testSku1", "Test SKU 1", 1, ServiceLevel.STANDARD, Usage.PRODUCTION, "ocp");
    subscription.setOffering(o1);
    offeringRepo.save(o1);
    subscriptionRepo.save(subscription);

    Offering o2 =
        createOffering("testSku2", "Test SKU 2", 1, ServiceLevel.PREMIUM, Usage.PRODUCTION, "ocp");
    offeringRepo.saveAndFlush(o2);

    Set<String> productNames = Set.of("Test SKU 1");
    var resultList =
        subscriptionRepo.findByCriteria(
            DbReportCriteria.builder()
                .productNames(productNames)
                .serviceLevel(ServiceLevel.STANDARD)
                .usage(Usage.PRODUCTION)
                .billingProvider(BillingProvider._ANY)
                .billingAccountId("sellerAcctId")
                .beginning(NOW)
                .ending(NOW)
                .build(),
            Sort.by(Subscription_.START_DATE).descending());
    assertEquals(1, resultList.size());

    var result = resultList.get(0);
    assertEquals("testSku1", result.getOffering().getSku());
  }

  @Transactional
  @Test
  void doesNotMatchMismatchedSkusOfferings() {
    Offering offering = createOffering("testSku", "Test SKU", 1066, null, null, null);
    offeringRepo.save(offering);

    Subscription subscription = createSubscription("1", "1000", "123", "sellerAcctId");
    subscription.setOffering(offering);
    subscriptionRepo.saveAndFlush(subscription);

    Offering o1 =
        createOffering(
            "otherSku1", "Other SKU 1", 1, ServiceLevel.STANDARD, Usage.PRODUCTION, "ocp");
    offeringRepo.saveAndFlush(o1);
    Offering o2 =
        createOffering(
            "otherSku2", "Other SKU 2", 1, ServiceLevel.PREMIUM, Usage.PRODUCTION, "ocp");
    offeringRepo.saveAndFlush(o2);

    Set<String> productNames = Set.of("Other SKU 1", "Other SKU 2");
    var result =
        subscriptionRepo.findByCriteria(
            DbReportCriteria.builder()
                .productNames(productNames)
                .serviceLevel(ServiceLevel.STANDARD)
                .usage(Usage.PRODUCTION)
                .billingProvider(BillingProvider._ANY)
                .billingAccountId("sellerAcctId")
                .beginning(NOW)
                .ending(NOW)
                .build(),
            Sort.by(Subscription_.START_DATE).descending());
    assertEquals(0, result.size());
  }

  @Transactional
  @Test
  void doesNotMatchMismatchedBillingAccountId() {
    Offering offering = createOffering("testSku", "Test SKU", 1066, null, null, null);
    offeringRepo.save(offering);

    Subscription subscription = createSubscription("1", "1000", "123", "sellerAcctId");
    subscription.setOffering(offering);
    subscriptionRepo.saveAndFlush(subscription);

    Offering o1 =
        createOffering("testSku1", "Test SKU 1", 1, ServiceLevel.STANDARD, Usage.PRODUCTION, "ocp");
    offeringRepo.save(o1);
    Offering o2 =
        createOffering("testSku2", "Test SKU 2", 1, ServiceLevel.PREMIUM, Usage.PRODUCTION, "ocp");
    offeringRepo.saveAndFlush(o2);

    Set<String> productNames = Set.of("Test SKU 1");
    var resultList =
        subscriptionRepo.findByCriteria(
            DbReportCriteria.builder()
                .productNames(productNames)
                .serviceLevel(ServiceLevel.STANDARD)
                .usage(Usage.PRODUCTION)
                .billingProvider(BillingProvider._ANY)
                .billingAccountId("wrongSellerAccount")
                .beginning(NOW)
                .ending(NOW)
                .build(),
            Sort.by(Subscription_.START_DATE).descending());

    assertEquals(0, resultList.size());
  }

  @Transactional
  @Test
  void removeAllButMostRecentMarketplaceSubscriptions() {
    Subscription subscription1 =
        createSubscription("1", "1000", "123", "sellerAcctId", NOW.minusDays(30), NOW.plusDays(10));
    Subscription subscription2 =
        createSubscription("1", "1000", "234", "sellerAcctId", NOW, NOW.plusDays(30));
    Offering offering =
        createOffering("testSku1", "Test SKU 1", 1, ServiceLevel.STANDARD, Usage.PRODUCTION, "ocp");
    List<Subscription> subscriptions = List.of(subscription1, subscription2);
    subscriptions.forEach(x -> x.setOffering(offering));
    offeringRepo.save(offering);
    subscriptionRepo.saveAllAndFlush(subscriptions);

    Set<String> productNames = Set.of("Test SKU 1");

    var resultList =
        subscriptionRepo.findByCriteria(
            DbReportCriteria.builder()
                .productNames(productNames)
                .serviceLevel(ServiceLevel.STANDARD)
                .usage(Usage.PRODUCTION)
                .billingProvider(BillingProvider._ANY)
                .billingAccountId("sellerAcctId")
                .beginning(NOW)
                .ending(NOW)
                .build(),
            Sort.by(Subscription_.START_DATE).descending());

    assertEquals(2, resultList.size());

    var result1 = resultList.get(0);
    var result2 = resultList.get(1);

    assertTrue(result1.getStartDate().isAfter(result2.getStartDate()));
  }

  @Transactional
  @Test
  void findsAllSubscriptionsForSla() {
    Offering mct3718 =
        createOffering(
            "MCT3718", "MCT3718 SKU", 1066, ServiceLevel.SELF_SUPPORT, Usage.PRODUCTION, "ROLE");
    offeringRepo.save(mct3718);

    for (int i = 0; i < 5; i++) {
      Subscription subscription =
          createSubscription("1", "1001", String.valueOf(new Random().nextInt()), "sellerAcctId");
      subscription.setOffering(mct3718);
      subscriptionRepo.saveAndFlush(subscription);
    }
    var criteria = DbReportCriteria.builder().serviceLevel(ServiceLevel.SELF_SUPPORT).build();

    var result = subscriptionRepo.findByCriteria(criteria, Sort.unsorted());
    assertEquals(5, result.size());
  }

  @Transactional
  @Test
  void findsAllSubscriptionsForAGivenSku() {
    Offering mct3718 = createOffering("MCT3718", "MCT3718 SKU", 1066, null, null, null);
    Offering rh00798 = createOffering("RH00798", "RH00798 SKU", 1512, null, null, null);
    offeringRepo.saveAllAndFlush(List.of(mct3718, rh00798));

    for (int i = 0; i < 5; i++) {
      Subscription subscription1 =
          createSubscription("1", "1001", String.valueOf(new Random().nextInt()), "sellerAcctId");
      subscription1.setOffering(mct3718);

      Subscription subscription2 =
          createSubscription("1", "1001", String.valueOf(new Random().nextInt()), "sellerAcctId");
      subscription2.setOffering(rh00798);

      Subscription subscription3 =
          createSubscription("2", "1002", String.valueOf(new Random().nextInt()), "sellerAcctId");
      subscription3.setOffering(mct3718);
      subscriptionRepo.saveAll(List.of(subscription1, subscription2, subscription3));
    }

    var result = subscriptionRepo.findByOfferingSku("MCT3718", Pageable.ofSize(5));
    assertEquals(5, result.stream().count());

    result = subscriptionRepo.findByOfferingSku("MCT3718", Pageable.unpaged());
    assertEquals(10, result.stream().count());
  }

  @Transactional
  @Test
  void findsUnlimitedSubscriptions() {
    var s1 = createSubscription("org123", "account123", "sub123", "seller123");
    var s2 = createSubscription("org123", "account123", "sub321", "seller123");
    var offering1 = createOffering("testSkuUnlimited", "TestSKUUnlimited", 1066, null, null, null);
    offering1.setHasUnlimitedUsage(true);
    List.of(s1, s2).forEach(x -> x.setOffering(offering1));

    var s3 = createSubscription("org123", "account123", "sub456", "seller123");
    var s4 = createSubscription("org123", "account123", "sub678", "seller123");
    var offering2 = createOffering("testSkuLimited", "TestSKULimited", 1066, null, null, null);
    offering2.setHasUnlimitedUsage(false);
    List.of(s3, s4).forEach(x -> x.setOffering(offering2));

    offeringRepo.saveAll(List.of(offering1, offering2));
    subscriptionRepo.saveAllAndFlush(List.of(s1, s2, s3, s4));

    var criteria = DbReportCriteria.builder().orgId("org123").build();
    var result = subscriptionRepo.findUnlimited(criteria);
    assertThat(result, Matchers.containsInAnyOrder(s1, s2));
  }

  @Transactional
  @Test
  void testSubscriptionIsActive() {
    // Because the findActiveSubscription query uses CURRENT_TIMESTAMP,
    // reset NOW so that it is current and not fixed.
    NOW = OffsetDateTime.now();

    var s1 = createSubscription("org123", "account123", "sub123", "seller123");
    var s2 = createSubscription("org123", "account123", "sub321", "seller123");
    s2.setEndDate(null);

    var offering1 = createOffering("testSkuUnlimited", "TestSKUUnlimited", 1066, null, null, null);
    List.of(s1, s2).forEach(x -> x.setOffering(offering1));

    offeringRepo.save(offering1);
    subscriptionRepo.saveAllAndFlush(List.of(s1, s2));

    assertTrue(subscriptionRepo.findActiveSubscription(s1.getSubscriptionId()).isPresent());
    assertTrue(subscriptionRepo.findActiveSubscription(s2.getSubscriptionId()).isPresent());
  }

  @Transactional
  @Test
  void testFindBySpecificationWhenSubscriptionEndDateIsNull() {
    var s1 = createSubscription("org123", "account123", "sub123", "seller123");

    var s2 = createSubscription("org123", "account123", "sub321", "seller123");
    s2.setEndDate(null);

    var offering1 = createOffering("testSkuUnlimited", "TestSKUUnlimited", 1066, null, null, null);
    List.of(s1, s2).forEach(x -> x.setOffering(offering1));

    offeringRepo.save(offering1);
    subscriptionRepo.saveAllAndFlush(List.of(s1, s2));

    var resultList =
        subscriptionRepo.findByCriteria(
            DbReportCriteria.builder()
                .orgId("org123")
                .beginning(NOW)
                .ending(NOW.plusDays(1))
                .build(),
            Sort.by(Subscription_.START_DATE).descending());
    assertEquals(2, resultList.size());
    assertTrue(resultList.contains(s2));
    assertTrue(resultList.contains(s1));
    assertThat(resultList, Matchers.containsInAnyOrder(s1, s2));
  }

  private Offering createOffering(
      String sku, String productName, int productId, ServiceLevel sla, Usage usage, String role) {
    return Offering.builder()
        .sku(sku)
        .productName(productName)
        .productIds(Set.of(productId))
        .serviceLevel(sla)
        .usage(usage)
        .role(role)
        .build();
  }

  private Subscription createSubscription(
      String orgId, String accountNumber, String subId, String billingAccountId) {

    // Truncate to avoid issues around nanosecond mismatches -- HSQLDB doesn't store timestamps
    // at the same resolution as the JVM
    OffsetDateTime startDate = NOW.truncatedTo(ChronoUnit.SECONDS);
    return createSubscription(
        orgId, accountNumber, subId, billingAccountId, startDate, startDate.plusDays(30));
  }

  private Subscription createSubscription(
      String orgId,
      String accountNumber,
      String subId,
      String billingAccountId,
      OffsetDateTime startDate,
      OffsetDateTime endDate) {

    Subscription subscription = new Subscription();
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
