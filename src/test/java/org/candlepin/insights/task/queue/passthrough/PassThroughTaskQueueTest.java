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
