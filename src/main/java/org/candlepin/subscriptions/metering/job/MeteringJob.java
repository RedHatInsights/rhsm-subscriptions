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

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.exception.JobFailureException;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMetricsProperties;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** A cron job that sends a task message to capture metrics from prometheus for metering. */
public class MeteringJob implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(MeteringJob.class);

  private PrometheusMetricsTaskManager tasks;
  private ApplicationClock clock;
  private ApplicationProperties appProps;
  private PrometheusMetricsProperties prometheusMetricsProperties;

  public MeteringJob(
      PrometheusMetricsTaskManager tasks,
      ApplicationClock clock,
      PrometheusMetricsProperties prometheusMetricsProperties,
      ApplicationProperties appProps) {
    this.tasks = tasks;
    this.clock = clock;
    this.prometheusMetricsProperties = prometheusMetricsProperties;
    this.appProps = appProps;
  }

  @Override
  @Scheduled(cron = "${rhsm-subscriptions.jobs.metering-schedule}")
  public void run() {
    Duration latency = appProps.getPrometheusLatencyDuration();
    for (String productProfileId : prometheusMetricsProperties.getMetricsEnabledProductProfiles()) {
      int range = prometheusMetricsProperties.getRangeInMinutesForProductProfile(productProfileId);
      OffsetDateTime startDate = clock.startOfHour(clock.now().minus(latency).minusMinutes(range));
      // Minus 1 minute to ensure that we use the last hour's maximum time. If the end
      // time
      // is
      // 6:00:00,
      // taking the last of that hour would give the range an extra hour (6:59:59.999999)
      // which is
      // not what we want. We subtract to break the even boundary before finding the last
      // minute.
      // We need to do this because our queries are date inclusive (greater/less than OR
      // equal to).
      OffsetDateTime endDate =
          clock.endOfHour(
              startDate.plusMinutes(range).truncatedTo(ChronoUnit.HOURS).minusMinutes(1));

      log.info(
          "Queuing {} metric updates for range: {} -> {}", productProfileId, startDate, endDate);
      try {
        tasks.updateMetricsForAllAccounts(productProfileId, startDate, endDate);
      } catch (Exception e) {
        throw new JobFailureException("Unable to run MeteringJob.", e);
      }
    }
  }
}
