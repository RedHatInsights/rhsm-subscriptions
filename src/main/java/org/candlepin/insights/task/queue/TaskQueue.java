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
package org.candlepin.insights.task.queue;

import org.candlepin.insights.task.TaskDescriptor;

/**
 * A TaskQueue is responsible for storing tasks until they are processed.
 */
public interface TaskQueue {

    /**
     * Enqueues a task that is to be processed by the registered processor.
     *
     * @param taskDescriptor a TaskDescriptor describing the task that is to be processed.
     */
    void enqueue(TaskDescriptor taskDescriptor);

}
