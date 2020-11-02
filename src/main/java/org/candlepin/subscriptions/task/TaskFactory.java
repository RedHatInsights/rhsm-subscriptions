/*
 * Copyright (c) 2019 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.task;

import org.candlepin.subscriptions.controller.TallySnapshotController;
import org.candlepin.subscriptions.task.tasks.UpdateAccountSnapshotsTask;

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
    private TallySnapshotController snapshotController;

    /**
     * Builds a Task instance based on the specified TaskDescriptor.
     *
     * @param taskDescriptor the task descriptor that is used to customize the Task that is to be created.
     *
     * @return the Task defined by the descriptor.
     */
    public Task build(TaskDescriptor taskDescriptor) {
        if (TaskType.UPDATE_SNAPSHOTS.equals(taskDescriptor.getTaskType())) {
            return new UpdateAccountSnapshotsTask(snapshotController, taskDescriptor.getArg("accounts"));
        }
        throw new IllegalArgumentException("Could not build task. Unknown task type: " +
            taskDescriptor.getTaskType());
    }

}
