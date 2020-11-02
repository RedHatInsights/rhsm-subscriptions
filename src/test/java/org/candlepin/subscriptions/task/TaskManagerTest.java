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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.doThrow;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.when;

import org.candlepin.subscriptions.tally.AccountListSource;
import org.candlepin.subscriptions.tally.AccountListSourceException;
import org.candlepin.subscriptions.task.queue.TaskQueue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;


@SpringBootTest
@TestPropertySource("classpath:/test.properties")
public class TaskManagerTest {

    @MockBean
    private TaskQueue queue;

    @Autowired
    private TaskManager manager;

    @MockBean
    private AccountListSource accountListSource;

    @Autowired
    private TaskQueueProperties taskQueueProperties;

    @Test
    public void testUpdateForSingleAccount() {
        String account = "12345";
        manager.updateAccountSnapshots(account);

        verify(queue).enqueue(eq(createDescriptor(account)));
    }

    @Test
    public void ensureUpdateIsRunForEachAccount() throws Exception {
        List<String> expectedAccounts = Arrays.asList("a1", "a2");
        when(accountListSource.syncableAccounts()).thenReturn(expectedAccounts.stream());

        manager.updateSnapshotsForAllAccounts();

        verify(queue, times(1)).enqueue(eq(createDescriptor(expectedAccounts)));
    }

    @Test
    public void ensureAccountListIsPartitionedWhenSendingTaskMessages() throws Exception {
        List<String> expectedAccounts = Arrays.asList("a1", "a2", "a3", "a4");
        when(accountListSource.syncableAccounts()).thenReturn(expectedAccounts.stream());

        manager.updateSnapshotsForAllAccounts();

        // NOTE: Partition size is defined in test.properties
        verify(queue, times(1)).enqueue(eq(createDescriptor(Arrays.asList("a1", "a2"))));
        verify(queue, times(1)).enqueue(eq(createDescriptor(Arrays.asList("a3", "a4"))));
    }

    @Test
    public void ensureLastAccountListPartitionIsIncludedWhenSendingTaskMessages() throws Exception {
        List<String> expectedAccounts = Arrays.asList("a1", "a2", "a3", "a4", "a5");
        when(accountListSource.syncableAccounts()).thenReturn(expectedAccounts.stream());

        manager.updateSnapshotsForAllAccounts();

        // NOTE: Partition size is defined in test.properties
        verify(queue, times(1)).enqueue(eq(createDescriptor(Arrays.asList("a1", "a2"))));
        verify(queue, times(1)).enqueue(eq(createDescriptor(Arrays.asList("a3", "a4"))));
        verify(queue, times(1)).enqueue(eq(createDescriptor(Arrays.asList("a5"))));
    }

    @Test
    public void ensureErrorOnUpdateContinuesWithoutFailure() throws Exception {
        List<String> expectedAccounts = Arrays.asList("a1", "a2", "a3", "a4", "a5", "a6");
        when(accountListSource.syncableAccounts()).thenReturn(expectedAccounts.stream());

        doThrow(new RuntimeException("Forced!"))
            .when(queue).enqueue(eq(createDescriptor(Arrays.asList("a3", "a4"))));

        manager.updateSnapshotsForAllAccounts();

        verify(queue, times(1)).enqueue(eq(createDescriptor(Arrays.asList("a1", "a2"))));
        verify(queue, times(1)).enqueue(eq(createDescriptor(Arrays.asList("a3", "a4"))));
        // Even though a3,a4 throws exception, a5,a6 should be enqueued.
        verify(queue, times(1)).enqueue(eq(createDescriptor(Arrays.asList("a5", "a6"))));
    }

    @Test
    public void ensureNoUpdatesWhenAccountListCanNotBeRetreived() throws Exception {
        doThrow(new AccountListSourceException("Forced!", new RuntimeException()))
            .when(accountListSource).syncableAccounts();

        assertThrows(TaskManagerException.class, () -> {
            manager.updateSnapshotsForAllAccounts();
        });

        verify(queue, never()).enqueue(any());
    }

    private TaskDescriptor createDescriptor(String account) {
        return createDescriptor(Arrays.asList(account));
    }

    private TaskDescriptor createDescriptor(List<String> accounts) {
        return TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, taskQueueProperties.getTaskGroup())
            .setArg("accounts", accounts)
            .build();
    }
}
