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
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.candlepin.subscriptions.utilization.admin.api.InternalApi;
import org.candlepin.subscriptions.utilization.admin.api.model.AwsUsageContext;
import org.springframework.stereotype.Component;

/** Subscriptions Table API implementation. */
@Slf4j
@Component
public class InternalSubscriptionResource implements InternalApi {

  private final SubscriptionSyncController subscriptionSyncController;
  private final Counter missingSubscriptionCounter;
  private final Counter ambiguousSubscriptionCounter;

  public InternalSubscriptionResource(
      MeterRegistry meterRegistry, SubscriptionSyncController subscriptionSyncController) {
    this.missingSubscriptionCounter = meterRegistry.counter("swatch_missing_aws_subscription");
    this.ambiguousSubscriptionCounter = meterRegistry.counter("swatch_ambiguous_aws_subscription");
    this.subscriptionSyncController = subscriptionSyncController;
  }

  @Override
  public String forceSyncSubscriptionsForOrg(String orgId) {
    subscriptionSyncController.forceSyncSubscriptionsForOrgAsync(orgId);
    return "Sync started.";
  }

  @Override
  public AwsUsageContext getAwsUsageContext(
      String accountNumber, OffsetDateTime date, String productId, String sla, String usage) {
    UsageCalculation.Key usageKey =
        new Key(
            productId,
            ServiceLevel.fromString(sla),
            Usage.fromString(usage),
            BillingProvider.AWS,
            null);
    List<Subscription> subscriptions =
        subscriptionSyncController.findSubscriptionsAndSyncIfNeeded(
            accountNumber, Optional.empty(), usageKey, date, date, BillingProvider.AWS);
    if (subscriptions.isEmpty()) {
      missingSubscriptionCounter.increment();
      throw new NotFoundException();
    }
    if (subscriptions.size() > 1) {
      ambiguousSubscriptionCounter.increment();
      log.warn(
          "Multiple subscriptions found for account {} with key {} and product tag {}."
              + " Selecting first result",
          accountNumber,
          usageKey,
          usageKey.getProductId());
    }
    return subscriptions.stream().findFirst().map(this::buildAwsUsageContext).orElseThrow();
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
}
