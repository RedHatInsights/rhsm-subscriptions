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
package org.candlepin.subscriptions.subscription;

import java.time.OffsetDateTime;
import java.util.Optional;
import javax.transaction.Transactional;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.stereotype.Component;

/** Update subscriptions from subscription service responses. */
@Component
public class SubscriptionSyncController {
  private final SubscriptionRepository subscriptionRepository;
  private final ApplicationClock clock;

  public SubscriptionSyncController(
      SubscriptionRepository subscriptionRepository, ApplicationClock clock) {
    this.subscriptionRepository = subscriptionRepository;
    this.clock = clock;
  }

  @Transactional
  public void syncSubscription(Subscription subscription) {
    final Optional<org.candlepin.subscriptions.db.model.Subscription> maybePresent =
        subscriptionRepository.findActiveSubscription(String.valueOf(subscription.getId()));
    if (maybePresent.isPresent()) {

      final org.candlepin.subscriptions.db.model.Subscription existing = maybePresent.get();
      if (existing.getQuantity() != subscription.getQuantity()) {

        existing.endSubscription();
        subscriptionRepository.save(existing);
        final org.candlepin.subscriptions.db.model.Subscription newSub = org.candlepin.subscriptions.db.model.Subscription.builder()
                .subscriptionId(existing.getSubscriptionId())
                .sku(existing.getSku())
                .ownerId(existing.getOwnerId())
                .accountNumber(existing.getAccountNumber())
                .quantity(subscription.getQuantity())
                .startDate(OffsetDateTime.now())
                .endDate(clock.dateFromMilliseconds(subscription.getEffectiveEndDate())) //TODO: For some reason this could be null. Why can't the api return an optional for this?
                .marketplaceSubscriptionId(SubscriptionDtoUtil.extractMarketplaceId(subscription)).build();
        subscriptionRepository.save(newSub);
      } else {
        updateSubscription(subscription, existing);
        subscriptionRepository.save(existing);
      }
    } else {

      final org.candlepin.subscriptions.db.model.Subscription newSub = org.candlepin.subscriptions.db.model.Subscription.builder()
              .subscriptionId(String.valueOf(subscription.getId()))
              .sku(SubscriptionDtoUtil.extractSku(subscription))
              .accountNumber(String.valueOf(subscription.getOracleAccountNumber()))
              .quantity(subscription.getQuantity())
              .startDate(clock.dateFromMilliseconds(subscription.getEffectiveStartDate()))
              .endDate(clock.dateFromMilliseconds(subscription.getEffectiveEndDate())) //TODO: For some reason this could be null. Why can't the api return an optional for this?
              .marketplaceSubscriptionId(SubscriptionDtoUtil.extractMarketplaceId(subscription)).build();

      subscriptionRepository.save(newSub);
    }
  }

  void syncSubscription(String subscriptionId){

  }

  Subscription getUpstreamSubscription(String subscriptionId){

  }

  protected void updateSubscription(
      Subscription dto, org.candlepin.subscriptions.db.model.Subscription entity) {
    if (dto.getEffectiveEndDate() != null) {
      entity.setEndDate(clock.dateFromMilliseconds(dto.getEffectiveEndDate()));
    }
  }
}
