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
package org.candlepin.subscriptions.jobs;

import org.candlepin.subscriptions.db.PostgresTlsDataSourceProperties;
import org.candlepin.subscriptions.db.PostgresTlsHikariDataSourceFactoryBean;

import org.quartz.JobDetail;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.quartz.QuartzDataSource;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.validation.annotation.Validated;

import java.util.Properties;

/**
 * A class to hold all job related configuration.
 */
@EnableConfigurationProperties(JobProperties.class)
@Configuration
@Profile("scheduler")
@PropertySource("classpath:/rhsm-subscriptions.properties")
public class JobsConfiguration {

    @Bean
    @Validated
    @ConfigurationProperties(prefix = "rhsm-subscriptions.quartz.datasource")
    public PostgresTlsDataSourceProperties quartzDataSourceProperties() {
        return new PostgresTlsDataSourceProperties();
    }

    @Bean(name = "quartz-ds")
    @QuartzDataSource
    public PostgresTlsHikariDataSourceFactoryBean quartzDataSource(
        @Qualifier("quartzDataSourceProperties") PostgresTlsDataSourceProperties dataSourceProperties) {
        PostgresTlsHikariDataSourceFactoryBean factory = new PostgresTlsHikariDataSourceFactoryBean();
        factory.setTlsDataSourceProperties(dataSourceProperties);
        return factory;
    }

    @Bean
    public SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer(
        @Qualifier("quartzDataSourceProperties") DataSourceProperties properties) {
        String driverDelegate = "org.quartz.impl.jdbcjobstore.StdJDBCDelegate";
        if (properties.getPlatform().startsWith("postgres")) {
            driverDelegate = "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate";
        }

        final String finalDriverDelegate = driverDelegate;
        return schedulerFactoryBean -> {
            Properties props = new Properties();
            props.put("org.quartz.jobStore.driverDelegateClass", finalDriverDelegate);
            schedulerFactoryBean.setQuartzProperties(props);
        };
    }

    @Bean
    public JobDetailFactoryBean orgSyncJobDetail() {
        JobDetailFactoryBean jobDetail = new JobDetailFactoryBean();
        jobDetail.setDurability(true);
        jobDetail.setName("CaptureSnapshotsJob");
        jobDetail.setJobClass(CaptureSnapshotsJob.class);
        return jobDetail;
    }

    @Bean
    public JobDetailFactoryBean purgeJobDetail() {
        JobDetailFactoryBean jobDetail = new JobDetailFactoryBean();
        jobDetail.setDurability(true);
        jobDetail.setName("PurgeSnapshotsJob");
        jobDetail.setJobClass(PurgeSnapshotsJob.class);
        return jobDetail;
    }

    @Bean
    public CronTriggerFactoryBean orgSyncTrigger(JobDetail orgSyncJobDetail, JobProperties jobProperties) {
        CronTriggerFactoryBean trigger = new CronTriggerFactoryBean();
        trigger.setJobDetail(orgSyncJobDetail);
        trigger.setCronExpression(jobProperties.getCaptureSnapshotSchedule());
        return trigger;
    }

    @Bean
    public CronTriggerFactoryBean purgeTrigger(JobDetail purgeJobDetail, JobProperties jobProperties) {
        CronTriggerFactoryBean trigger = new CronTriggerFactoryBean();
        trigger.setJobDetail(purgeJobDetail);
        trigger.setCronExpression(jobProperties.getPurgeSnapshotSchedule());
        return trigger;
    }
}
