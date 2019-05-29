/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.insights.task;

import org.candlepin.insights.task.queue.TaskQueue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * A TaskManager is an injectable component that is responsible for putting tasks into
 * the TaskQueue. This is the entry point into the task system. Any class that would like
 * to initiate a task within conduit, should inject this class and call the appropriate
 * method.
 */
@Component
public class TaskManager {

    @Autowired
    TaskQueueProperties taskQueueProperties;

    @Autowired
    TaskQueue queue;

    /**
     * Initiates a task that will update the inventory of the specified organization's ID.
     *
     * @param orgId the ID of the org in which to update.
     */
    @SuppressWarnings("indentation")
    public void updateOrgInventory(String orgId) {
        queue.enqueue(
            TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, taskQueueProperties.getTaskGroup())
                .setArg("org_id", orgId)
                .build()
        );
    }

}
