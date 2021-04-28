/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.subscription.jmx;

import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

/**
 * JMX bean for synchronizing subscriptions.
 */
@Component
@ManagedResource
@SuppressWarnings("java:S2139")
public class SubscriptionJmxBean {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionJmxBean.class);

    private final SubscriptionSyncController subscriptionSyncController;
    private final OrgConfigRepository orgConfigRepository;

    public SubscriptionJmxBean(SubscriptionSyncController subscriptionSyncController,
        OrgConfigRepository orgConfigRepository) {
        this.subscriptionSyncController = subscriptionSyncController;
        this.orgConfigRepository = orgConfigRepository;
    }

    @Transactional
    @ManagedOperation(description = "Sync all subscriptions for sync-enabled orgs.")
    public void syncAllSubscriptions() throws SubscriptionJmxException {
        try {
            log.info("syncAllSubscriptions MBean operation invoked");
            orgConfigRepository.findSyncEnabledOrgs().forEach(
                subscriptionSyncController::syncAllSubcriptionsForOrg);
        }
        catch (Exception ex) {
            log.error("Error occurred while handling syncAllSubscriptions Mbean operation", ex);
            throw new SubscriptionJmxException(ex);
        }
    }

    @ManagedOperation(description = "Sync subscriptions for a specific org by org ID.")
    public void syncSubscriptionForOrg(String orgId) throws SubscriptionJmxException {
        try {
            log.info("syncSubscriptionForOrg MBean operation invoked for orgId={}", orgId);
            subscriptionSyncController.syncAllSubcriptionsForOrg(orgId);
        }
        catch (Exception ex) {
            log.error("Error occurred while handling syncSubscriptionForOrg Mbean operation", ex);
            throw new SubscriptionJmxException(ex);
        }
    }

    @ManagedOperation(description = "Sync a subscription specified by subscription ID.")
    public void syncSubscription(String subscriptionId) throws SubscriptionJmxException {
        try {
            log.info("syncSubscription MBean operation invoked for subscriptionId={}", subscriptionId);
            subscriptionSyncController.syncSubscription(subscriptionId);
        }
        catch (Exception ex) {
            log.error("Error occurred while handling syncSubscription Mbean operation", ex);
            throw new SubscriptionJmxException(ex);
        }
    }
}
