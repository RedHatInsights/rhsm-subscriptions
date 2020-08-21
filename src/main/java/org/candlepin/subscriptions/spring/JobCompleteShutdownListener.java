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
package org.candlepin.subscriptions.spring;

import org.candlepin.subscriptions.ApplicationProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * This listener shuts down the Boot application after receiving an {@link JobCompleteEvent}.
 * In production, we have a pod running with the "purge-snapshots" and a pod with the "capture-snapshots"
 * profile. We want those pods to start, run the job once, and then shutdown cleanly.  Openshift
 * takes care of all the cron scheduling.  In  development mode, we want scheduling to happen internally
 * and for the pod to keep running.
 */
@Profile({"purge-snapshots", "capture-snapshots"})
@Component
public class JobCompleteShutdownListener implements ApplicationListener<JobCompleteEvent>,
    ApplicationContextAware {

    private ApplicationProperties applicationProperties;
    private ApplicationContext context;

    @Autowired
    public JobCompleteShutdownListener(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Override
    public void onApplicationEvent(JobCompleteEvent event) {
        if (applicationProperties.isDevMode()) {
            return;
        }

        System.exit(SpringApplication.exit(context, () -> 0));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.context = applicationContext;
    }
}
