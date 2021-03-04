/*
 * Copyright (c) 2021 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;

import javax.transaction.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class SubscriptionRepositoryTest {

    private static final OffsetDateTime NOW = OffsetDateTime.now();

    @Autowired
    SubscriptionRepository subscriptionRepo;

    @Autowired
    OfferingRepository offeringRepo;

    @Transactional
    @Test
    void canInsertAndRetrieveSubscriptions() {
        Subscription subscription = createSubscription("1", "1000", "testsku", "123");
        subscriptionRepo.saveAndFlush(subscription);

        Subscription retrieved = subscriptionRepo.findActiveSubscription("123").orElse(null);

        // because of an issue with precision related to findActiveSubscription passing the entity cache,
        // we'll have to check fields
        assertEquals(subscription.getSubscriptionId(), retrieved.getSubscriptionId());
        assertEquals(subscription.getSku(), retrieved.getSku());
        assertEquals(subscription.getOwnerId(), retrieved.getOwnerId());
        assertEquals(subscription.getQuantity(), retrieved.getQuantity());
        assertTrue(Duration.between(subscription.getStartDate(), retrieved.getStartDate())
            .abs().getSeconds() < 1L);
        assertTrue(Duration.between(subscription.getEndDate(), retrieved.getEndDate())
            .abs().getSeconds() < 1L);
    }

    @Transactional
    @Test
    void canMatchOfferings() {
        Subscription subscription = createSubscription("1", "1000", "testSku1", "123");
        subscriptionRepo.saveAndFlush(subscription);

        Offering o1 = createOffering("testSku1", 1, ServiceLevel.STANDARD, Usage.PRODUCTION, "testRole");
        offeringRepo.save(o1);
        Offering o2 = createOffering("testSku2", 1, ServiceLevel.PREMIUM, Usage.PRODUCTION, "premiumRole");
        offeringRepo.saveAndFlush(o2);

        UsageCalculation.Key key = new Key(String.valueOf(1), ServiceLevel.STANDARD, Usage.PRODUCTION);
        Subscription result = subscriptionRepo.findSubscriptionByAccountAndUsageKey("1000", key)
            .orElseThrow(() -> new AssertionFailedError("No record found"));

        assertEquals("testSku1", result.getSku());
        assertEquals("1000", result.getAccountNumber());
    }

    @Transactional
    @Test
    void doesNotMatchMismatchedSkusOfferings() {
        Subscription subscription = createSubscription("1", "1000", "testSku", "123");
        subscriptionRepo.saveAndFlush(subscription);

        Offering o1 = createOffering("otherSku1", 1, ServiceLevel.STANDARD, Usage.PRODUCTION, "testRole");
        offeringRepo.saveAndFlush(o1);
        Offering o2 = createOffering("otherSku2", 1, ServiceLevel.PREMIUM, Usage.PRODUCTION, "premiumRole");
        offeringRepo.saveAndFlush(o2);

        UsageCalculation.Key key = new Key(String.valueOf(1), ServiceLevel.STANDARD, Usage.PRODUCTION);
        Optional<Subscription> result = subscriptionRepo.findSubscriptionByAccountAndUsageKey("1000", key);
        assertTrue(result.isEmpty());
    }

    private Offering createOffering(String sku, int productId, ServiceLevel sla, Usage usage,
        String role) {
        Offering o = new Offering();
        o.setSku(sku);
        o.setProductIds(Collections.singletonList(productId));
        o.setServiceLevel(sla);
        o.setUsage(usage);
        o.setRole(role);
        return o;
    }

    private Subscription createSubscription(String orgId, String accountNumber, String sku, String subId) {
        Subscription subscription = new Subscription();
        subscription.setSubscriptionId(subId);
        subscription.setOwnerId(orgId);
        subscription.setAccountNumber(accountNumber);
        subscription.setQuantity(4L);
        subscription.setSku(sku);
        subscription.setStartDate(NOW);
        subscription.setEndDate(NOW.plusDays(30));

        return subscription;
    }


}
