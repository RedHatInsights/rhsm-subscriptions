/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
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
    TaskQueue queue;

    /**
     * Initiates a task that will update the inventory of the specified organization's ID.
     *
     * @param orgId the ID of the org in which to update.
     */
    @SuppressWarnings("indentation")
    public void updateOrgInventory(String orgId) {
        queue.enqueue(
            TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, TaskQueueConfiguration.TASK_GROUP)
                .setArg("org_id", orgId)
                .build()
        );
    }

}
