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

import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Update subscriptions from subscription service responses. */
@Component
public class SubscriptionSyncController {
  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionService subscriptionService;
  private final ApplicationClock clock;
  private final CapacityReconciliationController capacityReconciliationController;

  public SubscriptionSyncController(
      SubscriptionRepository subscriptionRepository,
      ApplicationClock clock,
      SubscriptionService subscriptionService,
      CapacityReconciliationController capacityReconciliationController) {
    this.subscriptionRepository = subscriptionRepository;
    this.subscriptionService = subscriptionService;
    this.capacityReconciliationController = capacityReconciliationController;
    this.clock = clock;
  }

  @Transactional
  public void syncSubscription(Subscription subscription) {

    //TODO: add metrics for subscriptions created and updated
    final Optional<org.candlepin.subscriptions.db.model.Subscription> maybePresent =
        subscriptionRepository.findActiveSubscription(String.valueOf(subscription.getId()));

    final org.candlepin.subscriptions.db.model.Subscription newOrUpdated = convertDto(subscription);

    if (maybePresent.isPresent()) {

      final org.candlepin.subscriptions.db.model.Subscription existing = maybePresent.get();
      if (!existing.equals(newOrUpdated)) {
        if (existing.quantityHasChanged(newOrUpdated.getQuantity())) {
          existing.endSubscription();
          subscriptionRepository.save(existing);
          final org.candlepin.subscriptions.db.model.Subscription newSub =
                  org.candlepin.subscriptions.db.model.Subscription.builder()
                          .subscriptionId(existing.getSubscriptionId())
                          .sku(existing.getSku())
                          .ownerId(existing.getOwnerId())
                          .accountNumber(existing.getAccountNumber())
                          .quantity(subscription.getQuantity())
                          .startDate(OffsetDateTime.now())
                          .endDate(clock.dateFromMilliseconds(subscription.getEffectiveEndDate())) // Q&A: For some reason this could be null.// Why can't the api return an optional for// this?
                          .marketplaceSubscriptionId(SubscriptionDtoUtil.extractMarketplaceId(subscription))
                          .build();
          subscriptionRepository.save(newSub);
        } else {
          updateSubscription(subscription, existing);
          subscriptionRepository.save(existing);
        }
        capacityReconciliationController.reconcileCapacityForSubscription(newOrUpdated);
      }
    } else {
      subscriptionRepository.save(newOrUpdated);
      capacityReconciliationController.reconcileCapacityForSubscription(newOrUpdated);
    }
  }

  protected void updateSubscription(
      Subscription dto, org.candlepin.subscriptions.db.model.Subscription entity) {
    //TODO: Add logs
    if (dto.getEffectiveEndDate() != null) {
      entity.setEndDate(clock.dateFromMilliseconds(dto.getEffectiveEndDate()));
    }
    //TODO: what if it is null? throw an exception
  }

  @Transactional
  public void syncSubscription(String subscriptionId) {
    Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId);
    syncSubscription(subscription);
  }

  public org.candlepin.subscriptions.db.model.Subscription getUpstreamSubscription(String subscriptionId)
          throws ExternalServiceException {
    Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId);
    return convertDto(subscription);
  }

  private org.candlepin.subscriptions.db.model.Subscription convertDto(Subscription subscription) {

    return org.candlepin.subscriptions.db.model.Subscription.builder()
            .subscriptionId(String.valueOf(subscription.getId()))
            .sku(SubscriptionDtoUtil.extractSku(subscription))
            .ownerId(subscription.getWebCustomerId().toString())
            .accountNumber(String.valueOf(subscription.getOracleAccountNumber()))
            .quantity(subscription.getQuantity())
            .startDate(clock.dateFromMilliseconds(subscription.getEffectiveStartDate()))
            .endDate(clock.dateFromMilliseconds(subscription.getEffectiveEndDate()))
            .marketplaceSubscriptionId(SubscriptionDtoUtil.extractMarketplaceId(subscription))
            .build();
  }
}
