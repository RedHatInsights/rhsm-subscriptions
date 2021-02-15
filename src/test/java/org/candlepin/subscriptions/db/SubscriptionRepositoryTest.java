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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.subscriptions.db.model.Subscription;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.OffsetDateTime;

import javax.transaction.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class SubscriptionRepositoryTest {

    private static final OffsetDateTime NOW = OffsetDateTime.now();

    @Autowired
    SubscriptionRepository subject;

    @Transactional
    @Test
    void canInsertAndRetrieveSubscriptions() {
        final Subscription subscription = createSubscription("1", "testsku", "123");
        subject.saveAndFlush(subscription);

        final Subscription retrieved = subject.findActiveSubscription("123").orElse(null);

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

    private Subscription createSubscription(String orgId, String sku, String subId) {
        final Subscription subscription = new Subscription();
        subscription.setSubscriptionId(subId);
        subscription.setOwnerId(orgId);
        subscription.setQuantity(4L);
        subscription.setSku(sku);
        subscription.setStartDate(NOW);
        subscription.setEndDate(NOW.plusDays(30));

        return subscription;
    }
}
