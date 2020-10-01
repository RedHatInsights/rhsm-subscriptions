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
package org.candlepin.subscriptions.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.candlepin.subscriptions.orgsync.db.DatabaseOrgList;
import org.candlepin.subscriptions.task.queue.ExecutorTaskProcessor;
import org.candlepin.subscriptions.task.queue.TaskQueue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.stream.Stream;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
public class TaskManagerTest {

    @MockBean
    private TaskQueue queue;

    @MockBean
    private ExecutorTaskProcessor processor;

    @Autowired
    private TaskManager manager;

    @MockBean
    private DatabaseOrgList orgList;

    @Autowired
    private TaskQueueProperties taskQueueProperties;

    @Test
    public void testUpdateOrgInventory() {
        String expectedOrg = "my-org";
        manager.updateOrgInventory(expectedOrg, null);

        verify(queue).enqueue(eq(createDescriptor(expectedOrg)));
    }

    @Test
    public void ensureUpdateIsRunForEachOrg() throws Exception {
        Stream<String> expectedOrgs = Stream.of("org_a", "org_b");
        when(orgList.getOrgsToSync()).thenReturn(expectedOrgs);

        manager.syncFullOrgList();

        verify(queue, times(1)).enqueue(eq(createDescriptor("org_a")));
        verify(queue, times(1)).enqueue(eq(createDescriptor("org_b")));
    }

    @Test
    public void ensureOrgLimitIsEnforced() throws Exception {
        Stream<String> expectedOrgs = Stream.of("org_a", "org_b", "org_c");
        when(orgList.getOrgsToSync()).thenReturn(expectedOrgs);

        manager.syncFullOrgList();

        verify(queue, times(1)).enqueue(eq(createDescriptor("org_a")));
        verify(queue, times(1)).enqueue(eq(createDescriptor("org_b")));
        verify(queue, never()).enqueue(eq(createDescriptor("org_c")));
    }

    @Test
    public void ensureErrorOnUpdateContinuesWithoutFailure() throws Exception {
        Stream<String> expectedOrgs = Stream.of("org_a", "org_b");
        when(orgList.getOrgsToSync()).thenReturn(expectedOrgs);

        doThrow(new RuntimeException("Forced!")).when(queue).enqueue(eq(createDescriptor("org_a")));

        manager.syncFullOrgList();

        verify(queue, times(1)).enqueue(eq(createDescriptor("org_a")));
        verify(queue, times(1)).enqueue(eq(createDescriptor("org_b")));
    }

    @Test
    public void ensureNoUpdatesWhenOrgListCanNotBeRetreived() throws Exception {
        doThrow(new RuntimeException("Forced!")).when(orgList).getOrgsToSync();

        assertThrows(RuntimeException.class, () -> {
            manager.syncFullOrgList();
        });

        verify(queue, never()).enqueue(any());
    }

    private TaskDescriptor createDescriptor(String org) {
        return TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, taskQueueProperties.getTaskGroup())
            .setArg("org_id", org)
            .build();
    }
}
