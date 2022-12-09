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
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.persistence.EntityNotFoundException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response.Status;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.security.SecurityProperties;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.candlepin.subscriptions.utilization.admin.api.InternalApi;
import org.candlepin.subscriptions.utilization.admin.api.model.AwsUsageContext;
import org.candlepin.subscriptions.utilization.admin.api.model.TerminationRequest;
import org.candlepin.subscriptions.utilization.admin.api.model.TerminationRequestData;
import org.springframework.stereotype.Component;

/** Subscriptions Table API implementation. */
@Slf4j
@Component
public class InternalSubscriptionResource implements InternalApi {

  private final SubscriptionSyncController subscriptionSyncController;
  private final Counter missingSubscriptionCounter;
  private final Counter ambiguousSubscriptionCounter;
  private final SecurityProperties properties;

  public InternalSubscriptionResource(
      MeterRegistry meterRegistry,
      SubscriptionSyncController subscriptionSyncController,
      SecurityProperties properties) {
    this.missingSubscriptionCounter = meterRegistry.counter("swatch_missing_aws_subscription");
    this.ambiguousSubscriptionCounter = meterRegistry.counter("swatch_ambiguous_aws_subscription");
    this.subscriptionSyncController = subscriptionSyncController;
    this.properties = properties;
  }

  @Override
  public String forceSyncSubscriptionsForOrg(String orgId) {
    subscriptionSyncController.forceSyncSubscriptionsForOrgAsync(orgId);
    return "Sync started.";
  }

  @Override
  public AwsUsageContext getAwsUsageContext(
      String orgId,
      OffsetDateTime date,
      String productId,
      String accountNumber,
      String sla,
      String usage,
      String awsAccountId) {
    UsageCalculation.Key usageKey =
        new Key(
            productId,
            ServiceLevel.fromString(sla),
            Usage.fromString(usage),
            BillingProvider.AWS,
            awsAccountId);

    // Set start date one hour in past to pickup recently terminated subscriptions
    var start = date.minusHours(1);
    List<Subscription> subscriptions =
        subscriptionSyncController.findSubscriptionsAndSyncIfNeeded(
            accountNumber, Optional.ofNullable(orgId), usageKey, start, date, true);

    var existsRecentlyTerminatedSubscription =
        subscriptions.stream().anyMatch(subscription -> subscription.getEndDate().isBefore(date));

    // Filter out any terminated subscriptions
    var activeSubscriptions =
        subscriptions.stream()
            .filter(
                subscription ->
                    subscription.getEndDate().isAfter(date)
                        || subscription.getEndDate().equals(date))
            .collect(Collectors.toList());

    if (subscriptions.isEmpty()) {
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
      ambiguousSubscriptionCounter.increment();
      log.warn(
          "Multiple subscriptions found for account {} or for org {} with key {} and product tag {}."
              + " Selecting first result",
          accountNumber,
          orgId,
          usageKey,
          usageKey.getProductId());
    }
    return activeSubscriptions.stream().findFirst().map(this::buildAwsUsageContext).orElseThrow();
  }

  private AwsUsageContext buildAwsUsageContext(Subscription subscription) {
    String[] parts = subscription.getBillingProviderId().split(";");
    String productCode = parts[0];
    String customerId = parts[1];
    String sellerAccount = parts[2];
    return new AwsUsageContext()
        .rhSubscriptionId(subscription.getSubscriptionId())
        .subscriptionStartDate(subscription.getStartDate())
        .productCode(productCode)
        .customerId(customerId)
        .awsSellerAccountId(sellerAccount);
  }

  @Override
  public TerminationRequest terminateSubscription(String subscriptionId, OffsetDateTime timestamp) {
    if (!properties.isManualEventEditingEnabled()) {
      throw new UnsupportedOperationException("Manual event editing is disabled");
    }

    try {
      var msg = subscriptionSyncController.terminateSubscription(subscriptionId, timestamp);
      return new TerminationRequest().data(new TerminationRequestData().terminationMessage(msg));
    } catch (EntityNotFoundException e) {
      throw new NotFoundException(
          "Subscription " + subscriptionId + " either does not exist or is already terminated");
    }
  }
}
