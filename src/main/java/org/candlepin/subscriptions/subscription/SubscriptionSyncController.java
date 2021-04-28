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
import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.subscription.api.model.SubscriptionProduct;
import org.candlepin.subscriptions.subscription.job.SubscriptionTaskManager;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

/**
 * Update subscriptions from subscription service responses.
 */
@Component
public class SubscriptionSyncController {
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final ApplicationClock clock;
    private final SubscriptionTaskManager subscriptionTaskManager;

    public SubscriptionSyncController(SubscriptionRepository subscriptionRepository, ApplicationClock clock,
        SubscriptionService subscriptionService, SubscriptionTaskManager subscriptionTaskManager) {
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
        this.subscriptionService = subscriptionService;
        this.subscriptionTaskManager = subscriptionTaskManager;
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

                newSub.setMarketplaceSubscriptionId(SubscriptionDtoUtil.extractMarketplaceId(subscription));

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
                convertDto(subscription);
            subscriptionRepository.save(newSub);
        }
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

    public void syncAllSubcriptionsForOrg(String orgId) {
        subscriptionTaskManager.syncSubscriptionsForOrg(orgId, 0, 100L);
    }

    public void syncSubscriptions(String orgId, String offset, String limit) throws ApiException {
        int index = Integer.parseInt(offset);
        int pageSize = Integer.parseInt(limit);
        List<Subscription> subscriptions = subscriptionService
            .getSubscriptionsByOrgId(orgId, index, pageSize);
        subscriptions.forEach(this::syncSubscription);
        if (pageSize == subscriptions.size()) {
            subscriptionTaskManager.syncSubscriptionsForOrg(orgId, index + pageSize,
                (long) pageSize);
        }
    }

    @Transactional
    public void syncSubscription(String subscriptionId) throws ApiException {
        Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId);
        syncSubscription(subscription);
    }

    public org.candlepin.subscriptions.db.model.Subscription getUpstreamSubscription(String subscriptionId)
        throws ApiException {
        Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId);
        return convertDto(subscription);
    }

    private org.candlepin.subscriptions.db.model.Subscription convertDto(Subscription subscription) {
        final org.candlepin.subscriptions.db.model.Subscription entity =
            new org.candlepin.subscriptions.db.model.Subscription();

        entity.setSubscriptionId(String.valueOf(subscription.getId()));
        entity.setSku(subscription.getSubscriptionProducts().stream()
            .filter(subscriptionProduct -> subscriptionProduct.getParentSubscriptionProductId() == null)
            .findAny().map(SubscriptionProduct::getSku).orElse(null));
        if (subscription.getEffectiveStartDate() != null) {
            entity.setStartDate(clock.dateFromUnix(subscription.getEffectiveStartDate()));
        }
        if (subscription.getEffectiveEndDate() != null) {
            entity.setStartDate(clock.dateFromUnix(subscription.getEffectiveEndDate()));
        }
        entity.setQuantity(subscription.getQuantity());
        entity.setOwnerId(String.valueOf(subscription.getWebCustomerId()));
        entity.setMarketplaceSubscriptionId(SubscriptionDtoUtil.extractMarketplaceId(subscription));
        entity.setAccountNumber(String.valueOf(subscription.getOracleAccountNumber()));

        return entity;
    }
}
