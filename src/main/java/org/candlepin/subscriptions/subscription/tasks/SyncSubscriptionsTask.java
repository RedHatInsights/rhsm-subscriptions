/*
 * Copyright (c) 2019 - 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.subscription.tasks;

import org.candlepin.subscriptions.subscription.ApiException;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.task.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task for synchronizing subscriptions for an org.
 */
public class SyncSubscriptionsTask implements Task {
    private static final Logger log = LoggerFactory.getLogger(SyncSubscriptionsTask.class);

    private final SubscriptionSyncController subscriptionSyncController;
    private final String orgId;
    private final String offset;
    private final String limit;

    public SyncSubscriptionsTask(SubscriptionSyncController subscriptionSyncController, String orgId,
        String offset, String limit) {
        this.subscriptionSyncController = subscriptionSyncController;
        this.orgId = orgId;
        this.offset = offset;
        this.limit = limit;
    }

    @Override
    public void execute() {
        try {
            subscriptionSyncController.syncSubscriptions(orgId, offset, limit);
        }
        catch (ApiException e) {
            log.error("Exception calling RHSM", e);
        }
    }
}
