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
package org.candlepin.subscriptions.subscription.job;

import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.subscription.tasks.SyncSubscriptionsTask;
import org.candlepin.subscriptions.task.Task;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskFactory;
import org.candlepin.subscriptions.task.TaskType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Task factory for creating subscription-related tasks.
 */
@Component
public class SubscriptionTaskFactory implements TaskFactory {

    @Autowired
    SubscriptionSyncController subscriptionSyncController;

    /**
     * Build a task based on descriptor.
     * @param taskDescriptor the descriptor of the task to build.
     * @return a task based on the descriptor.
     */
    @Override
    public Task build(TaskDescriptor taskDescriptor) {
        if (TaskType.SYNC_ORG_SUBSCRIPTIONS == taskDescriptor.getTaskType()) {
            return new SyncSubscriptionsTask(subscriptionSyncController,
                taskDescriptor.getArg("orgId").get(0),
                taskDescriptor.getArg("offset").get(0),
                taskDescriptor.getArg("limit").get(0));
        }
        throw new IllegalArgumentException("Could not build task. Unknown task type: " +
            taskDescriptor.getTaskType());
    }
}
