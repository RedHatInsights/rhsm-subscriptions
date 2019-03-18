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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
public class TaskWorkerTest {

    @MockBean
    private TaskFactory factory;

    @Autowired
    private TaskWorker worker;

    @Mock
    private Task mockTask;

    @Test
    public void testExecuteTask() throws Exception {
        when(factory.build(any(TaskDescriptor.class))).thenReturn(mockTask);
        worker.executeTask(TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, "group").build());
        verify(mockTask).execute();
    }

    @Test
    public void testThrowsTaskExecutionExceptionOnTaskFailure() throws Exception {
        when(factory.build(any(TaskDescriptor.class))).thenReturn(mockTask);
        doThrow(RuntimeException.class).when(mockTask).execute();
        assertThrows(TaskExecutionException.class,
            () -> worker.executeTask(TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, "group").build()));
    }
}
