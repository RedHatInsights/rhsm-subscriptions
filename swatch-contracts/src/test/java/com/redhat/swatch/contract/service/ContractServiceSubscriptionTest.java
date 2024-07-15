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

import com.redhat.swatch.contract.BaseUnitTest;
import com.redhat.swatch.contract.repository.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ContractServiceSubscriptionTest extends BaseUnitTest {

  private static final String ORG_ID = "org123";
  private static final String SKU = "RH000000";
  private static final String PRODUCT_TAG = "MH123";
  private static final String SUBSCRIPTION_NUMBER = "subs123";
  private static final String SUBSCRIPTION_ID = "456";
  private static final OffsetDateTime DEFAULT_START_DATE =
      OffsetDateTime.parse("2023-06-09T13:59:43.035365Z");

  @Inject ContractService contractService;
  @Inject ContractRepository contractRepository;
  @Inject OfferingRepository offeringRepository;
  @Inject SubscriptionRepository subscriptionRepository;

  private final OfferingEntity offeringEntity =
      OfferingEntity.builder().sku(SKU).productTags(Set.of(PRODUCT_TAG)).build();

  @Transactional
  @BeforeEach
  public void setup() {
    contractRepository.deleteAll();
    offeringRepository.deleteAll();
    subscriptionRepository.deleteAll();
    offeringRepository.persist(offeringEntity);
  }

  @Test
  @Transactional
  void testDeleteContractDeletesSubscription() {
    givenExistingContract();
    givenExistingSubscription();
    assertEquals(
        1,
        subscriptionRepository.findAll().stream()
            .findFirst()
            .get()
            .getSubscriptionProductIds()
            .size());
    contractService.deleteContractsByOrgId(ORG_ID);
    assertEquals(0, subscriptionRepository.findAll().stream().count());
  }

  private void givenExistingSubscription() {
    SubscriptionEntity subscription =
        SubscriptionEntity.builder()
            .subscriptionId(SUBSCRIPTION_ID)
            .startDate(DEFAULT_START_DATE)
            .subscriptionNumber(SUBSCRIPTION_NUMBER)
            .offering(offeringEntity)
            .orgId(ORG_ID)
            .build();
    var subscriptionProductId =
        SubscriptionProductIdEntity.builder()
            .productId(PRODUCT_TAG)
            .subscription(subscription)
            .build();
    subscription.setSubscriptionProductIds(Set.of(subscriptionProductId));
    subscriptionRepository.persist(subscription);
  }

  private void givenExistingContract() {
    ContractEntity contract =
        ContractEntity.builder()
            .subscriptionNumber(SUBSCRIPTION_NUMBER)
            .orgId(ORG_ID)
            .startDate(DEFAULT_START_DATE)
            .offering(offeringEntity)
            .uuid(UUID.randomUUID())
            .vendorProductCode("testing")
            .lastUpdated(DEFAULT_START_DATE)
            .billingProvider("aws")
            .billingAccountId("test")
            .billingProviderId("test;test;test")
            .build();
    contractRepository.persist(contract);
  }
}
