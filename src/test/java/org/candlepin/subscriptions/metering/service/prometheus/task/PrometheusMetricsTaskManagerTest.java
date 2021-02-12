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
package org.candlepin.subscriptions.metering.service.prometheus.task;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.subscriptions.db.AccountListSource;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.TaskType;
import org.candlepin.subscriptions.task.queue.TaskQueue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Arrays;


@ExtendWith(MockitoExtension.class)
class PrometheusMetricsTaskManagerTest {

    private static final String TASK_TOPIC = "metrics-tasks-topic";

    @Mock
    private TaskQueue queue;

    @Mock
    private AccountListSource accountSource;

    @Mock
    private TaskQueueProperties queueProperties;

    private PrometheusMetricsTaskManager manager;

    @BeforeEach
    void setupTest() {
        when(queueProperties.getTopic()).thenReturn(TASK_TOPIC);
        manager = new PrometheusMetricsTaskManager(queue, queueProperties, accountSource);
    }

    @Test
    void updateForSingleAccount() throws Exception {
        String account = "single-account";
        OffsetDateTime end = OffsetDateTime.now();
        OffsetDateTime start = end.minusDays(1);

        TaskDescriptor expectedTask = TaskDescriptor.builder(
            TaskType.OPENSHIFT_METRICS_COLLECTION, TASK_TOPIC)
            .setSingleValuedArg("account", account)
            .setSingleValuedArg("start", start.toString())
            .setSingleValuedArg("end", end.toString())
            .build();
        manager.updateOpenshiftMetricsForAccount(account, start, end);
        verify(queue).enqueue(expectedTask);
    }

    @Test
    void updateForConfiguredAccounts() throws Exception {
        OffsetDateTime end = OffsetDateTime.now();
        OffsetDateTime start = end.minusDays(1);

        when(accountSource.syncableAccounts()).thenReturn(Arrays.asList("a1", "a2").stream());
        TaskDescriptor account1Task =
            TaskDescriptor.builder(TaskType.OPENSHIFT_METRICS_COLLECTION, TASK_TOPIC)
            .setSingleValuedArg("account", "a1")
            .setSingleValuedArg("start", start.toString())
            .setSingleValuedArg("end", end.toString())
            .build();
        TaskDescriptor account2Task =
            TaskDescriptor.builder(TaskType.OPENSHIFT_METRICS_COLLECTION, TASK_TOPIC)
            .setSingleValuedArg("account", "a2")
            .setSingleValuedArg("start", start.toString())
            .setSingleValuedArg("end", end.toString())
            .build();

        manager.updateOpenshiftMetricsForAllAccounts(start, end);
        verify(queue).enqueue(account1Task);
        verify(queue).enqueue(account2Task);
        verifyNoMoreInteractions(queue);
    }
}
