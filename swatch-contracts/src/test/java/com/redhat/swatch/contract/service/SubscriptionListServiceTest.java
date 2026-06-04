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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.contract.openapi.model.Subscription;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SubscriptionListServiceTest {

  @Inject SubscriptionListService subscriptionListService;
  @Inject SubscriptionRepository subscriptionRepository;
  @Inject OfferingRepository offeringRepository;

  @TestTransaction
  @Test
  void testGetSubscriptionsByOrgIdReturnsEmptyListWhenNoSubscriptions() {
    List<Subscription> result = subscriptionListService.getSubscriptionsByOrgId("nonExistentOrg");

    assertTrue(result.isEmpty());
  }

  @TestTransaction
  @Test
  void testGetSubscriptionsByOrgIdReturnsSingleSubscription() {
    OfferingEntity offering = createOffering("TEST-SKU", "testProduct", 100);
    offeringRepository.persistAndFlush(offering);

    SubscriptionEntity subscription = createSubscription("org123", "sub123", offering);
    subscriptionRepository.persistAndFlush(subscription);

    List<Subscription> result = subscriptionListService.getSubscriptionsByOrgId("org123");

    assertEquals(1, result.size());
    assertEquals("sub123", result.get(0).getSubscriptionId());
    assertEquals("org123", result.get(0).getOrgId());
    assertEquals("TEST-SKU", result.get(0).getSku());
  }

  @TestTransaction
  @Test
  void testGetSubscriptionsByOrgIdReturnsMultipleSubscriptions() {
    OfferingEntity offering1 = createOffering("SKU-001", "product1", 100);
    OfferingEntity offering2 = createOffering("SKU-002", "product2", 200);
    offeringRepository.persist(offering1);
    offeringRepository.persistAndFlush(offering2);

    SubscriptionEntity sub1 = createSubscription("org456", "sub001", offering1);
    SubscriptionEntity sub2 = createSubscription("org456", "sub002", offering2);
    SubscriptionEntity sub3 = createSubscription("org456", "sub003", offering1);
    subscriptionRepository.persist(sub1);
    subscriptionRepository.persist(sub2);
    subscriptionRepository.persistAndFlush(sub3);

    List<Subscription> result = subscriptionListService.getSubscriptionsByOrgId("org456");

    assertEquals(3, result.size());
  }

  @TestTransaction
  @Test
  void testGetSubscriptionsByOrgIdFiltersCorrectly() {
    OfferingEntity offering = createOffering("TEST-SKU", "testProduct", 100);
    offeringRepository.persistAndFlush(offering);

    SubscriptionEntity sub1 = createSubscription("org111", "sub111", offering);
    SubscriptionEntity sub2 = createSubscription("org222", "sub222", offering);
    SubscriptionEntity sub3 = createSubscription("org111", "sub333", offering);
    subscriptionRepository.persist(sub1);
    subscriptionRepository.persist(sub2);
    subscriptionRepository.persistAndFlush(sub3);

    List<Subscription> result = subscriptionListService.getSubscriptionsByOrgId("org111");

    assertEquals(2, result.size());
    assertTrue(result.stream().allMatch(s -> "org111".equals(s.getOrgId())));
    assertTrue(result.stream().anyMatch(s -> "sub111".equals(s.getSubscriptionId())));
    assertTrue(result.stream().anyMatch(s -> "sub333".equals(s.getSubscriptionId())));
  }

  @TestTransaction
  @Test
  void testGetSubscriptionsByOrgIdWithDifferentOfferings() {
    OfferingEntity offering1 = createOffering("PREMIUM-SKU", "premium", 500);
    OfferingEntity offering2 = createOffering("STANDARD-SKU", "standard", 300);
    offeringRepository.persist(offering1);
    offeringRepository.persistAndFlush(offering2);

    SubscriptionEntity sub1 = createSubscription("orgABC", "subPremium", offering1);
    SubscriptionEntity sub2 = createSubscription("orgABC", "subStandard", offering2);
    subscriptionRepository.persist(sub1);
    subscriptionRepository.persistAndFlush(sub2);

    List<Subscription> result = subscriptionListService.getSubscriptionsByOrgId("orgABC");

    assertEquals(2, result.size());
    assertTrue(result.stream().anyMatch(s -> "PREMIUM-SKU".equals(s.getSku())));
    assertTrue(result.stream().anyMatch(s -> "STANDARD-SKU".equals(s.getSku())));
  }

  private OfferingEntity createOffering(String sku, String productTag, int productId) {
    return OfferingEntity.builder()
        .sku(sku)
        .productTags(Set.of(productTag))
        .productIds(Set.of(productId))
        .build();
  }

  private SubscriptionEntity createSubscription(
      String orgId, String subscriptionId, OfferingEntity offering) {
    OffsetDateTime now = OffsetDateTime.now();
    SubscriptionEntity subscription = new SubscriptionEntity();
    subscription.setSubscriptionId(subscriptionId);
    subscription.setOrgId(orgId);
    subscription.setOffering(offering);
    subscription.setQuantity(10L);
    subscription.setStartDate(now);
    subscription.setEndDate(now.plusDays(365));
    subscription.setSubscriptionNumber(subscriptionId + "-NUM");
    subscription.setBillingProvider(BillingProvider.RED_HAT);
    subscription.setBillingAccountId("account-" + orgId);
    subscription.setBillingProviderId("provider-" + orgId);
    return subscription;
  }
}
