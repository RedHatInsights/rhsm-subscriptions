/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
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
