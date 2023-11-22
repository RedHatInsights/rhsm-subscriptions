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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.OffsetDateTime;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMeteringController;
import org.candlepin.subscriptions.metering.task.MetricsTask;
import org.candlepin.subscriptions.task.Task;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskType;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrometheusMeteringTaskFactoryTest {

  @Mock private PrometheusMeteringController controller;

  private PrometheusMeteringTaskFactory factory;

  @BeforeEach
  void before() {
    this.factory = new PrometheusMeteringTaskFactory(controller);
  }

  @Test
  void testUnsupportedTask() {
    TaskDescriptor descriptor =
        TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, "a-group", null).build();
    assertThrows(IllegalArgumentException.class, () -> factory.build(descriptor));
  }

  @Test
  void testOpenshiftMetricsTaskCreation() {
    ApplicationClock clock = new TestClockConfiguration().adjustableClock();
    OffsetDateTime end = clock.now();
    OffsetDateTime start = end.minusDays(1);

    Task task =
        factory.build(
            TaskDescriptor.builder(TaskType.METRICS_COLLECTION, "a-group", null)
                .setSingleValuedArg("orgId", "12234")
                .setSingleValuedArg("productTag", "OpenShift")
                .setSingleValuedArg("metric", "Cores")
                .setSingleValuedArg("start", start.toString())
                .setSingleValuedArg("end", end.toString())
                .build());
    assertNotNull(task);
    assertTrue(task instanceof MetricsTask);

    task.execute();
    verify(controller).collectMetrics("OpenShift", MetricIdUtils.getCores(), "12234", start, end);
  }

  @Test
  void testOpenshiftMetricsTaskMissingOrgId() {
    TaskDescriptor descriptor =
        TaskDescriptor.builder(TaskType.METRICS_COLLECTION, "a-group", null)
            .setSingleValuedArg("start", "2018-03-20T09:12:28Z")
            .setSingleValuedArg("end", "2018-03-20T09:12:28Z")
            .setSingleValuedArg("productTag", "OpenShift")
            .setSingleValuedArg("metric", "Cores")
            .setSingleValuedArg("step", "1h")
            .build();
    Throwable e = assertThrows(IllegalArgumentException.class, () -> factory.build(descriptor));
    assertEquals("Could not build task. Missing task argument: orgId", e.getMessage());
  }

  @Test
  void testOpenshiftMetricsTaskInvalidStartDateFormat() {
    TaskDescriptor descriptor =
        TaskDescriptor.builder(TaskType.METRICS_COLLECTION, "a-group", null)
            .setSingleValuedArg("orgId", "1234")
            .setSingleValuedArg("productTag", "OpenShift")
            .setSingleValuedArg("metric", "Cores")
            .setSingleValuedArg("start", "2018-03-20")
            .setSingleValuedArg("end", "2018-03-20T09:12:28Z")
            .setSingleValuedArg("step", "1h")
            .build();

    Throwable e = assertThrows(IllegalArgumentException.class, () -> factory.build(descriptor));
    assertEquals("Unable to parse date arg 'start'. Invalid format.", e.getMessage());
  }

  @Test
  void testOpenshiftMetricsTaskInvalidEndDateFormat() {
    TaskDescriptor descriptor =
        TaskDescriptor.builder(TaskType.METRICS_COLLECTION, "a-group", null)
            .setSingleValuedArg("orgId", "1234")
            .setSingleValuedArg("productTag", "OpenShift")
            .setSingleValuedArg("metric", "Cores")
            .setSingleValuedArg("start", "2018-03-20T09:12:28Z")
            .setSingleValuedArg("end", "2018-03-20T09")
            .setSingleValuedArg("step", "1h")
            .build();
    Throwable e = assertThrows(IllegalArgumentException.class, () -> factory.build(descriptor));
    assertEquals("Unable to parse date arg 'end'. Invalid format.", e.getMessage());
  }
}
