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

import org.candlepin.insights.orgsync.db.DatabaseOrgListStrategy;
import org.candlepin.insights.orgsync.db.OrganizationRepository;

import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;

/**
 * Configuration class for beans related to the OrgSyncJob.  Includes several conditional beans which are
 * loaded based on a value set in the application properties.  Picked up via the @ComponentScan that's
 * implicit in @SpringBootApplication on the {@link org.candlepin.insights.BootApplication} class.
 */
@EnableConfigurationProperties(OrgSyncProperties.class)
@EnableJpaRepositories
@Configuration
@PropertySource("classpath:/rhsm-conduit.properties")
public class OrgSyncConfiguration {
    public static final String ORG_LIST_STRATEGY = "rhsm-conduit.org-sync.strategy";
    private static final Logger log = LoggerFactory.getLogger(OrgSyncProperties.class);

    @Autowired
    private OrgSyncProperties orgSyncProperties;

    @Bean
    @ConditionalOnProperty(value = ORG_LIST_STRATEGY, havingValue = "fileBasedOrgListStrategy")
    @ConfigurationProperties(prefix = "rhsm-conduit.org-sync.file-based-org-list-strategy")
    public FileBasedOrgListStrategyProperties fileBasedOrgListStrategyProperties() {
        return new FileBasedOrgListStrategyProperties();
    }

    @Bean
    @ConditionalOnProperty(value = ORG_LIST_STRATEGY, havingValue = "fileBasedOrgListStrategy")
    public OrgListStrategy fileBasedOrgListStrategy(FileBasedOrgListStrategyProperties conf) {
        log.info("Using {} strategy", orgSyncProperties.getStrategy());
        return new FileBasedOrgListStrategy(conf);
    }

    @Bean
    @ConditionalOnProperty(value = ORG_LIST_STRATEGY, havingValue = "databaseOrgListStrategy")
    public DatabaseOrgListStrategy databaseOrgListStrategy(OrganizationRepository repo) {
        return new DatabaseOrgListStrategy(repo);
    }

    @Bean
    public JobDetailFactoryBean orgSyncJobDetail(@Autowired(required = false) OrgListStrategy strategy) {
        if (strategy == null) {
            throw new IllegalStateException(
                "Either no value or no valid value is defined for " + ORG_LIST_STRATEGY
            );
        }
        JobDetailFactoryBean jobDetail = new JobDetailFactoryBean();
        jobDetail.setDurability(true);
        jobDetail.setName("OrgSyncJob");
        jobDetail.setJobClass(OrgSyncJob.class);
        return jobDetail;
    }

    @Bean
    public CronTriggerFactoryBean trigger(JobDetail job) {
        CronTriggerFactoryBean trigger = new CronTriggerFactoryBean();
        trigger.setJobDetail(job);
        trigger.setCronExpression(orgSyncProperties.getSchedule());
        return trigger;
    }
}
