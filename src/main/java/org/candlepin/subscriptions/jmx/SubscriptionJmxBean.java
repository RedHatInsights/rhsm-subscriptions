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
package org.candlepin.subscriptions.jmx;

import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

@Component
@ManagedResource
@Slf4j
public class SubscriptionJmxBean {

  SubscriptionSyncController subscriptionSyncController;

  OrgConfigRepository orgConfigRepository;

  SubscriptionJmxBean(
      SubscriptionSyncController subscriptionSyncController,
      OrgConfigRepository orgConfigRepository) {
    this.subscriptionSyncController = subscriptionSyncController;
    this.orgConfigRepository = orgConfigRepository;
  }

  @ManagedOperation
  void syncSubscription(String subscriptionId) {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Sync for subscription {} triggered over JMX by {}", subscriptionId, principal);
    subscriptionSyncController.syncSubscription(subscriptionId);
  }

  @ManagedOperation
  void syncSubscriptionsForOrg(String orgId) {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Sync for org {} triggered over JMX by {}", orgId, principal);
    subscriptionSyncController.syncAllSubcriptionsForOrg(orgId);
  }

  @Transactional
  @ManagedOperation(description = "Sync all subscriptions for sync-enabled orgs.")
  public void syncAllSubscriptions() throws SubscriptionJmxException {
    try {
      log.info("syncAllSubscriptions MBean operation invoked");
      orgConfigRepository
          .findSyncEnabledOrgs()
          .forEach(subscriptionSyncController::syncAllSubcriptionsForOrg);
    } catch (Exception ex) {
      log.error("Error occurred while handling syncAllSubscriptions Mbean operation", ex);
      throw new SubscriptionJmxException(ex);
    }
  }
}
