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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.subscriptions.metering.MeteringController;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class MeteringTaskFactoryTest {

    @Mock
    private MeteringController controller;

    private MeteringTaskFactory factory;

    @BeforeEach
    void before() {
        this.factory = new MeteringTaskFactory(controller);
    }

    @Test
    void testUnsupportedTask() {
        assertThrows(IllegalArgumentException.class, () -> {
            factory.build(TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, "a-group").build());
        });
    }

    @Test
    void testOpenshiftMetricsTaskCreation() {
        factory.build(
            TaskDescriptor.builder(TaskType.OPENSHIFT_METRICS_COLLECTION, "a-group")
            .setSingleValuedArg("account", "12234")
            .setSingleValuedArg("start", "2018-03-20T09:12:28Z")
            .setSingleValuedArg("end", "2018-03-20T09:12:28Z")
            .setSingleValuedArg("step", "1h")
            .build()
        );
    }

    @Test
    void testOpenshiftMetricsTaskMissingAccount() {
        Throwable e = assertThrows(IllegalArgumentException.class, () -> {
            factory.build(
                TaskDescriptor.builder(TaskType.OPENSHIFT_METRICS_COLLECTION, "a-group")
                .setSingleValuedArg("start", "2018-03-20T09:12:28Z")
                .setSingleValuedArg("end", "2018-03-20T09:12:28Z")
                .setSingleValuedArg("step", "1h")
                .build()
            );
        });
        assertEquals("Could not build task. Missing task argument: account", e.getMessage());
    }

    @Test
    void testOpenshiftMetricsTaskInvalidStartDateFormat() {
        Throwable e = assertThrows(IllegalArgumentException.class, () -> {
            factory.build(
                TaskDescriptor.builder(TaskType.OPENSHIFT_METRICS_COLLECTION, "a-group")
                .setSingleValuedArg("account", "1234")
                .setSingleValuedArg("start", "2018-03-20")
                .setSingleValuedArg("end", "2018-03-20T09:12:28Z")
                .setSingleValuedArg("step", "1h")
                .build()
            );
        });
        assertEquals("Unable to parse date arg 'start'. Invalid format.", e.getMessage());
    }

    @Test
    void testOpenshiftMetricsTaskInvalidEndDateFormat() {
        Throwable e = assertThrows(IllegalArgumentException.class, () -> {
            factory.build(
                TaskDescriptor.builder(TaskType.OPENSHIFT_METRICS_COLLECTION, "a-group")
                .setSingleValuedArg("account", "1234")
                .setSingleValuedArg("start", "2018-03-20T09:12:28Z")
                .setSingleValuedArg("end", "2018-03-20T09")
                .setSingleValuedArg("step", "1h")
                .build()
            );
        });
        assertEquals("Unable to parse date arg 'end'. Invalid format.", e.getMessage());
    }
}
