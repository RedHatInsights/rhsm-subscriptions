/*
 * Copyright (c) 2019 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.jobs;

import org.candlepin.subscriptions.ApplicationProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Instant;

/**
 * A class to hold all job related configuration.
 */
@EnableConfigurationProperties(JobProperties.class)
@Configuration
@PropertySource("classpath:/rhsm-subscriptions.properties")
public class JobsConfiguration implements SchedulingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(JobsConfiguration.class);

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private JobProperties jobProperties;

    @Autowired(required = false)
    private PurgeSnapshotsJob purgeSnapshotsJob;

    @Autowired(required = false)
    private CaptureSnapshotsJob captureSnapshotsJob;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        TaskScheduler scheduler = poolScheduler();
        taskRegistrar.setScheduler(poolScheduler());
        // In dev mode, we want to run this job with an internal cron trigger.  In production, the cron
        // scheduling is managed by openshift.
        if (applicationProperties.isDevMode()) {
            String purgeCronExpression = jobProperties.getPurgeSnapshotSchedule();
            if (purgeSnapshotsJob != null) {
                scheduler.schedule(purgeSnapshotsJob, new CronTrigger(purgeCronExpression));
            }

            String captureCronExpression = jobProperties.getCaptureSnapshotSchedule();
            if (captureSnapshotsJob != null) {
                scheduler.schedule(captureSnapshotsJob, new CronTrigger(captureCronExpression));
            }
            return;
        }

        boolean jobless = true;
        if (purgeSnapshotsJob != null) {
            scheduler.schedule(purgeSnapshotsJob, Instant.now());
            log.info("Purge Snapshots job scheduled to run now");
            jobless = false;
        }

        if (captureSnapshotsJob != null) {
            scheduler.schedule(captureSnapshotsJob, Instant.now());
            log.info("Capture Snapshots job scheduled to run now");
            jobless = false;
        }

        if (jobless) {
            // If we're not in dev mode and not running with any relevant profile, do nothing.
            log.info("No jobs are enabled");
        }
    }

    @Bean
    public TaskScheduler poolScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("ThreadPoolTaskScheduler");
        scheduler.setPoolSize(4);
        scheduler.initialize();
        return scheduler;
    }
}
