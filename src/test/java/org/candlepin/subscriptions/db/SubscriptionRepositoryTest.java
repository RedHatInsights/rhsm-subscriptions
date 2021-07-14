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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import javax.transaction.Transactional;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SubscriptionRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired SubscriptionRepository subscriptionRepo;

  @Autowired OfferingRepository offeringRepo;

  @Transactional
  @Test
  void canInsertAndRetrieveSubscriptions() {
    Subscription subscription = createSubscription("1", "1000", "testsku", "123");
    subscriptionRepo.saveAndFlush(subscription);

    Subscription retrieved = subscriptionRepo.findActiveSubscription("123").orElse(null);

    // because of an issue with precision related to findActiveSubscription passing the entity
    // cache,
    // we'll have to check fields
    assertEquals(subscription.getSubscriptionId(), retrieved.getSubscriptionId());
    assertEquals(subscription.getSku(), retrieved.getSku());
    assertEquals(subscription.getOwnerId(), retrieved.getOwnerId());
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
    Subscription subscription = createSubscription("1", "1000", "testSku1", "123");
    subscriptionRepo.saveAndFlush(subscription);

    Offering o1 = createOffering("testSku1", 1, ServiceLevel.STANDARD, Usage.PRODUCTION, "ocp");
    offeringRepo.save(o1);
    Offering o2 = createOffering("testSku2", 1, ServiceLevel.PREMIUM, Usage.PRODUCTION, "ocp");
    offeringRepo.saveAndFlush(o2);

    UsageCalculation.Key key = new Key(String.valueOf(1), ServiceLevel.STANDARD, Usage.PRODUCTION);
    Set<String> roles = Set.of("ocp");
    var resultList =
        subscriptionRepo
            .findSubscriptionByAccountAndUsageKeyAndStartDateAndEndDateAndMarketplaceSubscriptionId(
                "1000", key, roles, NOW, NOW);
    assertEquals(1, resultList.size());

    var result = resultList.get(0);
    assertEquals("testSku1", result.getSku());
    assertEquals("1000", result.getAccountNumber());
  }

  @Transactional
  @Test
  void doesNotMatchMismatchedSkusOfferings() {
    Subscription subscription = createSubscription("1", "1000", "testSku", "123");
    subscriptionRepo.saveAndFlush(subscription);

    Offering o1 = createOffering("otherSku1", 1, ServiceLevel.STANDARD, Usage.PRODUCTION, "ocp");
    offeringRepo.saveAndFlush(o1);
    Offering o2 = createOffering("otherSku2", 1, ServiceLevel.PREMIUM, Usage.PRODUCTION, "ocp");
    offeringRepo.saveAndFlush(o2);

    UsageCalculation.Key key = new Key(String.valueOf(1), ServiceLevel.STANDARD, Usage.PRODUCTION);
    Set<String> roles = Set.of("ocp");
    var result =
        subscriptionRepo
            .findSubscriptionByAccountAndUsageKeyAndStartDateAndEndDateAndMarketplaceSubscriptionId(
                "1000", key, roles, NOW, NOW);
    assertEquals(0, result.size());
  }

  @Transactional
  @Test
  void removeAllButMostRecentMarketplaceSubscriptions() {
    Subscription subscription1 =
        createSubscription("1", "1000", "testSku1", "123", NOW.minusDays(30), NOW.plusDays(10));
    subscriptionRepo.saveAndFlush(subscription1);
    Subscription subscription2 =
        createSubscription("1", "1000", "testSku1", "234", NOW, NOW.plusDays(30));
    subscriptionRepo.saveAndFlush(subscription2);

    Offering offering =
        createOffering("testSku1", 1, ServiceLevel.STANDARD, Usage.PRODUCTION, "ocp");
    offeringRepo.save(offering);

    UsageCalculation.Key key = new Key(String.valueOf(1), ServiceLevel.STANDARD, Usage.PRODUCTION);
    Set<String> roles = Set.of("ocp");

    var resultList =
        subscriptionRepo
            .findSubscriptionByAccountAndUsageKeyAndStartDateAndEndDateAndMarketplaceSubscriptionId(
                "1000", key, roles, NOW, NOW);

    assertEquals(2, resultList.size());

    var result1 = resultList.get(0);
    var result2 = resultList.get(1);

    assertTrue(result1.getStartDate().isAfter(result2.getStartDate()));
  }

  private Offering createOffering(
      String sku, int productId, ServiceLevel sla, Usage usage, String role) {
    Offering o = new Offering();
    o.setSku(sku);
    o.setProductIds(Set.of(productId));
    o.setServiceLevel(sla);
    o.setUsage(usage);
    o.setRole(role);
    return o;
  }

  private Subscription createSubscription(
      String orgId, String accountNumber, String sku, String subId) {
    return createSubscription(orgId, accountNumber, sku, subId, NOW, NOW.plusDays(30));
  }

  private Subscription createSubscription(
      String orgId,
      String accountNumber,
      String sku,
      String subId,
      OffsetDateTime startDate,
      OffsetDateTime endDate) {

    Subscription subscription = new Subscription();
    subscription.setMarketplaceSubscriptionId("bananas");
    subscription.setSubscriptionId(subId);
    subscription.setOwnerId(orgId);
    subscription.setAccountNumber(accountNumber);
    subscription.setQuantity(4L);
    subscription.setSku(sku);
    subscription.setStartDate(startDate);
    subscription.setEndDate(endDate);
    subscription.setSubscriptionNumber(subId+"1");

    return subscription;
  }
}
