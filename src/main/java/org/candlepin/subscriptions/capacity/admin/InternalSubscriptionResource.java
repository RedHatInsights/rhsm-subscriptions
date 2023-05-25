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

import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import javax.persistence.EntityNotFoundException;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.security.SecurityProperties;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.utilization.admin.api.InternalApi;
import org.candlepin.subscriptions.utilization.admin.api.model.AwsUsageContext;
import org.candlepin.subscriptions.utilization.admin.api.model.OfferingProductTags;
import org.candlepin.subscriptions.utilization.admin.api.model.RhmUsageContext;
import org.candlepin.subscriptions.utilization.admin.api.model.TerminationRequest;
import org.candlepin.subscriptions.utilization.admin.api.model.TerminationRequestData;
import org.springframework.stereotype.Component;

/** Subscriptions Table API implementation. */
@Slf4j
@Component
public class InternalSubscriptionResource implements InternalApi {

  private final SubscriptionSyncController subscriptionSyncController;
  private final SecurityProperties properties;
  private final MeterRegistry meterRegistry;
  private final UsageContextSubscriptionProvider awsSubscriptionProvider;
  private final UsageContextSubscriptionProvider rhmSubscriptionProvider;

  public InternalSubscriptionResource(
      MeterRegistry meterRegistry,
      SubscriptionSyncController subscriptionSyncController,
      SecurityProperties properties) {
    this.meterRegistry = meterRegistry;
    this.subscriptionSyncController = subscriptionSyncController;
    this.properties = properties;
    this.awsSubscriptionProvider =
        new UsageContextSubscriptionProvider(
            this.subscriptionSyncController,
            this.meterRegistry.counter("swatch_missing_aws_subscription"),
            this.meterRegistry.counter("swatch_ambiguous_aws_subscription"),
            BillingProvider.AWS);
    this.rhmSubscriptionProvider =
        new UsageContextSubscriptionProvider(
            this.subscriptionSyncController,
            this.meterRegistry.counter("rhsm-subscriptions.marketplace.missing.subscription"),
            this.meterRegistry.counter("rhsm-subscriptions.marketplace.ambiguous.subscription"),
            BillingProvider.RED_HAT);
  }

  @Override
  public String forceSyncSubscriptionsForOrg(String orgId) {
    subscriptionSyncController.forceSyncSubscriptionsForOrgAsync(orgId);
    return "Sync started.";
  }

  @Override
  public RhmUsageContext getRhmUsageContext(
      String orgId,
      OffsetDateTime date,
      String productId,
      String accountNumber,
      String sla,
      String usage) {

    // Use "_ANY" because we don't support multiple rh marketplace accounts for a single customer
    String billingAccoutId = "_ANY";

    return rhmSubscriptionProvider
        .getSubscription(orgId, accountNumber, productId, sla, usage, billingAccoutId, date)
        .map(this::buildRhmUsageContext)
        .orElseThrow();
  }

  private RhmUsageContext buildRhmUsageContext(Subscription subscription) {
    RhmUsageContext context = new RhmUsageContext();
    context.setRhSubscriptionId(subscription.getBillingProviderId());
    return context;
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

    return awsSubscriptionProvider
        .getSubscription(orgId, accountNumber, productId, sla, usage, awsAccountId, date)
        .map(this::buildAwsUsageContext)
        .orElseThrow();
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

  /**
   * @param sku
   * @return OfferingProductTags
   */
  @Override
  public OfferingProductTags getSkuProductTags(String sku) {
    return subscriptionSyncController.findProductTags(sku);
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
