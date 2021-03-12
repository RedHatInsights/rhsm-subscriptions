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
package org.candlepin.subscriptions.marketplace;

import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Class responsible for searching Swatch database for subscriptionIds corresponding to usage keys and if
 * none is found, delegates fetching the subscriptionId to the {@link MarketplaceSubscriptionCollector}.
 */
@Component
public class MarketplaceSubscriptionIdProvider {
    private static final Logger log = LoggerFactory.getLogger(MarketplaceSubscriptionIdProvider.class);

    private final MarketplaceSubscriptionCollector collector;
    private final SubscriptionRepository subscriptionRepo;
    private final SubscriptionSyncController syncController;

    @Autowired
    public MarketplaceSubscriptionIdProvider(MarketplaceSubscriptionCollector collector,
        SubscriptionRepository subscriptionRepo, SubscriptionSyncController syncController) {
        this.collector = collector;
        this.subscriptionRepo = subscriptionRepo;
        this.syncController = syncController;
    }

    public Optional<Object> findSubscriptionId(String accountNumber, Key usageKey,
        OffsetDateTime rangeStart, OffsetDateTime rangeEnd) {
        Assert.isTrue(Usage._ANY != usageKey.getUsage(), "Usage cannot be _ANY");
        Assert.isTrue(ServiceLevel._ANY != usageKey.getSla(), "Service Level cannot be _ANY");

        List<Subscription> result =
            subscriptionRepo.findSubscriptionByAccountAndUsageKey(accountNumber, usageKey);
        result = filterByDateRange(result, rangeStart, rangeEnd);

        if (result.isEmpty()) {
            /* If we are missing the subscription, call out to the MarketplaceSubscriptionCollector
               to fetch from Marketplace.  Sync all those subscriptions. Query again. */
            var subscriptions = collector.fetchSubscription(accountNumber, usageKey);
            subscriptions.forEach(syncController::syncSubscription);
            result = subscriptionRepo.findSubscriptionByAccountAndUsageKey(accountNumber, usageKey);
            result = filterByDateRange(result, rangeStart, rangeEnd);
        }

        if (result.isEmpty()) {
            return Optional.empty();
        }

        if (result.size() > 1) {
            log.warn("Multiple subscriptions found for account {} with key {}. Selecting first result",
                accountNumber, usageKey);
        }
        return Optional.of(result.get(0).getMarketplaceSubscriptionId());
    }

    private List<Subscription> filterByDateRange(List<Subscription> result, OffsetDateTime rangeStart,
        OffsetDateTime rangeEnd) {
        return result.stream()
            .filter(x -> rangeStart.isBefore(x.getStartDate()) && rangeEnd.isAfter(x.getEndDate()))
            .collect(Collectors.toList());
    }
}
