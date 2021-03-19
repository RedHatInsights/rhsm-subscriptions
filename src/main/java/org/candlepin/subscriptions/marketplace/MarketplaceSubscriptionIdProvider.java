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
import org.candlepin.subscriptions.files.ProductProfile;
import org.candlepin.subscriptions.files.ProductProfileRegistry;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final ProductProfileRegistry profileRegistry;
    private final MarketplaceProperties properties;

    @Autowired
    public MarketplaceSubscriptionIdProvider(MarketplaceSubscriptionCollector collector,
        SubscriptionRepository subscriptionRepo, SubscriptionSyncController syncController,
        ProductProfileRegistry profileRegistry, MarketplaceProperties properties) {
        this.collector = collector;
        this.subscriptionRepo = subscriptionRepo;
        this.syncController = syncController;
        this.profileRegistry = profileRegistry;
        this.properties = properties;
    }

    public Optional<String> findSubscriptionId(String accountNumber, Key usageKey,
        OffsetDateTime rangeStart, OffsetDateTime rangeEnd) {
        Assert.isTrue(Usage._ANY != usageKey.getUsage(), "Usage cannot be _ANY");
        Assert.isTrue(ServiceLevel._ANY != usageKey.getSla(), "Service Level cannot be _ANY");

        String productId = usageKey.getProductId();
        ProductProfile profile = profileRegistry.findProfileForSwatchProductId(productId);
        Set<String> roles = profile.getRolesBySwatchProduct().getOrDefault(productId, Collections.emptySet());

        List<Subscription> result = fetchSubscriptions(accountNumber, usageKey, roles, rangeStart, rangeEnd);

        if (result.isEmpty()) {
            /* If we are missing the subscription, call out to the MarketplaceSubscriptionCollector
               to fetch from Marketplace.  Sync all those subscriptions. Query again. */
            var subscriptions = collector.fetchSubscription(accountNumber, usageKey);
            subscriptions.forEach(syncController::syncSubscription);
            result = fetchSubscriptions(accountNumber, usageKey, roles, rangeStart, rangeEnd);
        }

        if (result.isEmpty()) {
            return Optional.of(properties.getDummyId());
        }

        if (result.size() > 1) {
            log.warn("Multiple subscriptions found for account {} with key {} and roles {}. Selecting first" +
                " result", accountNumber, usageKey, roles);
        }
        return Optional.of(result.get(0).getMarketplaceSubscriptionId());
    }

    protected List<Subscription> fetchSubscriptions(String accountNumber, Key usageKey, Set<String> roles,
        OffsetDateTime rangeStart, OffsetDateTime rangeEnd) {
        List<Subscription> result =
            subscriptionRepo.findSubscriptionByAccountAndUsageKey(accountNumber, usageKey, roles);
        result = filterByDateRange(result, rangeStart, rangeEnd);
        result = filterByMissingMarketplaceSubscriptionId(result);
        return result;
    }

    private List<Subscription> filterByMissingMarketplaceSubscriptionId(List<Subscription> result) {
        return result.stream()
            .filter(x -> StringUtils.hasText(x.getMarketplaceSubscriptionId()))
            .collect(Collectors.toList());
    }

    private List<Subscription> filterByDateRange(List<Subscription> result, OffsetDateTime rangeStart,
        OffsetDateTime rangeEnd) {
        return result.stream()
            // Ensure that the subscription range covers at least the entire time range we're given
            // !isBefore => rangeStart is either equal to or after subscription.getStartDate()
            // !isAfter => rangeEnd is either equal to or before subscription.getEndDate()
            .filter(x -> !rangeStart.isBefore(x.getStartDate()) && !rangeEnd.isAfter(x.getEndDate()))
            .collect(Collectors.toList());
    }
}
