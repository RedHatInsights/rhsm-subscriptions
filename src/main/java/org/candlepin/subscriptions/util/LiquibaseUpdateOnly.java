/*
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Bean that exits the application as soon as it's started.
 *
 * Intended to be used to get liquibase updates to run without restarting the application.
 * If running locally, you may need to adjust the port to avoid conflict with a running instance.
 */
@Component
@Profile("liquibase-only")
public class LiquibaseUpdateOnly implements CommandLineRunner {

    private static Logger log = LoggerFactory.getLogger(LiquibaseUpdateOnly.class);

    private final ApplicationContext context;

    public LiquibaseUpdateOnly(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(String... args) {
        log.info("Running in liquibase update only mode. Exiting.");
        SpringApplication.exit(context);
    }
}
