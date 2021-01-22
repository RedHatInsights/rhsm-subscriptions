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

import org.candlepin.subscriptions.db.model.Subscription;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;

import javax.transaction.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class SubscriptionRepositoryTest {

  @Autowired SubscriptionRepository subject;

  @Transactional
  @Test
  void canInsertAndRetrieveSubscriptions() {
    final Subscription subscription = createSubscription("1", "testsku", "123");
    subject.saveAndFlush(subscription);

    final Subscription retrieved = subject.getOne("123");

    assertEquals(subscription, retrieved);
  }

  private Subscription createSubscription(String orgId, String sku, String subId) {
    final Subscription subscription = new Subscription();
    subscription.setSubscriptionId(subId);
    subscription.setOwnerId(orgId);
    subscription.setQuantity(4L);
    subscription.setSku(sku);
    subscription.setStartDate(OffsetDateTime.now());
    subscription.setEndDate(OffsetDateTime.now());

    return subscription;
  }
}
