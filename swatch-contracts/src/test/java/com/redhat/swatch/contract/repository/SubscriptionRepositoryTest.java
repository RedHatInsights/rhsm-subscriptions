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

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SubscriptionRepositoryTest {

  private static final String BILLING_ACCOUNT_ID = "sellerAcctId";
  private static final String PRODUCT_TAG = "rosa";

  @Inject SubscriptionRepository subscriptionRepo;
  @Inject OfferingRepository offeringRepo;

  @Transactional
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

  private OfferingEntity createOffering(String sku, int productId) {
    return OfferingEntity.builder()
        .sku(sku)
        .productIds(Set.of(productId))
        .productTags(Set.of(PRODUCT_TAG))
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
}
