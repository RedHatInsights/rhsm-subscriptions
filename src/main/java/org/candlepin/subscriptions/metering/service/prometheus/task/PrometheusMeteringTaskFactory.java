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
package org.candlepin.subscriptions.metering.service.prometheus.task;

import com.redhat.swatch.configuration.registry.MetricId;
import java.time.OffsetDateTime;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMeteringController;
import org.candlepin.subscriptions.metering.task.MetricsTask;
import org.candlepin.subscriptions.task.Task;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskFactory;
import org.candlepin.subscriptions.task.TaskType;
import org.springframework.util.StringUtils;

/** Creates metering related Tasks. */
public class PrometheusMeteringTaskFactory implements TaskFactory {

  private final PrometheusMeteringController controller;

  public PrometheusMeteringTaskFactory(PrometheusMeteringController controller) {
    this.controller = controller;
  }

  @Override
  public Task build(TaskDescriptor taskDescriptor) {
    if (TaskType.METRICS_COLLECTION.equals(taskDescriptor.getTaskType())) {
      if (taskDescriptor.hasArg("account")) {
        throw new IllegalArgumentException(
            String.format("Task has account rather than orgId: %s", taskDescriptor));
      }
      return new MetricsTask(
          controller,
          validateString(taskDescriptor, "orgId"),
          validateString(taskDescriptor, "productTag"),
          MetricId.fromString(validateString(taskDescriptor, "metric")),
          validateDate(taskDescriptor, "start"),
          validateDate(taskDescriptor, "end"));
    }
    throw new IllegalArgumentException(
        String.format("Could not build task. Unknown task type: %s", taskDescriptor.getTaskType()));
  }

  private String validateString(TaskDescriptor desc, String arg) {
    if (!desc.hasArg(arg)) {
      throw new IllegalArgumentException(
          String.format("Could not build task. Missing task argument: %s", arg));
    }

    if (!StringUtils.hasText(desc.getArg(arg).get(0))) {
      throw new IllegalArgumentException(
          String.format("Could not build task. Task argument %s was empty.", arg));
    }
    return desc.getArg(arg).get(0);
  }

  private OffsetDateTime validateDate(TaskDescriptor desc, String arg) {
    String dateStr = validateString(desc, arg);

    try {
      // Example format: 2018-03-20T09:12:28Z
      return OffsetDateTime.parse(dateStr);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          String.format("Unable to parse date arg '%s'. Invalid format.", arg));
    }
  }
}
