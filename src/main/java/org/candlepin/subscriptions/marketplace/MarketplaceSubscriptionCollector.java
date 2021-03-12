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

import org.candlepin.subscriptions.marketplace.api.resources.MarketplaceApi;
import org.candlepin.subscriptions.subscription.SubscriptionDtoUtil;
import org.candlepin.subscriptions.subscription.api.model.ExternalReference;
import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class responsible for communicating with the Marketplace API and fetching the subscription ID.
 */
@Component
public class MarketplaceSubscriptionCollector {
    @SuppressWarnings("java:S1068") // Unused field; Remove after implementing
    private final MarketplaceApi marketplaceApi;
    private final MarketplaceProperties properties;

    @Autowired
    public MarketplaceSubscriptionCollector(MarketplaceApi marketplaceApi,
        MarketplaceProperties properties) {
        this.marketplaceApi = marketplaceApi;
        this.properties = properties;
    }

    @SuppressWarnings("java:S1172") // Unused parameters; remove after implementing
    public List<Subscription> fetchSubscription(String orgId, Key usageKey) {
        var s = new Subscription();
        var ref = new ExternalReference();
        ref.setSubscriptionID(properties.getDummyId());

        Map<String, ExternalReference> refMap = new HashMap<>();
        refMap.put(SubscriptionDtoUtil.MARKETPLACE, ref);
        s.setExternalReferences(refMap);

        return Collections.singletonList(s);
    }
}
