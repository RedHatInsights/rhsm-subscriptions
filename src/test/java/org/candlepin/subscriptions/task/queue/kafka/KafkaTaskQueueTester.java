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
package org.candlepin.subscriptions.task.queue.kafka;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.candlepin.subscriptions.tally.TallyTaskFactory;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsTaskManager;
import org.candlepin.subscriptions.task.Task;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.TaskType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

/** Base class for testing message sending and receiving via Kafka. */
public class KafkaTaskQueueTester {

  @MockBean private TallyTaskFactory factory;

  @Autowired private CaptureSnapshotsTaskManager manager;

  @Autowired private TaskQueueProperties taskQueueProperties;

  protected void runSendAndReceiveTaskMessageTestWithOrg() throws InterruptedException {
    String org = "o1";
    TaskDescriptor taskDescriptor =
        TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, taskQueueProperties.getTopic(), null)
            .setSingleValuedArg("orgs", org)
            .build();

    // Expect the task to be ran once.
    CountDownLatch latch = new CountDownLatch(1);
    CountDownTask cdt = new CountDownTask(latch);

    when(factory.build(taskDescriptor)).thenReturn(cdt);

    manager.updateOrgSnapshots(org);

    // Wait a max of 5 seconds for the task to be executed
    latch.await(5L, TimeUnit.SECONDS);
    assertTrue(cdt.taskWasExecuted(), "The task failed to execute. The message was not received.");
  }

  /**
   * A testing Task that uses a latch to allow the calling test to know that it has been executed.
   * It provides an executed field to allow tests to verify that the Task has actually been run in
   * cases where latch.await(timeout) times out waiting for it to execute.
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
