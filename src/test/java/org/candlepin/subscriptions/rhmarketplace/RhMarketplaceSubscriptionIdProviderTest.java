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
package org.candlepin.subscriptions.rhmarketplace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
@ActiveProfiles({"rh-marketplace", "test"})
class RhMarketplaceSubscriptionIdProviderTest {
  @Mock SubscriptionSyncController syncController;

  @Test
  void incrementsMissingCounter() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    RhMarketplaceSubscriptionIdProvider idProvider =
        new RhMarketplaceSubscriptionIdProvider(syncController, meterRegistry);
    when(syncController.findSubscriptionsAndSyncIfNeeded(any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    Optional<String> subscriptionId =
        idProvider.findSubscriptionId(
            "account123",
            "org123",
            new Key(
                "productId", ServiceLevel.PREMIUM, Usage.PRODUCTION, BillingProvider._ANY, "_ANY"),
            OffsetDateTime.MIN,
            OffsetDateTime.MAX);
    Counter counter = meterRegistry.counter("rhsm-subscriptions.marketplace.missing.subscription");
    assertEquals(1.0, counter.count());
    assertTrue(subscriptionId.isEmpty());
  }

  @Test
  void incrementsAmbiguousCounter() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    RhMarketplaceSubscriptionIdProvider idProvider =
        new RhMarketplaceSubscriptionIdProvider(syncController, meterRegistry);
    Subscription sub1 = new Subscription();
    sub1.setBillingProviderId("foo");
    Subscription sub2 = new Subscription();
    sub2.setBillingProviderId("bar");
    when(syncController.findSubscriptionsAndSyncIfNeeded(any(), any(), any(), any(), any()))
        .thenReturn(List.of(sub1, sub2));
    Optional<String> subscriptionId =
        idProvider.findSubscriptionId(
            "account123",
            "org123",
            new Key(
                "productId", ServiceLevel.PREMIUM, Usage.PRODUCTION, BillingProvider._ANY, "_ANY"),
            OffsetDateTime.MIN,
            OffsetDateTime.MAX);
    Counter counter =
        meterRegistry.counter("rhsm-subscriptions.marketplace.ambiguous.subscription");
    assertEquals(1.0, counter.count());
    assertEquals("foo", subscriptionId.orElseThrow());
  }
}
