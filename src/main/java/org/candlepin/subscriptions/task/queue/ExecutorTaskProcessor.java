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
package org.candlepin.subscriptions.task.queue;

import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskExecutionException;
import org.candlepin.subscriptions.task.TaskFactory;
import org.candlepin.subscriptions.task.TaskWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Processor that is responsible for running queued tasks.
 *
 * Uses a separate thread to convert TaskDescriptors into actual tasks.
 *
 * @see ExecutorTaskQueue
 */
public class ExecutorTaskProcessor {
    private static final Logger log = LoggerFactory.getLogger(ExecutorTaskProcessor.class);

    private final ExecutorService executor;
    private final ExecutorTaskQueue queue;
    private final TaskFactory taskFactory;
    private final Thread thread;

    public ExecutorTaskProcessor(ExecutorService executor, TaskFactory taskFactory,
        ExecutorTaskQueue queue) {
        this.executor = executor;
        this.taskFactory = taskFactory;
        this.queue = queue;
        this.thread = new Thread(this::run);
        this.thread.start();
    }

    private void processTask(TaskDescriptor taskDescriptor) {
        TaskWorker worker = new TaskWorker(taskFactory);
        try {
            worker.executeTask(taskDescriptor);
        }
        catch (TaskExecutionException e) {
            log.error("An error occurred running a task.", e);
        }
    }

    /**
     * Shut down the associated executor gracefully, and wait for any pending tasks to complete.
     *
     * Used mainly for testing.
     *
     * @param timeout the maximum time to wait
     * @param timeUnit the time unit of the timeout argument
     * @throws InterruptedException
     */
    public void shutdown(long timeout, TimeUnit timeUnit) throws InterruptedException {
        this.queue.shutdown();
        this.thread.join();
        this.executor.shutdown();
        this.executor.awaitTermination(timeout, timeUnit);
    }

    private void run() {
        log.info("Starting in-memory task processor");
        while (true) {
            try {
                Optional<TaskDescriptor> task = queue.take();
                if (task.isPresent()) {
                    this.executor.execute(() -> this.processTask(task.get()));
                }
                else {
                    log.info("Stopping in-memory task processor");
                    break;
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
