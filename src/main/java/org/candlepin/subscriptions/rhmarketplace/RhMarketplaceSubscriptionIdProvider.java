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
import java.util.Set;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Class responsible for searching Swatch database for subscriptionIds corresponding to usage keys
 * and if none is found, delegates fetching the subscriptionId to the {@link
 * RhMarketplaceSubscriptionCollector}.
 */
@Component
public class RhMarketplaceSubscriptionIdProvider {
  private static final Logger log =
      LoggerFactory.getLogger(RhMarketplaceSubscriptionIdProvider.class);

  private final RhMarketplaceSubscriptionCollector collector;
  private final SubscriptionRepository subscriptionRepo;
  private final SubscriptionSyncController syncController;
  private final TagProfile tagProfile;
  private final Counter missingSubscriptionCounter;
  private final Counter ambiguousSubscriptionCounter;

  @Autowired
  public RhMarketplaceSubscriptionIdProvider(
      RhMarketplaceSubscriptionCollector collector,
      SubscriptionRepository subscriptionRepo,
      SubscriptionSyncController syncController,
      TagProfile tagProfile,
      MeterRegistry meterRegistry) {
    this.collector = collector;
    this.subscriptionRepo = subscriptionRepo;
    this.syncController = syncController;
    this.tagProfile = tagProfile;
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
    Assert.isTrue(Usage._ANY != usageKey.getUsage(), "Usage cannot be _ANY");
    Assert.isTrue(ServiceLevel._ANY != usageKey.getSla(), "Service Level cannot be _ANY");

    String productId = usageKey.getProductId();
    Set<String> productNames = tagProfile.getOfferingProductNamesForTag(productId);
    if (productNames.isEmpty()) {
      log.warn("No product names configured for tag: {}", productId);
      return Optional.empty();
    }

    List<Subscription> result =
        fetchSubscriptions(accountNumber, usageKey, productNames, rangeStart, rangeEnd);

    if (result.isEmpty()) {
      /* If we are missing the subscription, call out to the RhMarketplaceSubscriptionCollector
      to fetch from Marketplace.  Sync all those subscriptions. Query again. */
      log.info("Syncing subscriptions for account {} using orgId {}", accountNumber, orgId);
      var subscriptions = collector.requestSubscriptions(orgId);
      subscriptions.forEach(syncController::syncSubscription);
      result = fetchSubscriptions(accountNumber, usageKey, productNames, rangeStart, rangeEnd);
    }

    if (result.isEmpty()) {
      missingSubscriptionCounter.increment();
      log.error(
          "No subscription found for account {} with key {} and product names {}",
          accountNumber,
          usageKey,
          productNames);
      return Optional.empty();
    }

    if (result.size() > 1) {
      ambiguousSubscriptionCounter.increment();
      log.warn(
          "Multiple subscriptions found for account {} with key {} and product names {}."
              + " Selecting first result",
          accountNumber,
          usageKey,
          productNames);
    }
    return Optional.of(result.get(0).getMarketplaceSubscriptionId());
  }

  protected List<Subscription> fetchSubscriptions(
      String accountNumber,
      Key usageKey,
      Set<String> productNames,
      OffsetDateTime rangeStart,
      OffsetDateTime rangeEnd) {
    return subscriptionRepo.findByAccountAndProductNameAndServiceLevel(
        accountNumber, usageKey, productNames, rangeStart, rangeEnd);
  }
}
