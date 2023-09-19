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
package org.candlepin.subscriptions.metering.job;

import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.metering.service.prometheus.MetricProperties;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.support.RetryTemplate;

@ExtendWith(MockitoExtension.class)
class MeteringJobTest {

  @Mock private PrometheusMetricsTaskManager tasks;
  @Mock private RetryTemplate retryTemplate;

  private ApplicationClock clock;
  private MetricProperties metricProps;
  private ApplicationProperties appProps;
  private MeteringJob job;

  @BeforeEach
  void setupTests() {
    metricProps = new MetricProperties();
    metricProps.setRangeInMinutes(180); // 3h

    appProps = new ApplicationProperties();
    appProps.setPrometheusLatencyDuration(Duration.ofHours(6L));

    clock = new TestClockConfiguration().adjustableClock();
    job = new MeteringJob(tasks, metricProps, retryTemplate);
  }

  @Test
  void testRunJob() {
    Duration latency = appProps.getPrometheusLatencyDuration();
    int range = metricProps.getRangeInMinutes();

    // NOW: 2019-05-24T12:35Z
    // Metric Period: 2019-05-24T03:00Z -> 2019-05-24T06:00Z
    OffsetDateTime expStartDate = clock.startOfHour(clock.now().minus(latency).minusMinutes(range));

    job.run();

    verify(tasks).updateMetricsForAllAccounts("OpenShift-metrics", range, retryTemplate);
  }
}
