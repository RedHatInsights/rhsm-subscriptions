/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.insights.orgsync;

import org.candlepin.insights.ApplicationProperties;
import org.candlepin.insights.orgsync.db.DatabaseOrgList;
import org.candlepin.insights.orgsync.db.OrgConfigRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Instant;
import java.util.Arrays;

/**
 * Configuration class for beans related to the OrgSyncJob.  Includes several conditional beans which are
 * loaded based on a value set in the application properties.  Picked up via the @ComponentScan that's
 * implicit in @SpringBootApplication on the {@link org.candlepin.insights.BootApplication} class.
 */
@EnableConfigurationProperties(OrgSyncProperties.class)
@Configuration
@PropertySource("classpath:/rhsm-conduit.properties")
public class OrgSyncConfiguration implements SchedulingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(OrgSyncConfiguration.class);

    @Autowired
    private OrgSyncJob orgSyncJob;

    @Autowired
    private OrgSyncProperties orgSyncProperties;

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private Environment environment;

    @Bean
    public DatabaseOrgList databaseOrgListStrategy(OrgConfigRepository repo) {
        return new DatabaseOrgList(repo);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        TaskScheduler scheduler = poolScheduler();
        // In dev mode, we want to run this job with an internal cron trigger.  In production, the cron
        // sceduling is managed by openshift.
        if (applicationProperties.isDevMode()) {
            String cronExpression = orgSyncProperties.getSchedule();
            scheduler.schedule(orgSyncJob, new CronTrigger(cronExpression));
            log.info("OrgSync job scheduled to run at {}", cronExpression);
        }
        else if (Arrays.binarySearch(environment.getActiveProfiles(), "orgsync") >= 0) {
            scheduler.schedule(orgSyncJob, Instant.now());
            log.info("OrgSync job scheduled to run now");
        }
        else {
            // If we're not in dev mode and not running with the "orgsync" profile, do nothing.
            log.info("OrgSync job is not enabled");
        }
        taskRegistrar.setScheduler(poolScheduler());
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
