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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusAccountSource;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.TaskType;
import org.candlepin.subscriptions.task.queue.TaskQueue;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrometheusMetricsTaskManagerTest {

  private static final String TASK_TOPIC = "metrics-tasks-topic";
  private static final String TEST_PROFILE_ID = "OpenShift-dedicated-metrics";

  @Mock private TaskQueue queue;

  @Mock private TaskQueueProperties queueProperties;

  @Mock private PrometheusAccountSource accountSource;

  private PrometheusMetricsTaskManager manager;

  @BeforeEach
  void setupTest() {
    when(queueProperties.getTopic()).thenReturn(TASK_TOPIC);
    ApplicationClock clock = new TestClockConfiguration().adjustableClock();
    manager =
        new PrometheusMetricsTaskManager(
            queue, queueProperties, accountSource, clock, new ApplicationProperties());
  }

  @Test
  void updateForOrgId() throws Exception {
    String orgId = "org123";
    OffsetDateTime end = OffsetDateTime.now();
    OffsetDateTime start = end.minusDays(1);

    TaskDescriptor expectedTask =
        TaskDescriptor.builder(TaskType.METRICS_COLLECTION, TASK_TOPIC, null)
            .setSingleValuedArg("orgId", orgId)
            .setSingleValuedArg("productTag", TEST_PROFILE_ID)
            .setSingleValuedArg("metric", "Cores")
            .setSingleValuedArg("start", start.toString())
            .setSingleValuedArg("end", end.toString())
            .build();
    manager.updateMetricsForOrgId(orgId, TEST_PROFILE_ID, start, end);
    verify(queue).enqueue(expectedTask);
  }
}
