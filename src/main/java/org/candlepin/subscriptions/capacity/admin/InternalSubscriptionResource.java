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
import jakarta.persistence.EntityNotFoundException;
import jakarta.ws.rs.NotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.product.OfferingSyncController;
import org.candlepin.subscriptions.product.SyncResult;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.security.SecurityProperties;
import org.candlepin.subscriptions.subscription.SubscriptionPruneController;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.utilization.admin.api.InternalApi;
import org.candlepin.subscriptions.utilization.admin.api.model.AwsUsageContext;
import org.candlepin.subscriptions.utilization.admin.api.model.DefaultResponse;
import org.candlepin.subscriptions.utilization.admin.api.model.Metric;
import org.candlepin.subscriptions.utilization.admin.api.model.OfferingProductTags;
import org.candlepin.subscriptions.utilization.admin.api.model.OfferingResponse;
import org.candlepin.subscriptions.utilization.admin.api.model.RhmUsageContext;
import org.candlepin.subscriptions.utilization.admin.api.model.SubscriptionResponse;
import org.candlepin.subscriptions.utilization.admin.api.model.TerminationRequest;
import org.candlepin.subscriptions.utilization.admin.api.model.TerminationRequestData;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Subscriptions Table API implementation. */
@Slf4j
@Component
public class InternalSubscriptionResource implements InternalApi {

  private final SubscriptionSyncController subscriptionSyncController;
  private final SubscriptionPruneController subscriptionPruneController;
  private final OfferingSyncController offeringSync;
  private final CapacityReconciliationController capacityReconciliationController;
  private final SecurityProperties properties;
  private final MeterRegistry meterRegistry;
  private final UsageContextSubscriptionProvider awsSubscriptionProvider;
  private final UsageContextSubscriptionProvider rhmSubscriptionProvider;
  private final MetricMapper metricMapper;
  private static final String SUCCESS_STATUS = "Success";

  public static final String FEATURE_NOT_ENABLED_MESSSAGE =
      "This feature is not currently enabled.";

  @Autowired
  public InternalSubscriptionResource(
      MeterRegistry meterRegistry,
      SubscriptionSyncController subscriptionSyncController,
      SecurityProperties properties,
      SubscriptionPruneController subscriptionPruneController,
      OfferingSyncController offeringSync,
      CapacityReconciliationController capacityReconciliationController,
      MetricMapper metricMapper) {
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
    this.subscriptionPruneController = subscriptionPruneController;
    this.offeringSync = offeringSync;
    this.capacityReconciliationController = capacityReconciliationController;
    this.metricMapper = metricMapper;
  }

  /**
   * Save subscriptions manually. Supported only in dev-mode.
   *
   * @param reconcileCapacity Invoke reconciliation logic to create capacity? (hint: offering for
   *     the SKU must be present)
   * @param subscriptionsJson JSON array containing subscriptions to save
   */
  @Override
  public SubscriptionResponse saveSubscriptions(
      Boolean reconcileCapacity, String subscriptionsJson) {
    var response = new SubscriptionResponse();
    if (!properties.isDevMode() && !properties.isManualSubscriptionEditingEnabled()) {
      response.setDetail(FEATURE_NOT_ENABLED_MESSSAGE);
      return response;
    }
    try {
      Object principal = ResourceUtils.getPrincipal();
      log.info("Save of new subscriptions triggered over internal API by {}", principal);
      subscriptionSyncController.saveSubscriptions(subscriptionsJson, reconcileCapacity);
      response.setDetail(SUCCESS_STATUS);
    } catch (Exception e) {
      log.error("Error saving subscriptions", e);
      response.setDetail("Error saving subscriptions.");
    }
    return response;
  }

  /** Enqueue all sync-enabled orgs to sync their subscriptions with upstream. */
  @Override
  public DefaultResponse syncAllSubscriptions() {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Sync for all sync enabled orgs triggered by {}", principal);
    subscriptionSyncController.syncAllSubscriptionsForAllOrgs();
    return getDefaultResponse(SUCCESS_STATUS);
  }

  @Override
  public String forceSyncSubscriptionsForOrg(String orgId) {
    subscriptionSyncController.forceSyncSubscriptionsForOrgAsync(orgId);
    return "Sync started.";
  }

  /** Remove subscription and capacity records that are in the denylist */
  @Override
  public DefaultResponse pruneUnlistedSubscriptions() {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Prune of unlisted subscriptions triggered by {}", principal);
    subscriptionPruneController.pruneAllUnlistedSubscriptions();
    return getDefaultResponse(SUCCESS_STATUS);
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

  @Override
  public List<Metric> getMetrics(String tag) {
    return metricMapper.mapMetrics(subscriptionSyncController.getMetricsForTag(tag));
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

  /**
   * Sync an offering from the upstream source.
   *
   * @param sku A marketing SKU
   */
  @Override
  public OfferingResponse syncOffering(String sku) {
    var response = new OfferingResponse();
    try {
      Object principal = ResourceUtils.getPrincipal();
      log.info("Sync for offering {} triggered by {}", sku, principal);
      SyncResult result = offeringSync.syncOffering(sku);

      response.setDetail(String.format("%s for offeringSku=\"%s\".", result, sku));
    } catch (Exception e) {
      log.error("Error syncing offering", e);
      response.setDetail("Error syncing offering");
    }
    return response;
  }

  /** Syncs all offerings not listed in deny list from the upstream source. */
  @Override
  public OfferingResponse syncAllOfferings() {
    var response = new OfferingResponse();
    try {
      Object principal = ResourceUtils.getPrincipal();
      log.info("Sync all offerings triggered by {}", principal);
      int numProducts = offeringSync.syncAllOfferings();

      response.setDetail(
          String.format("Enqueued %s numProducts offerings to be synced.", numProducts));
    } catch (RuntimeException e) {
      log.error("Error enqueueing offerings to be synced. See log for details.", e);
      response.setDetail("Error enqueueing offerings to be synced");
    }
    return response;
  }

  /**
   * Reconcile capacity for an offering from the upstream source.
   *
   * @param sku A marketing SKU
   */
  @Override
  public OfferingResponse forceReconcileOffering(String sku) {
    var response = new OfferingResponse();
    try {
      Object principal = ResourceUtils.getPrincipal();
      log.info("Capacity Reconciliation for sku {} triggered by {}", sku, principal);
      capacityReconciliationController.reconcileCapacityForOffering(sku, 0, 100);
      response.setDetail(SUCCESS_STATUS);
    } catch (Exception e) {
      log.error("Error reconciling offering", e);
      response.setDetail("Error reconciling offering.");
    }
    return response;
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

  @NotNull
  private DefaultResponse getDefaultResponse(String status) {
    var response = new DefaultResponse();
    response.setStatus(status);
    return response;
  }
}
