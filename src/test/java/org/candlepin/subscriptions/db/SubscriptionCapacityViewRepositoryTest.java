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

import org.candlepin.subscriptions.db.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DirtiesContext
class SubscriptionCapacityViewRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  private static final OffsetDateTime LONG_AGO =
      OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  private static final OffsetDateTime NOWISH =
      OffsetDateTime.of(2019, 6, 23, 0, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime FAR_FUTURE =
      OffsetDateTime.of(2099, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
  static final String ACCOUNT_NUMBER = "account";
  static final String PRODUCT_ID = "123";
  static final String SUBSCRIPTION_ID = "subscription";
  static final String OWNER_ID = "ownerId";

  @Autowired private SubscriptionCapacityViewRepository repository;

  @Autowired private SubscriptionCapacityRepository subscriptionCapacityRepository;

  @Autowired private SubscriptionRepository subscriptionRepository;

  @Autowired OfferingRepository offeringRepository;

  @Transactional
  @Test
  void aTest() {
    Subscription subscription = createSubscription("1", "1000", "testSku1", "123");
    subscriptionRepository.saveAndFlush(subscription);

    Offering o1 = createOffering("testSku1", 1, ServiceLevel.STANDARD, Usage.PRODUCTION, "ocp");
    offeringRepository.save(o1);
  }

  @Transactional
  @Test
  void testCanQueryBySlaEmpty() {

    SubscriptionCapacity premium = createUnpersisted(NOWISH.plusDays(1), FAR_FUTURE.plusDays(1));
    premium.setSubscriptionId("premium");
    premium.setServiceLevel(ServiceLevel.PREMIUM);
    premium.setSku("testSku1");
    premium.setProductId("100");
    subscriptionRepository.saveAndFlush(
            createSubscription(
                    OWNER_ID, ACCOUNT_NUMBER,
                    premium.getSku(), premium.getSubscriptionId(),
                    premium.getBeginDate(), premium.getEndDate()));

    subscriptionCapacityRepository.saveAndFlush(premium);
    offeringRepository.saveAndFlush(createOffering(premium.getSku(), Integer.parseInt(premium.getProductId()), null, premium.getUsage(), "role1"));

   List<SubscriptionCapacity> subscriptionCapacities = subscriptionCapacityRepository.findAll();
   List<Offering> offerings = offeringRepository.findAll();
   List<Subscription> subscriptions = subscriptionRepository.findAll();
    List<SubscriptionCapacityView> found = repository.findByKeyOwnerIdAndKeyProductIdAndServiceLevelAndUsage(
            premium.getOwnerId(),
            premium.getProductId(),
            premium.getServiceLevel(),
            premium.getUsage());
    assertEquals(1, found.size());
  }

  private SubscriptionCapacity createUnpersisted(OffsetDateTime begin, OffsetDateTime end) {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setAccountNumber(ACCOUNT_NUMBER);
    capacity.setProductId(PRODUCT_ID);
    capacity.setSubscriptionId(SUBSCRIPTION_ID);
    capacity.setBeginDate(begin);
    capacity.setEndDate(end);
    capacity.setHasUnlimitedGuestSockets(false);
    capacity.setOwnerId(OWNER_ID);
    capacity.setPhysicalSockets(4);
    capacity.setVirtualSockets(20);
    capacity.setPhysicalCores(8);
    capacity.setVirtualCores(40);
    capacity.setServiceLevel(ServiceLevel.PREMIUM);
    capacity.setUsage(Usage.PRODUCTION);
    capacity.setSku("testsku1");
    return capacity;
  }

  private Offering createOffering(
          String sku, int productId, ServiceLevel sla, Usage usage, String role) {
    Offering o = new Offering();
    o.setSku(sku);
    o.setProductIds(Set.of(productId));
    o.setServiceLevel(sla);
    o.setUsage(usage);
    o.setRole(role);
    o.setProductName("Description of sku");
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
    subscription.setSubscriptionNumber(subId + "1");

    return subscription;
  }
}
