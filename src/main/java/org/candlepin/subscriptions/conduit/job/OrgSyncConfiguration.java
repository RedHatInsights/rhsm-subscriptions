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
package org.candlepin.subscriptions.conduit.job;

import org.candlepin.subscriptions.conduit.ConduitTaskQueueConfiguration;
import org.candlepin.subscriptions.spring.JobRunner;
import org.candlepin.subscriptions.task.queue.TaskProducerConfiguration;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Configuration class for beans related to the OrgSyncJob.  Includes several conditional beans which are
 * loaded based on a value set in the application properties.
 */
@Configuration
@Profile("orgsync")
@Import({ConduitTaskQueueConfiguration.class, TaskProducerConfiguration.class})
@ComponentScan(basePackages = "org.candlepin.subscriptions.conduit.job")
public class OrgSyncConfiguration {
    @Bean
    OrgSyncJob job(OrgSyncTaskManager taskManager) {
        return new OrgSyncJob(taskManager);
    }

    @Bean
    JobRunner jobRunner(OrgSyncJob job, ApplicationContext applicationContext) {
        return new JobRunner(job, applicationContext);
    }
}
