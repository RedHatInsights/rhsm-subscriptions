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
package org.candlepin.subscriptions.tally;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.validation.executable.ExecutableValidator;
import java.lang.reflect.Constructor;
import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.tally.tasks.CaptureMetricsSnapshotTask;
import org.candlepin.subscriptions.tally.tasks.UpdateOrgSnapshotsTask;
import org.candlepin.subscriptions.task.Task;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskFactory;
import org.candlepin.subscriptions.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * A class responsible for a TaskDescriptor into actual Task instances. Task instances are build via
 * the build(TaskDescriptor) method. The type of Task that will be built is determined by the
 * descriptor's TaskType property.
 */
@Component
public class TallyTaskFactory implements TaskFactory {
  private static final Logger log = LoggerFactory.getLogger(TallyTaskFactory.class);
  private final TallySnapshotController snapshotController;
  private final ExecutableValidator validator;

  @Autowired
  public TallyTaskFactory(Validator validator, TallySnapshotController snapshotController) {
    this.validator = validator.forExecutables();
    this.snapshotController = snapshotController;
  }

  /**
   * Builds a Task instance based on the specified TaskDescriptor.
   *
   * @param taskDescriptor the task descriptor that is used to customize the Task that is to be
   *     created.
   * @return the Task defined by the descriptor.
   */
  @Override
  public Task build(TaskDescriptor taskDescriptor) {
    if (taskDescriptor.getTaskType() == TaskType.UPDATE_SNAPSHOTS) {
      // We can assume that the task messages will have orgs arg going forward.
      log.debug("Task created for processing orgs");
      return new UpdateOrgSnapshotsTask(snapshotController, taskDescriptor.getArg("orgs"));
    }

    if (taskDescriptor.getTaskType() == TaskType.UPDATE_HOURLY_SNAPSHOTS) {
      validateHourlySnapshotTaskArgs(taskDescriptor);

      String orgId = taskDescriptor.getArg("orgId").get(0);
      String startDateTime = taskDescriptor.getArg("startDateTime").get(0);
      String endDateTime = taskDescriptor.getArg("endDateTime").get(0);

      // CaptureMetricsSnapshotTask is not a Spring managed bean, so we have to invoke the validator
      // ourselves. This code relies on the CaptureMetricSnapshotTask only having one constructor.
      Constructor<?> ctor = CaptureMetricsSnapshotTask.class.getConstructors()[0];
      Object[] args = new Object[] {snapshotController, orgId, startDateTime, endDateTime};
      Set<? extends ConstraintViolation<?>> constraintViolations =
          validator.validateConstructorParameters(ctor, args);

      if (constraintViolations.isEmpty()) {
        return new CaptureMetricsSnapshotTask(
            snapshotController, orgId, startDateTime, endDateTime);
      } else {
        String message =
            constraintViolations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.error("CaptureMetricsSnapshotTask failed validation: {}", message);
        throw new ConstraintViolationException(constraintViolations);
      }
    }

    throw new IllegalArgumentException(
        "Could not build task. Unknown task type: " + taskDescriptor.getTaskType());
  }

  protected void validateHourlySnapshotTaskArgs(TaskDescriptor taskDescriptor) {
    if (CollectionUtils.isEmpty(taskDescriptor.getArg("orgId"))
        || CollectionUtils.isEmpty(taskDescriptor.getArg("startDateTime"))
        || CollectionUtils.isEmpty(taskDescriptor.getArg("endDateTime"))) {
      throw new IllegalArgumentException(
          String.format(
              "Could not build %s task. orgId, startDateTime, endDateTime are all required",
              TaskType.UPDATE_HOURLY_SNAPSHOTS));
    }
  }
}
