/*
 * Copyright (c) 2020 Red Hat, Inc.
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
package org.candlepin.subscriptions.metering.tasks;

import org.candlepin.subscriptions.metering.MeteringController;
import org.candlepin.subscriptions.task.Task;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskFactory;

import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;


/**
 * Creates metering related Tasks.
 */
public class MeteringTaskFactory implements TaskFactory {

    private final MeteringController controller;

    public MeteringTaskFactory(MeteringController controller) {
        this.controller = controller;
    }

    @Override
    public Task build(TaskDescriptor taskDescriptor) {
        switch (taskDescriptor.getTaskType()) {
            case OPENSHIFT_METRICS_COLLECTION:
                // TODO: Arg validation should be moved to Task.execute() at some point.
                String account = validateString(taskDescriptor, "account");

                OffsetDateTime start = validateDate(taskDescriptor, "start");
                OffsetDateTime end = validateDate(taskDescriptor, "end");

                return new OpenshiftMetricsTask(controller, account, start, end);
            default:
                throw new IllegalArgumentException("Could not build task. Unknown task type: " +
                    taskDescriptor.getTaskType());
        }
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
        }
        catch (Exception e) {
            throw new IllegalArgumentException(
                String.format("Unable to parse date arg '%s'. Invalid format.", arg)
            );
        }

    }
}
