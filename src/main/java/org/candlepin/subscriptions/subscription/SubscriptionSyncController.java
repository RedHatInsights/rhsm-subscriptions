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
package org.candlepin.subscriptions.subscription;

import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.subscription.api.model.ExternalReference;
import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.subscription.api.model.SubscriptionProduct;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

/**
 * Update subscriptions from subscription service responses.
 */
@Component
public class SubscriptionSyncController {
    public static final String MARKETPLACE = "ibmmarketplace";
    private final SubscriptionRepository subscriptionRepository;
    private final ApplicationClock clock;

    public SubscriptionSyncController(SubscriptionRepository subscriptionRepository, ApplicationClock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    @Transactional
    public void syncSubscription(Subscription subscription) {
        final Optional<org.candlepin.subscriptions.db.model.Subscription> entity =
            subscriptionRepository.findActiveSubscription(String.valueOf(subscription.getId()));
        if (entity.isPresent()) {
            final org.candlepin.subscriptions.db.model.Subscription dbSub = entity.get();
            if (needNewRecord(subscription, dbSub)) {
                // end the current record
                dbSub.setEndDate(OffsetDateTime.now());
                subscriptionRepository.save(dbSub);
                // create a new record
                final org.candlepin.subscriptions.db.model.Subscription newSub =
                    new org.candlepin.subscriptions.db.model.Subscription();
                newSub.setSubscriptionId(dbSub.getSubscriptionId());
                newSub.setSku(dbSub.getSku());
                newSub.setOwnerId(dbSub.getOwnerId());
                newSub.setAccountNumber(dbSub.getAccountNumber());
                newSub.setQuantity(subscription.getQuantity());

                newSub.setStartDate(OffsetDateTime.now());
                if (subscription.getEffectiveEndDate() != null) {
                    newSub.setEndDate(clock.dateFromUnix(subscription.getEffectiveEndDate()));
                }

                newSub.setMarketplaceSubscriptionId(extractMarketplaceId(subscription));

                subscriptionRepository.save(newSub);
            }
            else {
                updateSubscription(subscription, dbSub);
                subscriptionRepository.save(dbSub);
            }
        }
        else {
            // create a new record
            final org.candlepin.subscriptions.db.model.Subscription newSub =
                new org.candlepin.subscriptions.db.model.Subscription();
            newSub.setSubscriptionId(String.valueOf(subscription.getId()));
            newSub.setAccountNumber(String.valueOf(subscription.getOracleAccountNumber()));
            newSub.setMarketplaceSubscriptionId(extractMarketplaceId(subscription));
            newSub.setSku(extractSku(subscription));
            newSub.setQuantity(subscription.getQuantity());
            if (subscription.getEffectiveStartDate() != null) {
                newSub.setStartDate(clock.dateFromUnix(subscription.getEffectiveStartDate()));
            }
            if (subscription.getEffectiveEndDate() != null) {
                newSub.setEndDate(clock.dateFromUnix(subscription.getEffectiveEndDate()));
            }
            subscriptionRepository.save(newSub);
        }
    }

    /**
     * The subscription JSON coming from the service includes a list of every product associated with the
     * subscription.  In order to find the operative SKU, we need the top-level product which is the one
     * with a null parentSubscriptionProductId.
     * @param subscription Subscription object from SubscriptionService
     * @return the SKU that has a parentSubscriptionProductId of null
     */
    protected String extractSku(Subscription subscription) {
        List<SubscriptionProduct> products = subscription.getSubscriptionProducts();
        Objects.requireNonNull(products, "No subscription products found");
        List<String> skus = products.stream()
            .filter(x -> x.getParentSubscriptionProductId() == null)
            .distinct()
            .map(SubscriptionProduct::getSku)
            .collect(Collectors.toList());

        if (skus.size() == 1) {
            return skus.get(0);
        }
        throw new IllegalStateException("Could not find top level SKU for subscription " + subscription);
    }

    protected String extractMarketplaceId(Subscription subscription) {
        Map<String, ExternalReference> externalRefs = subscription.getExternalReferences();
        String subId = null;
        if (externalRefs != null && !externalRefs.isEmpty()) {
            ExternalReference marketplace = externalRefs
                .getOrDefault(MARKETPLACE, new ExternalReference());
            subId = marketplace.getSubscriptionID();
        }
        return (StringUtils.hasText(subId)) ? subId : null;
    }

    protected static boolean needNewRecord(Subscription dto,
        org.candlepin.subscriptions.db.model.Subscription entity) {
        return dto.getQuantity() != entity.getQuantity();
    }

    protected void updateSubscription(Subscription dto,
        org.candlepin.subscriptions.db.model.Subscription entity) {
        if (dto.getEffectiveEndDate() != null) {
            entity.setEndDate(clock.dateFromUnix(dto.getEffectiveEndDate()));
        }
    }
}
