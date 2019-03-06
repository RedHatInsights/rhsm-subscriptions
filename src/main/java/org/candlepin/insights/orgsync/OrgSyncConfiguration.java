/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.insights.orgsync;

import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;

/**
 * Configuration class for beans related to the OrgSyncJob.  Includes several conditional beans which are
 * loaded based on a value set in the application properties.  Picked up via the @ComponentScan that's
 * implicit in @SpringBootApplication on the {@link org.candlepin.insights.BootApplication} class.
 */
@EnableConfigurationProperties(OrgSyncProperties.class)
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
    @Conditional(NoStrategyCondition.class)
    public OrgListStrategy noOrgListStrategy() {
        throw new IllegalStateException(
            "Either no value or no valid value is defined for " + ORG_LIST_STRATEGY
        );
    }

    @Bean
    public JobDetailFactoryBean orgSyncJobDetail() {
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
