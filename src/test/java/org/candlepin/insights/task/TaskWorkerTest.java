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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
public class TaskWorkerTest {

    @MockBean
    private TaskFactory factory;

    @Mock
    private Task mockTask;

    @Test
    public void testExecuteTask() throws Exception {
        TaskWorker worker = new TaskWorker(factory);
        when(factory.build(any(TaskDescriptor.class))).thenReturn(mockTask);
        worker.executeTask(TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, "group").build());
        verify(mockTask).execute();
    }

    @Test
    public void testThrowsTaskExecutionExceptionOnTaskFailure() throws Exception {
        TaskWorker worker = new TaskWorker(factory);
        when(factory.build(any(TaskDescriptor.class))).thenReturn(mockTask);
        doThrow(RuntimeException.class).when(mockTask).execute();
        assertThrows(TaskExecutionException.class,
            () -> worker.executeTask(TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, "group").build()));
    }
}
