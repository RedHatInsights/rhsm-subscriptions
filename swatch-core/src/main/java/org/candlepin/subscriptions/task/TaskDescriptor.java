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
package org.candlepin.subscriptions.task;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import lombok.Getter;
import org.springframework.lang.NonNull;

/**
 * A TaskDescriptor describes a Task that is to be stored in a TaskQueue and is to be eventually
 * executed by a TaskWorker.
 *
 * <p>When describing a Task that is the be queued, a descriptor must at least define the task group
 * that a task belongs to, as well as the TaskType of the Task.
 *
 * <p>A TaskDescriptor requires two key pieces of data; a groupId and a TaskType.
 *
 * <p>A groupId should be specified so that the TaskQueue can use it for task organization within
 * the queue.
 *
 * <p>A TaskType should be specified so that the TaskFactory can use it to build an associated Task
 * object that defines the actual work that is to be done.
 *
 * <p>A descriptor can also specify any task arguments to customize task execution.
 */
public class TaskDescriptor {

  @Getter private final String groupId;
  private final TaskType type;
  @Getter private final String key;
  private final Map<String, List<String>> args;

  private TaskDescriptor(TaskDescriptorBuilder builder) {
    this.groupId = builder.groupId;
    this.type = builder.type;
    this.args = builder.args;
    this.key = builder.key;
  }

  public TaskType getTaskType() {
    return type;
  }

  public Map<String, List<String>> getTaskArgs() {
    return args;
  }

  public List<String> getArg(String key) {
    return this.args.get(key);
  }

  public boolean hasArg(String arg) {
    return this.args.containsKey(arg) && !this.args.get(arg).isEmpty();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("TaskDescriptor[");
    builder.append("groupId: " + groupId);
    builder.append(", taskType: " + type);
    builder.append(", args: [");

    Iterator<Entry<String, List<String>>> iter = args.entrySet().iterator();
    while (iter.hasNext()) {
      Entry<String, List<String>> argEntry = iter.next();
      builder.append(argEntry.getKey() + ": [" + String.join(",", argEntry.getValue()) + "]");

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
    return Objects.equals(groupId, that.groupId)
        && type == that.type
        && Objects.equals(args, that.args);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, type, args);
  }

  public static TaskDescriptorBuilder builder(TaskType type, String taskGroup, String key) {
    return new TaskDescriptorBuilder(type, taskGroup, key);
  }

  /** A builder object for building TaskDescriptor objects. */
  public static class TaskDescriptorBuilder {

    @NonNull private final String groupId;

    @NonNull private final TaskType type;

    @NonNull private final String key;

    private Map<String, List<String>> args;

    private TaskDescriptorBuilder(TaskType type, String groupId, @NonNull String key) {
      this.type = type;
      this.groupId = groupId;
      this.key = key;
      this.args = new HashMap<>();
    }

    public TaskDescriptorBuilder setArg(String name, List<String> values) {
      this.args.put(name, values);
      return this;
    }

    public TaskDescriptorBuilder setSingleValuedArg(String name, String value) {
      this.args.put(name, Arrays.asList(value));
      return this;
    }

    public TaskDescriptorBuilder setArgs(Map<String, List<String>> args) {
      this.args = new HashMap<>(args);
      return this;
    }

    public TaskDescriptor build() {
      return new TaskDescriptor(this);
    }
  }
}
