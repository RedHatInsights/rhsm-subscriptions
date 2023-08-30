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
package org.candlepin.subscriptions.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.doThrow;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.when;

import org.candlepin.subscriptions.tally.TallyTaskFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskWorkerTest {

  @Mock private TallyTaskFactory factory;

  @Mock private Task mockTask;

  @Test
  void testExecuteTask() throws Exception {
    TaskWorker worker = new TaskWorker(factory);
    when(factory.build(any(TaskDescriptor.class))).thenReturn(mockTask);
    worker.executeTask(TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, "group", null).build());
    verify(mockTask).execute();
  }

  @Test
  void testThrowsTaskExecutionExceptionOnTaskFailure() throws Exception {
    TaskWorker worker = new TaskWorker(factory);
    when(factory.build(any(TaskDescriptor.class))).thenReturn(mockTask);
    doThrow(RuntimeException.class).when(mockTask).execute();
    assertThrows(
        TaskExecutionException.class,
        () ->
            worker.executeTask(
                TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, "group", null).build()));
  }
}
