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

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Update subscriptions from subscription service responses.
 */
@Component
public class SubscriptionSyncController {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionSyncController(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

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
                newSub.setQuantity(subscription.getQuantity());
                newSub.setStartDate(OffsetDateTime.now());
                if (subscription.getEffectiveEndDate() != null) {
                    newSub.setEndDate(convertLongTime(subscription.getEffectiveEndDate()));
                }
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
            newSub.setQuantity(subscription.getQuantity());
            if (subscription.getEffectiveStartDate() != null) {
                newSub.setStartDate(convertLongTime(subscription.getEffectiveStartDate()));
            }
            if (subscription.getEffectiveEndDate() != null) {
                newSub.setEndDate(convertLongTime(subscription.getEffectiveEndDate()));
            }
            subscriptionRepository.save(newSub);
        }
    }

    private static boolean needNewRecord(Subscription dto,
        org.candlepin.subscriptions.db.model.Subscription entity) {
        return dto.getQuantity() != entity.getQuantity();
    }

    private static void updateSubscription(Subscription dto,
        org.candlepin.subscriptions.db.model.Subscription entity) {
        if (dto.getEffectiveEndDate() != null) {
            entity.setEndDate(
                convertLongTime(dto.getEffectiveEndDate())
            );
        }
    }

    private static OffsetDateTime convertLongTime(Long time) {
        return OffsetDateTime.of(LocalDateTime.ofEpochSecond(
            time, 0, ZoneOffset.UTC),
            ZoneOffset.UTC);
    }
}
