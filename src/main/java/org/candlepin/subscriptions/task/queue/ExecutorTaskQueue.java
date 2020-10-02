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

import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.task.TaskDescriptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;

import javax.ws.rs.core.Response;

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

    private final BlockingQueue<Optional<TaskDescriptor>> queue;

    public ExecutorTaskQueue() {
        queue = new SynchronousQueue<>();
    }

    @Override
    public void enqueue(TaskDescriptor taskDescriptor) {
        try {
            queue.put(Optional.of(taskDescriptor));
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SubscriptionsException(
                ErrorCode.UNHANDLED_EXCEPTION_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                "Interrupted while trying to queue a task.",
                e
            );
        }
    }

    Optional<TaskDescriptor> take() throws InterruptedException {
        return queue.take();
    }

    void shutdown() {
        try {
            queue.put(Optional.empty());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
