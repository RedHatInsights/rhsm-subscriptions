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
package com.redhat.swatch.contract.service;

import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@AllArgsConstructor
@Slf4j
public class SubscriptionService {

  private final SubscriptionRepository subscriptionRepository;

  public Stream<SubscriptionEntity> streamByOrgId(String orgId) {
    return subscriptionRepository.streamByOrgId(orgId);
  }

  public List<SubscriptionEntity> findBySubscriptionNumber(String subscriptionNumber) {
    return subscriptionRepository.findBySubscriptionNumber(subscriptionNumber);
  }

  public List<SubscriptionEntity> findActiveSubscription(String subscriptionId) {
    return subscriptionRepository.findActiveSubscription(subscriptionId);
  }

  public List<SubscriptionEntity> findByContract(ContractEntity contract) {
    return subscriptionRepository.find(
        SubscriptionEntity.class, SubscriptionEntity.forContract(contract));
  }

  @Transactional(TxType.MANDATORY)
  public void flushAndClearPersistenceContext() {
    subscriptionRepository.flush();
    subscriptionRepository.getEntityManager().clear();
  }

  @Transactional(TxType.MANDATORY)
  public void save(SubscriptionEntity subscription) {
    subscription = persistAndMerge(subscription);
    log.info(
        "Subscription created/updated org_id={} subscription_id={} subscription_number={} start_date={} end_date={} billing_account_id={}",
        subscription.getOrgId(),
        subscription.getSubscriptionId(),
        subscription.getSubscriptionNumber(),
        subscription.getStartDate(),
        subscription.getEndDate(),
        subscription.getBillingAccountId());
  }

  @Transactional(TxType.MANDATORY)
  public void terminate(SubscriptionEntity subscription) {
    subscription = persistAndMerge(subscription);
    log.info(
        "Subscription terminated org_id={} subscription_id={} subscription_number={} start_date={} end_date={} billing_account_id={}",
        subscription.getOrgId(),
        subscription.getSubscriptionId(),
        subscription.getSubscriptionNumber(),
        subscription.getStartDate(),
        subscription.getEndDate(),
        subscription.getBillingAccountId());
  }

  @Transactional(TxType.MANDATORY)
  public void delete(SubscriptionEntity subscription, SubscriptionDeleteReason deleteReason) {
    Objects.requireNonNull(deleteReason, "deleteReason must not be null");
    subscriptionRepository.delete(subscription);
    log.info(
        "Subscription deleted org_id={} subscription_id={} subscription_number={} start_date={} end_date={} billing_account_id={} delete_reason={}",
        subscription.getOrgId(),
        subscription.getSubscriptionId(),
        subscription.getSubscriptionNumber(),
        subscription.getStartDate(),
        subscription.getEndDate(),
        subscription.getBillingAccountId(),
        deleteReason);
  }

  private SubscriptionEntity persistAndMerge(SubscriptionEntity subscription) {
    var em = subscriptionRepository.getEntityManager();
    if (em.contains(subscription)) {
      subscriptionRepository.persist(subscription);
      return subscription;
    }

    // merge is needed here because the existing subscription might not be found if we only use
    // the subscription number (since primary keys are subscription ID and start date).
    // To be fixed in SWATCH-2801.
    return em.merge(subscription);
  }
}
