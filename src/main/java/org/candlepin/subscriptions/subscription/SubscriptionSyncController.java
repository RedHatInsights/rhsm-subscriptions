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
import java.util.List;
import java.util.Optional;
import javax.transaction.Transactional;

import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Update subscriptions from subscription service responses. */
@Component
@Slf4j
public class SubscriptionSyncController {
  private SubscriptionRepository subscriptionRepository;
  private SubscriptionService subscriptionService;
  private ApplicationClock clock;
  private CapacityReconciliationController capacityReconciliationController;
  private KafkaTemplate<String, SyncSubscriptions> syncSubscriptionsKafkaTemplate;
  private String syncSubscriptionsTopic;

  public SubscriptionSyncController(
      SubscriptionRepository subscriptionRepository,
      ApplicationClock clock,
      SubscriptionService subscriptionService,
      CapacityReconciliationController capacityReconciliationController,
      KafkaTemplate<String, SyncSubscriptions> syncSubscriptionsKafkaTemplate,
      @Qualifier("subscriptionTasks") TaskQueueProperties props) {
    this.subscriptionRepository = subscriptionRepository;
    this.subscriptionService = subscriptionService;
    this.capacityReconciliationController = capacityReconciliationController;
    this.clock = clock;
    this.syncSubscriptionsTopic = props.getTopic();
    this.syncSubscriptionsKafkaTemplate = syncSubscriptionsKafkaTemplate;
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

  @Transactional
  public void syncSubscription(String subscriptionId) {
    Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId);
    syncSubscription(subscription);
  }

  @Transactional
  public void syncSubscriptions(String orgId, int offset, int limit){
    log.info("Syncing subscriptions for org: {} with offset: {} and limit: {} ", orgId, offset, limit);

    int pageSize = limit+1;
    boolean hasMore = false;
    List<Subscription> subscriptions = subscriptionService.getSubscriptionsByOrgId(orgId, offset, pageSize);
    if(subscriptions.size() >= pageSize)
      hasMore = true;
    subscriptions.forEach(this::syncSubscription);
    if(hasMore){
      offset = offset+limit;
      syncSubscriptionsKafkaTemplate.send(
              syncSubscriptionsTopic,
              SyncSubscriptions.builder()
                      .orgId(orgId).offset(offset).limit(limit).build());
    }
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

  protected void updateSubscription(
          Subscription dto, org.candlepin.subscriptions.db.model.Subscription entity) {
    if (dto.getEffectiveEndDate() != null) {
      entity.setEndDate(clock.dateFromMilliseconds(dto.getEffectiveEndDate()));
    }
  }
}
