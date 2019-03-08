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

import org.candlepin.insights.task.TaskDescriptor;
import org.candlepin.insights.task.TaskType;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the task data that is stored in Kafka. This is pretty much a mirror
 * of TaskDescriptor but serves as the DTO for sending through Kafka.
 */
public class TaskMessage {

    private String groupId;
    private TaskType type;
    private Map<String, String> args;

    public TaskMessage() {
    }

    public TaskMessage(TaskDescriptor descriptor) {
        groupId = descriptor.getGroupId();
        type = descriptor.getTaskType();
        args = new HashMap<>(descriptor.getTaskArgs());
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public TaskType getType() {
        return type;
    }

    public void setType(TaskType type) {
        this.type = type;
    }

    public Map<String, String> getArgs() {
        return args;
    }

    public void setArgs(Map<String, String> args) {
        this.args = args;
    }

    public TaskDescriptor toDescriptor() {
        return TaskDescriptor.builder(this.type, this.groupId).setArgs(this.args).build();
    }

    @Override
    public String toString() {
        StringBuilder output = new StringBuilder();
        output.append(String.format("%s[ ", TaskMessage.class.getSimpleName()));
        output.append(String.format("Group ID: %s, ", groupId));
        output.append(String.format("Type: %s, ", type));
        output.append("Args: [");
        args.entrySet().forEach((e) -> output.append(String.format("%s: %s ", e.getKey(), e.getValue())));
        output.append("]]");
        return output.toString();
    }
}
