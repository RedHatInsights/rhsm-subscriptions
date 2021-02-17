/*
 * Copyright (c) 2021 Red Hat, Inc.
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

import org.candlepin.subscriptions.exception.JobFailureException;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMetricsProperties;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.springframework.scheduling.annotation.Scheduled;

import java.time.OffsetDateTime;

/**
 * A cron job that sends a task message to capture metrics from prometheus for metering.
 */
public class OpenShiftMeteringJob implements Runnable {

    private PrometheusMetricsTaskManager tasks;
    private ApplicationClock clock;
    private int rangeInMinutes;

    public OpenShiftMeteringJob(PrometheusMetricsTaskManager tasks, ApplicationClock clock,
        PrometheusMetricsProperties metricProperties) {
        this.tasks = tasks;
        this.clock = clock;
        this.rangeInMinutes = metricProperties.getOpenshift().getRangeInMinutes();
    }

    @Override
    @Scheduled(cron = "${rhsm-subscriptions.jobs.metering.openshift-metering-schedule}")
    public void run() {
        OffsetDateTime endDate = clock.now();
        OffsetDateTime startDate = endDate.minusMinutes(rangeInMinutes);

        try {
            tasks.updateOpenshiftMetricsForAllAccounts(startDate, endDate);
        }
        catch (Exception e) {
            throw new JobFailureException("Unable to run MeteringJob.", e);
        }
    }
}
