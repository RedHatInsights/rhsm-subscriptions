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
package org.candlepin.subscriptions.task.queue.inmemory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.candlepin.subscriptions.tally.TallyTaskFactory;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutorTaskQueueTest {

  @Mock TallyTaskFactory taskFactory;

  @Test
  void ensureTaskIsExecutedPriorToShutdown() throws InterruptedException {
    ExecutorTaskQueue queue = new ExecutorTaskQueue();
    ExecutorTaskProcessor processor =
        new ExecutorTaskProcessor(Executors.newCachedThreadPool(), taskFactory, queue, "my-group");
    TaskDescriptor expectedTaskDesc =
        TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, "my-group", null).build();
    final AtomicBoolean done = new AtomicBoolean();
    Mockito.when(taskFactory.build(Mockito.any()))
        .thenReturn(
            () -> {
              done.set(true);
            });
    queue.enqueue(expectedTaskDesc);
    processor.shutdown(2000, TimeUnit.MILLISECONDS);
    assertTrue(done.get());
  }

  @Test
  void verifyNoExceptionWhenTaskFails() throws InterruptedException {
    AtomicBoolean failed = new AtomicBoolean();
    ExecutorTaskQueue queue = new ExecutorTaskQueue();
    ExecutorTaskProcessor processor =
        new ExecutorTaskProcessor(
            Executors.newCachedThreadPool(
                (runnable) -> {
                  Thread thread = new Thread(runnable);
                  thread.setUncaughtExceptionHandler(
                      (_thread, throwable) -> {
                        failed.set(true);
                      });
                  return thread;
                }),
            taskFactory,
            queue,
            "my-group");
    TaskDescriptor expectedTaskDesc =
        TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, "my-group", null).build();
    Mockito.when(taskFactory.build(Mockito.any()))
        .thenReturn(
            () -> {
              throw new RuntimeException("Error!");
            });
    queue.enqueue(expectedTaskDesc);
    processor.shutdown(2000, TimeUnit.MILLISECONDS);
    assertFalse(failed.get());
  }
}
