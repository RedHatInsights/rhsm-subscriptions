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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Class responsible for searching Swatch database for subscriptionIds corresponding to usage keys
 * and if none is found, delegates fetching the subscriptionId to SubscriptionSyncController.
 */
@Component
public class RhMarketplaceSubscriptionIdProvider {
  private static final Logger log =
      LoggerFactory.getLogger(RhMarketplaceSubscriptionIdProvider.class);

  private final SubscriptionSyncController syncController;
  private final Counter missingSubscriptionCounter;
  private final Counter ambiguousSubscriptionCounter;

  @Autowired
  public RhMarketplaceSubscriptionIdProvider(
      SubscriptionSyncController syncController, MeterRegistry meterRegistry) {
    this.syncController = syncController;
    this.missingSubscriptionCounter =
        meterRegistry.counter("rhsm-subscriptions.marketplace.missing.subscription");
    this.ambiguousSubscriptionCounter =
        meterRegistry.counter("rhsm-subscriptions.marketplace.ambiguous.subscription");
  }

  public Optional<String> findSubscriptionId(
      String accountNumber,
      String orgId,
      Key usageKey,
      OffsetDateTime rangeStart,
      OffsetDateTime rangeEnd) {
    List<Subscription> results =
        syncController.findSubscriptionsAndSyncIfNeeded(
            accountNumber, Optional.of(orgId), usageKey, rangeStart, rangeEnd);
    if (results.isEmpty()) {
      missingSubscriptionCounter.increment();
    }
    if (results.size() > 1) {
      ambiguousSubscriptionCounter.increment();
      log.warn(
          "Multiple subscriptions found for account {} with key {} and product tag {}."
              + " Selecting first result",
          accountNumber,
          usageKey,
          usageKey.getProductId());
    }
    return results.stream().findFirst().map(Subscription::getBillingProviderId);
  }
}
