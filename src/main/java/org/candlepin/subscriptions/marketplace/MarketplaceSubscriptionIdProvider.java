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
import org.candlepin.subscriptions.tally.UsageCalculation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Class responsible for searching Swatch database for subscriptionIds corresponding to usage keys and if
 * none is found, delegates fetching the subscriptionId to the {@link MarketplaceSubscriptionIdCollector}.
 */
@Component
public class MarketplaceSubscriptionIdProvider {

    private final MarketplaceSubscriptionIdCollector collector;
    private final SubscriptionRepository subscriptionRepo;

    @Autowired
    public MarketplaceSubscriptionIdProvider(MarketplaceSubscriptionIdCollector collector,
        SubscriptionRepository subscriptionRepo) {
        this.collector = collector;
        this.subscriptionRepo = subscriptionRepo;
    }

    public Optional<String> findSubscriptionId(String accountNumber, UsageCalculation.Key usageKey) {
        Assert.isTrue(Usage._ANY != usageKey.getUsage(), "Usage cannot be _ANY");
        Assert.isTrue(ServiceLevel._ANY != usageKey.getSla(), "Service Level cannot be _ANY");

        Optional<Subscription> result =
            subscriptionRepo.findSubscriptionByAccountAndUsageKey(accountNumber, usageKey);

        if (result.isPresent()) {
            Subscription s = result.get();

            /* If we have the subscription, but the marketplace subscription ID didn't come with the initial
               fetch from SubscriptionService, then we call out to the MarketplaceSubscriptionIdCollector
               to fetch the ID directly from Marketplace.  Then we'll update the Subscription record so we
               only need to do the fetch once. */
            if (!StringUtils.hasText(s.getMarketplaceSubscriptionId())) {
                String missingId = collector.fetchSubscriptionId(accountNumber, usageKey);
                s.setMarketplaceSubscriptionId(missingId);
                subscriptionRepo.save(s);
            }
            return Optional.of(s.getMarketplaceSubscriptionId());
        }

        return Optional.empty();
    }
}
