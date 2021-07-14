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
import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.util.ApplicationClock;
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

    // TODO: https://issues.redhat.com/browse/ENT-4029 //NOSONAR
    final Optional<org.candlepin.subscriptions.db.model.Subscription> subscriptionOptional =
        subscriptionRepository.findActiveSubscription(String.valueOf(subscription.getId()));

    final org.candlepin.subscriptions.db.model.Subscription newOrUpdated = convertDto(subscription);

    if (subscriptionOptional.isPresent()) {

      final org.candlepin.subscriptions.db.model.Subscription existingSubscription =
          subscriptionOptional.get();
      if (!existingSubscription.equals(newOrUpdated)) {
        if (existingSubscription.quantityHasChanged(newOrUpdated.getQuantity())) {
          existingSubscription.endSubscription();
          subscriptionRepository.save(existingSubscription);
          final org.candlepin.subscriptions.db.model.Subscription newSub =
              org.candlepin.subscriptions.db.model.Subscription.builder()
                  .subscriptionId(existingSubscription.getSubscriptionId())
                  .sku(existingSubscription.getSku())
                  .ownerId(existingSubscription.getOwnerId())
                  .accountNumber(existingSubscription.getAccountNumber())
                  .quantity(subscription.getQuantity())
                  .startDate(OffsetDateTime.now())
                  .endDate(clock.dateFromMilliseconds(subscription.getEffectiveEndDate()))
                  .marketplaceSubscriptionId(SubscriptionDtoUtil.extractMarketplaceId(subscription))
                      .subscriptionNumber(subscription.getSubscriptionNumber())
                  .build();
          subscriptionRepository.save(newSub);
        } else {
          updateSubscription(subscription, existingSubscription);
          subscriptionRepository.save(existingSubscription);
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
    if (dto.getEffectiveEndDate() != null) {
      entity.setEndDate(clock.dateFromMilliseconds(dto.getEffectiveEndDate()));
    }
  }

  @Transactional
  public void syncSubscription(String subscriptionId) {
    Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId);
    syncSubscription(subscription);
  }

  private org.candlepin.subscriptions.db.model.Subscription convertDto(Subscription subscription) {

    return org.candlepin.subscriptions.db.model.Subscription.builder()
        .subscriptionId(String.valueOf(subscription.getId()))
            .subscriptionNumber(subscription.getSubscriptionNumber())
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
