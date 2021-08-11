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
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.files.TagProfile;
import org.candlepin.subscriptions.metering.service.prometheus.MetricProperties;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeteringJobTest {

  @Mock private PrometheusMetricsTaskManager tasks;
  @Mock private TagProfile tagProfile;

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

    clock = new FixedClockConfiguration().fixedClock();
    job = new MeteringJob(tasks, clock, tagProfile, metricProps, appProps);

    when(tagProfile.getTagsWithPrometheusEnabledLookup()).thenReturn(Set.of("OpenShift-metrics"));
  }

  @Test
  void testRunJob() {
    Duration latency = appProps.getPrometheusLatencyDuration();
    int range = metricProps.getRangeInMinutes();

    // NOW: 2019-05-24T12:35Z
    // Metric Period: 2019-05-24T03:00Z -> 2019-05-24T06:00Z
    OffsetDateTime expStartDate = clock.startOfHour(clock.now().minus(latency).minusMinutes(range));
    OffsetDateTime expEndDate =
        clock.endOfHour(
            expStartDate.plusMinutes(range).truncatedTo(ChronoUnit.HOURS).minusMinutes(1));
    job.run();

    verify(tasks).updateMetricsForAllAccounts("OpenShift-metrics", expStartDate, expEndDate);
  }
}
