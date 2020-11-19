/*
 * Copyright (c) 2020 Red Hat, Inc.
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
package org.candlepin.subscriptions.util;

import org.candlepin.subscriptions.db.RhsmSubscriptionsDataSourceConfiguration;
import org.candlepin.subscriptions.spring.JobRunner;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for the "liquibase-only" profile.
 *
 * This profile can be used during development to run the liquibase changes and then simply exit.
 */
@Configuration
@Profile("liquibase-only")
@Import(RhsmSubscriptionsDataSourceConfiguration.class)
public class LiquibaseUpdateOnlyConfiguration {
    @Bean
    JobRunner jobRunner(ApplicationContext context) {
        return new JobRunner(new LiquibaseUpdateOnly(), context);
    }

    /**
     * No-op job.
     */
    public static class LiquibaseUpdateOnly implements Runnable {
        @Override
        public void run() {
            /* Intentionally left blank */
        }
    }
}
