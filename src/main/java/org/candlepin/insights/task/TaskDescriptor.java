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
package org.candlepin.insights.task;

import org.springframework.lang.NonNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;


/**
 * A TaskDescriptor describes a Task that is to be stored in a TaskQueue and is to be
 * eventually executed by a TaskWorker.
 *
 * When describing a Task that is the be queued, a descriptor must at least define the
 * task group that a task belongs to, as well as the TaskType of the Task.
 *
 * A TaskDescriptor requires two key pieces of data; a groupId and a TaskType.
 *
 * A groupId should be specified so that the TaskQueue can use it for task organization within the queue.
 *
 * A TaskType should be specified so that the TaskFactory can use it to build an associated Task object
 * that defines the actual work that is to be done.
 *
 * A descriptor can also specify any task arguments to customize task execution.
 */
public class TaskDescriptor {

    private final String groupId;
    private final TaskType type;
    private Map<String, String> args;

    private TaskDescriptor(TaskDescriptorBuilder builder) {
        this.groupId = builder.groupId;
        this.type = builder.type;
        this.args = builder.args;
    }

    public String getGroupId() {
        return groupId;
    }

    public TaskType getTaskType() {
        return type;
    }

    public Map<String, String> getTaskArgs() {
        return args;
    }

    public String getArg(String key) {
        return this.args.get(key);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("TaskDescriptor[");
        builder.append("groupId: " + groupId);
        builder.append(", taskType: " + type);
        builder.append(", args: [");

        Iterator<Entry<String, String>> iter = args.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<String, String> argEntry = iter.next();
            builder.append(argEntry.getKey() + ": " + argEntry.getValue());

            if (iter.hasNext()) {
                builder.append(", ");
            }
        }
        builder.append("]");
        builder.append("]");
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof TaskDescriptor)) {
            return false;
        }

        TaskDescriptor that = (TaskDescriptor) o;
        return Objects.equals(groupId, that.groupId) &&
            type == that.type &&
            Objects.equals(args, that.args);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, type, args);
    }

    public static TaskDescriptorBuilder builder(TaskType type, String taskGroup) {
        return new TaskDescriptorBuilder(type, taskGroup);
    }

    /**
     * A builder object for building TaskDescriptor objects.
     */
    public static class TaskDescriptorBuilder {

        @NonNull
        private final String groupId;

        @NonNull
        private final TaskType type;

        private Map<String, String> args;

        private TaskDescriptorBuilder(TaskType type, String groupId) {
            this.type = type;
            this.groupId = groupId;
            this.args = new HashMap<>();
        }

        public TaskDescriptorBuilder setArg(String name, String value) {
            if (value != null) {
                this.args.put(name, value);
            }
            return this;
        }

        public TaskDescriptorBuilder setArgs(Map<String, String> args) {
            this.args = new HashMap<>(args);
            return this;
        }

        public TaskDescriptor build() {
            return new TaskDescriptor(this);
        }

    }
}
