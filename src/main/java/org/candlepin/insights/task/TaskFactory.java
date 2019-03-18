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

import org.candlepin.insights.controller.InventoryController;
import org.candlepin.insights.task.tasks.UpdateOrgInventoryTask;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * A class responsible for a TaskDescriptor into actual Task instances. Task instances are build via the
 * build(TaskDescriptor) method. The type of Task that will be built is determined by the descriptor's
 * TaskType property.
 */
@Component
public class TaskFactory {

    @Autowired
    private InventoryController inventoryController;

    /**
     * Builds a Task instance based on the specified TaskDescriptor.
     *
     * @param taskDescriptor the task descriptor that is used to customize the Task that is to be created.
     *
     * @return the Task defined by the descriptor.
     */
    public Task build(TaskDescriptor taskDescriptor) {
        if (TaskType.UPDATE_ORG_INVENTORY.equals(taskDescriptor.getTaskType())) {
            return new UpdateOrgInventoryTask(inventoryController, taskDescriptor.getArg("org_id"));
        }
        throw new IllegalArgumentException("Could not build task. Unknown task type: " +
            taskDescriptor.getTaskType());
    }
}
