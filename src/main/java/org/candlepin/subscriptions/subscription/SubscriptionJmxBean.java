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

import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.security.SecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jmx.JmxException;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

@Profile("capacity-ingress")
@Component
@ManagedResource
@Slf4j
public class SubscriptionJmxBean {

  private final SubscriptionSyncController subscriptionSyncController;
  private final SubscriptionPruneController subscriptionPruneController;
  private final SecurityProperties properties;

  @Autowired
  SubscriptionJmxBean(
      SubscriptionSyncController subscriptionSyncController,
      SubscriptionPruneController subscriptionPruneController,
      SecurityProperties properties) {
    this.subscriptionSyncController = subscriptionSyncController;
    this.subscriptionPruneController = subscriptionPruneController;
    this.properties = properties;
  }

  @Transactional
  @ManagedOperation
  public void syncSubscription(String subscriptionId) {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Sync for subscription {} triggered over JMX by {}", subscriptionId, principal);
    subscriptionSyncController.syncSubscription(subscriptionId);
  }

  @Transactional
  @ManagedOperation
  public void syncSubscriptionsForOrg(String orgId) {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Sync for org {} triggered over JMX by {}", orgId, principal);
    subscriptionSyncController.syncAllSubcriptionsForOrg(orgId);
  }

  @Transactional
  @ManagedOperation(
      description = "Enqueue all sync-enabled orgs to sync their subscriptions with upstream.")
  public void syncAllSubscriptions() {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Sync for all sync enabled orgs triggered over JMX by {}", principal);
    subscriptionSyncController.syncAllSubscriptionsForAllOrgs();
  }

  @Transactional
  @ManagedOperation(
      description = "Remove subscription and capacity records that are not in the allowlist.")
  public void pruneUnlistedSubscriptions() {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Prune of unlisted subscriptions triggered over JMX by {}", principal);
    subscriptionPruneController.pruneAllUnlistedSubscriptions();
  }

  @ManagedOperation(
      description = "Save subscriptions manually, ignoring allowlist. Supported only in dev-mode.")
  @ManagedOperationParameter(
      name = "subscriptionsJson",
      description = "JSON array containing subscriptions to save")
  @ManagedOperationParameter(
      name = "reconcileCapacity",
      description =
          "Invoke reconciliation logic to create capacity? (hint: offering for the SKU must be present)")
  public void saveSubscriptions(String subscriptionsJson, boolean reconcileCapacity) {
    if (!properties.isDevMode() && !properties.isManualSubscriptionEditingEnabled()) {
      throw new JmxException("This feature is not currently enabled.");
    }
    try {
      Object principal = ResourceUtils.getPrincipal();
      log.info("Save of new subscriptions triggered over JMX by {}", principal);
      subscriptionSyncController.saveSubscriptions(subscriptionsJson, reconcileCapacity);
    } catch (Exception e) {
      log.error("Error saving subscriptions", e);
      throw new JmxException("Error saving subscriptions. See log for details.");
    }
  }

  @ManagedOperation(description = "Delete a subscription manually. Supported only in dev-mode.")
  public void deleteSubscription(String subscriptionId) {
    if (!properties.isDevMode() && !properties.isManualSubscriptionEditingEnabled()) {
      throw new JmxException("This feature is not currently enabled.");
    }
    Object principal = ResourceUtils.getPrincipal();
    log.info("Save of new subscriptions triggered over JMX by {}", principal);
    subscriptionSyncController.deleteSubscription(subscriptionId);
  }
}
