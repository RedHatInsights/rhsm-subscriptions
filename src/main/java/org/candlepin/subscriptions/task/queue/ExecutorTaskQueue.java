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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

/**
 * An TaskQueue implementation that uses an {@link ExecutorService} to queue/execute tasks.
 *
 * The behavior of the TaskQueue is dependent on the {@link ExecutorService} instance that is passed to the
 * constructor.
 *
 * If configured with {@link Executors#newCachedThreadPool()}, the queuing is done by starting a new thread
 * for each queued task.
 *
 * If configured with {@link Executors#newSingleThreadExecutor()}, then the task queue is backed by an
 * unbound queue.
 *
 * Custom queuing mechanisms can be implemented by configuring a
 * {@link java.util.concurrent.ThreadPoolExecutor} with a custom implementation of
 * {@link java.util.concurrent.BlockingQueue}.
 *
 * @see ExecutorService
 * @see Executors
 */
public class ExecutorTaskQueue implements TaskQueue {
    private static final Logger log = LoggerFactory.getLogger(ExecutorTaskQueue.class);

    private final ExecutorService executor;
    private final TaskFactory taskFactory;

    public ExecutorTaskQueue(ExecutorService executor, TaskFactory taskFactory) {
        this.executor = executor;
        this.taskFactory = taskFactory;
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

    @PreDestroy
    protected void destroy() throws InterruptedException {
        shutdown(Integer.MAX_VALUE, TimeUnit.DAYS);
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
        this.executor.shutdown();
        this.executor.awaitTermination(timeout, timeUnit);
    }

    @Override
    public void enqueue(TaskDescriptor taskDescriptor) {
        this.executor.execute(() -> this.processTask(taskDescriptor));
    }
}
