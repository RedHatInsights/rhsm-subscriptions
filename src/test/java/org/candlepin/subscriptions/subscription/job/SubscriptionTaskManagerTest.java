/*
 * Copyright (c) 2019 - 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.subscription.job;

import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.TaskType;
import org.candlepin.subscriptions.task.queue.inmemory.ExecutorTaskQueue;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = FixedClockConfiguration.class)
@ActiveProfiles({"worker", "test"})
class SubscriptionTaskManagerTest {

    @MockBean
    private ExecutorTaskQueue taskQueue;

    @Autowired
    @Qualifier("subscriptionTaskQueueProperties")
    private TaskQueueProperties taskQueueProperties;

    @Autowired
    private SubscriptionTaskManager subject;

    @Test
    void shouldEnqueueOrgSubscriptionSyncTaskTest() {
        subject.syncSubscriptionsForOrg("123", 0, 100L);
        Mockito.verify(taskQueue).enqueue(TaskDescriptor.builder(TaskType.SYNC_ORG_SUBSCRIPTIONS,
            taskQueueProperties.getTopic())
            .setSingleValuedArg("orgId", "123")
            .setSingleValuedArg("offset", "0")
            .setSingleValuedArg("limit", "100")
            .build());
    }
}
