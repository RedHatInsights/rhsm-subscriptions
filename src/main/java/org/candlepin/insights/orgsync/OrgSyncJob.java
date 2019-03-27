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

import org.candlepin.insights.task.TaskManager;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.io.IOException;
import java.util.List;

/**
 * A job to sync orgs from Pinhead to RHSM Conduit.
 */
public class OrgSyncJob extends QuartzJobBean {
    private static final Logger log = LoggerFactory.getLogger(OrgSyncJob.class);

    private OrgListStrategy orgListStrategy;

    private TaskManager tasks;

    @Autowired
    public OrgSyncJob(OrgListStrategy orgListStrategy, TaskManager tasks) {
        this.orgListStrategy = orgListStrategy;
        this.tasks = tasks;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        List<String> orgsToSync;
        try {
            orgsToSync = orgListStrategy.getOrgsToSync();
        }
        catch (IOException e) {
            throw new JobExecutionException("Failed to get the list of orgs to update.", e);
        }

        log.info("Starting inventory update for orgs: {}", orgsToSync);
        orgsToSync.forEach(org -> {
            try {
                tasks.updateOrgInventory(org);
            }
            catch (Exception e) {
                log.error("Could not update inventory for org: {}", org, e);
            }
        });
        log.info("Inventory update complete.");
    }
}
