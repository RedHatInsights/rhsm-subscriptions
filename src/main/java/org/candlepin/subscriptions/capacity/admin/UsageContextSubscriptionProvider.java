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
package org.candlepin.subscriptions.capacity.admin;

import io.micrometer.core.instrument.Counter;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response.Status;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;

@Slf4j
public class UsageContextSubscriptionProvider {

  private final SubscriptionSyncController subscriptionSyncController;
  private final BillingProvider billingProvider;
  private final Counter missingSubscriptionCounter;
  private final Counter ambiguousSubscriptionCounter;

  public UsageContextSubscriptionProvider(
      SubscriptionSyncController subscriptionSyncController,
      Counter missingSubscriptionCounter,
      Counter ambiguousSubscriptionCounter,
      BillingProvider billingProvider) {
    this.subscriptionSyncController = subscriptionSyncController;
    this.missingSubscriptionCounter = missingSubscriptionCounter;
    this.ambiguousSubscriptionCounter = ambiguousSubscriptionCounter;
    this.billingProvider = billingProvider;
  }

  public Optional<Subscription> getSubscription(
      String orgId,
      String productId,
      String sla,
      String usage,
      String billingAccountId,
      OffsetDateTime subscriptionDate) {

    // If billingAccountId is multipart, find all matching first part (ex: azureTenantId)
    var searchByBillingAccountId = billingAccountId;
    if (StringUtils.isNotBlank(billingAccountId) && billingAccountId.contains(";")) {
      searchByBillingAccountId = billingAccountId.split(";")[0];
    }

    UsageCalculation.Key usageKey =
        new Key(
            productId,
            ServiceLevel.fromString(sla),
            Usage.fromString(usage),
            billingProvider,
            searchByBillingAccountId);

    // Set start date one hour in past to pickup recently terminated subscriptions
    var start = subscriptionDate.minusHours(1);
    List<Subscription> subscriptions =
        subscriptionSyncController.findSubscriptions(
            Optional.ofNullable(orgId), usageKey, start, subscriptionDate);

    var bestMatchSubscriptions =
        filterByClosestMatchingMultipartBillingAccountId(subscriptions, billingAccountId);

    var existsRecentlyTerminatedSubscription =
        bestMatchSubscriptions.stream()
            .anyMatch(
                subscription ->
                    subscription.getEndDate() != null
                        && subscription.getEndDate().isBefore(subscriptionDate));

    // Filter out any terminated subscriptions
    var activeSubscriptions =
        bestMatchSubscriptions.stream()
            .filter(
                subscription ->
                    subscription.getEndDate() == null
                        || subscription.getEndDate().isAfter(subscriptionDate)
                        || subscription.getEndDate().equals(subscriptionDate))
            .toList();

    if (bestMatchSubscriptions.isEmpty()) {
      missingSubscriptionCounter.increment();
      throw new NotFoundException();
    }

    if (activeSubscriptions.isEmpty() && existsRecentlyTerminatedSubscription) {
      throw new SubscriptionsException(
          ErrorCode.SUBSCRIPTION_RECENTLY_TERMINATED,
          Status.NOT_FOUND,
          "Subscription recently terminated",
          "");
    }

    if (activeSubscriptions.size() > 1) {
      // If this is a multipart ID, throw an error since correct result could not be determined.
      if (billingAccountId.contains(";")) {
        throw new SubscriptionsException(
            ErrorCode.SUBSCRIPTION_CANNOT_BE_DETERMINED,
            Status.NOT_FOUND,
            "Subscription could not be determined. Likely multiple subscriptions exist missing partial billingAccountId information.",
            "");
      }
      ambiguousSubscriptionCounter.increment();
      log.warn(
          "Multiple subscriptions found for billing provider {} or for org {} with key {} and product tag {}."
              + " Selecting first result",
          billingProvider,
          orgId,
          usageKey,
          usageKey.getProductId());
    }
    return activeSubscriptions.stream().findFirst();
  }

  // Filter out subscriptions if we have an exact match on a multipart billingAccountId.
  private List<Subscription> filterByClosestMatchingMultipartBillingAccountId(
      List<Subscription> subscriptions, String billingAccountId) {
    if (Objects.nonNull(billingAccountId) && billingAccountId.contains(";")) {
      var exactMatchingSubscriptions =
          subscriptions.stream()
              .filter(
                  subscription ->
                      Objects.equals(billingAccountId, subscription.getBillingAccountId()))
              .collect(Collectors.toList());
      if (!exactMatchingSubscriptions.isEmpty()) {
        return exactMatchingSubscriptions;
      }
      // If exact billingAccountId doesn't match and there is only one subscription matching the
      // partial id, return that subscription
      else {
        var billingAccountIdPartOne = billingAccountId.split(";")[0];
        var closestMatch =
            subscriptions.stream()
                .filter(
                    subscription ->
                        subscription.getBillingAccountId().equals(billingAccountIdPartOne))
                .collect(Collectors.toList());
        if (closestMatch.size() == 1) {
          return closestMatch;
        }
      }
    }
    return subscriptions;
  }
}
