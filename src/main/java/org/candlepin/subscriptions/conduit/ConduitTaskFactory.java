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
package org.candlepin.subscriptions.conduit;

import org.candlepin.subscriptions.conduit.tasks.UpdateOrgInventoryTask;
import org.candlepin.subscriptions.task.Task;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskFactory;
import org.candlepin.subscriptions.task.TaskType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Converts a conduit task descriptor into an invocable task. */
@Component
public class ConduitTaskFactory implements TaskFactory {
  @Autowired InventoryController inventoryController;

  /**
   * Builds a Task instance based on the specified TaskDescriptor.
   *
   * @param taskDescriptor the task descriptor that is used to customize the Task that is to be
   *     created.
   * @return the Task defined by the descriptor.
   */
  @Override
  public Task build(TaskDescriptor taskDescriptor) {
    if (taskDescriptor.getTaskType() == TaskType.UPDATE_ORG_INVENTORY) {
      return new UpdateOrgInventoryTask(
          inventoryController,
          taskDescriptor.getArg("org_id").get(0),
          taskDescriptor.getArg("offset").get(0));
    }
    throw new IllegalArgumentException(
        "Could not build task. Unknown task type: " + taskDescriptor.getTaskType());
  }
}
