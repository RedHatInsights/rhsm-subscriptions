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

import com.redhat.swatch.contract.exception.ErrorCode;
import com.redhat.swatch.contract.exception.ServiceException;
import com.redhat.swatch.contract.repository.DbReportCriteria;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionEntity_;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response.Status;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class UsageContextSubscriptionProvider {

  private final SubscriptionRepository subscriptionRepository;
  private final MeterRegistry meterRegistry;

  public Optional<SubscriptionEntity> getSubscription(DbReportCriteria criteria) {
    var providerString = criteria.getBillingProvider().getValue();
    var missingSubscriptionCounter =
        meterRegistry.counter("swatch_missing_subscriptions", "provider", providerString);
    var ambiguousSubscriptionCounter =
        meterRegistry.counter("swatch_ambiguous_subscriptions", "provider", providerString);

    List<SubscriptionEntity> subscriptions =
        subscriptionRepository.findByCriteria(
            criteria, Sort.descending(SubscriptionEntity_.START_DATE));

    if (subscriptions.isEmpty()) {
      log.warn("No subscription found for criteria {}", criteria);
      missingSubscriptionCounter.increment();
      throw new NotFoundException();
    }

    var existsRecentlyTerminatedSubscription =
        subscriptions.stream()
            .anyMatch(
                subscription ->
                    subscription.getEndDate() != null
                        && subscription.getEndDate().isBefore(criteria.getEnding()));

    // Filter out any terminated subscriptions
    var activeSubscriptions =
        subscriptions.stream()
            .filter(
                subscription ->
                    subscription.getEndDate() == null
                        || subscription.getEndDate().isAfter(criteria.getEnding())
                        || subscription.getEndDate().equals(criteria.getEnding()))
            .toList();

    if (activeSubscriptions.isEmpty()) {
      if (existsRecentlyTerminatedSubscription) {
        throw new ServiceException(
            ErrorCode.SUBSCRIPTION_RECENTLY_TERMINATED,
            Status.NOT_FOUND,
            "Subscription recently terminated",
            "");
      } else {
        log.warn("No active subscription found for criteria {}", criteria);
        missingSubscriptionCounter.increment();
        throw new NotFoundException();
      }
    }

    if (activeSubscriptions.size() > 1) {
      ambiguousSubscriptionCounter.increment();
      log.warn("Multiple subscriptions found for criteria {}. Selecting first result", criteria);
    }
    return activeSubscriptions.stream().findFirst();
  }
}
