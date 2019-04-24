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
package org.candlepin.insights.task.queue.kafka;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.candlepin.insights.task.Task;
import org.candlepin.insights.task.TaskDescriptor;
import org.candlepin.insights.task.TaskFactory;
import org.candlepin.insights.task.TaskManager;
import org.candlepin.insights.task.TaskQueueConfiguration;
import org.candlepin.insights.task.TaskType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


@SpringBootTest
@TestPropertySource("classpath:/kafka_test.properties")
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = {TaskQueueConfiguration.TASK_GROUP})
public class KafkaTaskQueueTest {

    @MockBean
    private TaskFactory factory;

    @Autowired
    private TaskManager manager;

    @Test
    public void testSendAndReceiveTaskMessage() throws InterruptedException {
        String orgId = "test_org";
        TaskDescriptor taskDescriptor = TaskDescriptor.builder(
            TaskType.UPDATE_ORG_INVENTORY, TaskQueueConfiguration.TASK_GROUP)
            .setArg("org_id", orgId)
            .build();

        // Expect the task to be ran once.
        CountDownLatch latch = new CountDownLatch(1);
        CountDownTask cdt = new CountDownTask(latch);

        when(factory.build(eq(taskDescriptor))).thenReturn(cdt);

        manager.updateOrgInventory(orgId);

        // Wait a max of 5 seconds for the task to be executed
        latch.await(5L, TimeUnit.SECONDS);
        assertTrue(cdt.taskWasExecuted(), "The task failed to execute. The message was not received.");
    }

    /**
     * A testing Task that uses a latch to allow the calling test to know that it has been executed.
     * It provides an executed field to allow tests to verify that the Task has actually been run
     * in cases where latch.await(timeout) times out waiting for it to execute.
     */
    private class CountDownTask implements Task {

        private CountDownLatch latch;
        private boolean executed;

        public CountDownTask(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void execute() {
            executed = true;
            latch.countDown();
        }

        public boolean taskWasExecuted() {
            return executed;
        }
    }

}
