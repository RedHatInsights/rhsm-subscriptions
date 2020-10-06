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
package org.candlepin.subscriptions.orgsync;

import org.candlepin.subscriptions.spring.JobCompleteEvent;
import org.candlepin.subscriptions.task.TaskManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.scheduling.SchedulingException;
import org.springframework.stereotype.Component;

/**
 * A job to sync orgs from RHSM API to RHSM Conduit.
 */
@Component
public class OrgSyncJob implements Runnable, ApplicationEventPublisherAware {
    private static final Logger log = LoggerFactory.getLogger(OrgSyncJob.class);
    private TaskManager tasks;
    private ApplicationEventPublisher publisher;

    @Autowired
    public OrgSyncJob(TaskManager tasks) {
        this.tasks = tasks;
    }

    @Override
    public void run() {
        try {
            log.info("Firing OrgSync job");
            tasks.syncFullOrgList();
            publisher.publishEvent(new JobCompleteEvent(this));
        }
        catch (Exception e) {
            throw new SchedulingException("Failed to sync org list.", e);
        }
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.publisher = applicationEventPublisher;
    }
}
