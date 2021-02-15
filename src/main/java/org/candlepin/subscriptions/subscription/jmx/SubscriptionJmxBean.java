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

import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JMX bean for synchronizing subscriptions.
 */
@Component
@ManagedResource
@SuppressWarnings("java:S2139")
public class SubscriptionJmxBean {

    private final SubscriptionSyncController subscriptionSyncController;
    private final OrgConfigRepository orgConfigRepository;

    public SubscriptionJmxBean(SubscriptionSyncController subscriptionSyncController,
        OrgConfigRepository orgConfigRepository) {
        this.subscriptionSyncController = subscriptionSyncController;
        this.orgConfigRepository = orgConfigRepository;
    }

    @ManagedOperation(description = "Sync all subscriptions for sync-enabled orgs.")
    public void syncAllSubscriptions() throws SubscriptionJmxException {
        try {
            // because java still does not support throwing exceptions to higher scopes from a lambda
            // we must collect the stream and then old-school for loop
            final List<String> orgs = orgConfigRepository.findSyncEnabledOrgs().collect(Collectors.toList());
            for (String org: orgs) {
                subscriptionSyncController.syncAllSubcriptionsForOrg(org);
            }
        }
        catch (Exception ex) {
            throw new SubscriptionJmxException(ex);
        }
    }

    @ManagedOperation(description = "Sync subscriptions for a specific org by org ID.")
    public void syncSubscriptionForOrg(String orgId) throws SubscriptionJmxException {
        try {
            subscriptionSyncController.syncAllSubcriptionsForOrg(orgId);
        }
        catch (Exception ex) {
            throw new SubscriptionJmxException(ex);
        }
    }

    @ManagedOperation(description = "Sync a subscription specified by subscription ID.")
    public void syncSubscription(String subscriptionId) throws SubscriptionJmxException {
        try {
            subscriptionSyncController.syncSubscription(subscriptionId);
        }
        catch (Exception ex) {
            throw new SubscriptionJmxException(ex);
        }
    }
}
