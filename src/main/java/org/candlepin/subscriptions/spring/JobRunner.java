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
package org.candlepin.subscriptions.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

/**
 * Responsible for running a single job and then exiting.
 *
 * To use, create a JobRunner bean with the desired Runnable.
 */
public class JobRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    private final Runnable job;
    private final ApplicationContext applicationContext;

    public JobRunner(Runnable job, ApplicationContext applicationContext) {
        this.job = job;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) throws Exception {
        boolean success = false;
        try {
            log.info("Running {} and then exiting.", job.getClass().getSimpleName());
            job.run();
            success = true;
        }
        finally {
            log.info("{} job complete! Exiting.", job.getClass().getSimpleName());
            boolean finalSuccess = success;
            SpringApplication.exit(applicationContext, () -> finalSuccess ? 0 : 1);
        }
    }
}
