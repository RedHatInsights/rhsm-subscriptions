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
package org.candlepin.insights.task.queue.passthrough;

import static org.mockito.BDDMockito.*;

import org.candlepin.insights.task.TaskDescriptor;
import org.candlepin.insights.task.TaskExecutionException;
import org.candlepin.insights.task.TaskType;
import org.candlepin.insights.task.TaskWorker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class PassThroughTaskQueueTest {

    @Mock
    private TaskWorker worker;

    @Test
    public void ensureTaskIsExecutedImmediately() throws TaskExecutionException {
        PassThroughTaskQueue queue = new PassThroughTaskQueue(worker);
        TaskDescriptor expectedTaskDesc =
            TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, "my-group").build();
        queue.enqueue(expectedTaskDesc);
        Mockito.verify(worker).executeTask(eq(expectedTaskDesc));
    }

    @Test
    public void verifyNoExceptionWhenTaskFails() throws Exception {
        PassThroughTaskQueue queue = new PassThroughTaskQueue(worker);
        TaskDescriptor expectedTaskDesc =
            TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, "my-group").build();

        Mockito.doThrow(TaskExecutionException.class).when(worker).executeTask(eq(expectedTaskDesc));
        queue.enqueue(expectedTaskDesc);
        Mockito.verify(worker).executeTask(eq(expectedTaskDesc));
    }
}
