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
import org.candlepin.subscriptions.umb.UmbProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.core.JmsTemplate;
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

  public static final String NOT_ENABLED_MESSAGE = "This feature is not currently enabled.";
  private final SubscriptionSyncController subscriptionSyncController;
  private final SubscriptionPruneController subscriptionPruneController;
  private final SecurityProperties properties;
  private final UmbProperties umbProperties;
  private final JmsTemplate jmsTemplate;

  @Autowired
  SubscriptionJmxBean(
      SubscriptionSyncController subscriptionSyncController,
      SubscriptionPruneController subscriptionPruneController,
      SecurityProperties properties,
      UmbProperties umbProperties,
      JmsTemplate jmsTemplate) {
    this.subscriptionSyncController = subscriptionSyncController;
    this.subscriptionPruneController = subscriptionPruneController;
    this.properties = properties;
    this.umbProperties = umbProperties;
    this.jmsTemplate = jmsTemplate;
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
      description = "Remove subscription and capacity records that are in the denylist.")
  public void pruneUnlistedSubscriptions() {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Prune of unlisted subscriptions triggered over JMX by {}", principal);
    subscriptionPruneController.pruneAllUnlistedSubscriptions();
  }

  @ManagedOperation(description = "Save subscriptions manually. Supported only in dev-mode.")
  @ManagedOperationParameter(
      name = "subscriptionsJson",
      description = "JSON array containing subscriptions to save")
  @ManagedOperationParameter(
      name = "reconcileCapacity",
      description =
          "Invoke reconciliation logic to create capacity? (hint: offering for the SKU must be present)")
  public void saveSubscriptions(String subscriptionsJson, boolean reconcileCapacity) {
    if (!properties.isDevMode() && !properties.isManualSubscriptionEditingEnabled()) {
      throw new JmxException(NOT_ENABLED_MESSAGE);
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

  @ManagedOperation(description = "Sync UMB subscription manually. Supported only in dev-mode.")
  @ManagedOperationParameter(name = "subscriptionXml", description = "XML containing a UMB message")
  public void syncSubscriptionFromXml(String subscriptionXml) {
    if (!properties.isDevMode() && !properties.isManualSubscriptionEditingEnabled()) {
      throw new JmxException(NOT_ENABLED_MESSAGE);
    }
    try {
      Object principal = ResourceUtils.getPrincipal();
      log.info("Save of UMB subscription triggered over JMX by {}", principal);
      subscriptionSyncController.saveUmbSubscriptionFromXml(subscriptionXml);
    } catch (Exception e) {
      log.error("Error saving subscription", e);
      throw new JmxException("Error saving subscription. See log for details.");
    }
  }

  @ManagedOperation(
      description =
          "Enqueue UMB subscription XML. Supported only in dev-mode. Won't work against actual UMB brokers.")
  @ManagedOperationParameter(name = "subscriptionXml", description = "XML containing a UMB message")
  public void enqueueSubscriptionXml(String subscriptionXml) {
    if (!properties.isDevMode() && !properties.isManualSubscriptionEditingEnabled()) {
      throw new JmxException(NOT_ENABLED_MESSAGE);
    }
    Object principal = ResourceUtils.getPrincipal();
    jmsTemplate.convertAndSend(umbProperties.getSubscriptionTopic(), subscriptionXml);
    log.info("{} enqueued message to topic {}", principal, umbProperties.getSubscriptionTopic());
  }

  @ManagedOperation(description = "Delete a subscription manually. Supported only in dev-mode.")
  public void deleteSubscription(String subscriptionId) {
    if (!properties.isDevMode() && !properties.isManualSubscriptionEditingEnabled()) {
      throw new JmxException(NOT_ENABLED_MESSAGE);
    }
    Object principal = ResourceUtils.getPrincipal();
    log.info("Save of new subscriptions triggered over JMX by {}", principal);
    subscriptionSyncController.deleteSubscription(subscriptionId);
  }
}
