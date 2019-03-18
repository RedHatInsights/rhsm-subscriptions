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
package org.candlepin.insights.task.queue.passthrough;

import org.candlepin.insights.task.TaskDescriptor;
import org.candlepin.insights.task.TaskExecutionException;
import org.candlepin.insights.task.TaskWorker;
import org.candlepin.insights.task.queue.TaskQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A TaskQueue implementation that simply immediately executes a Task as
 * soon as it is enqueued. On task error, the task is simply discarded.
 */
public class PassThroughTaskQueue implements TaskQueue {

    private static final Logger log = LoggerFactory.getLogger(PassThroughTaskQueue.class);

    private TaskWorker worker;

    public PassThroughTaskQueue(TaskWorker worker) {
        this.worker = worker;
    }

    @Override
    public void enqueue(TaskDescriptor taskDescriptor) {
        // Immediately notify the worker that the task has arrived.
        try {
            worker.executeTask(taskDescriptor);
        }
        catch (TaskExecutionException e) {
            log.error("An error occurred running a task.", e);
        }
    }

}
