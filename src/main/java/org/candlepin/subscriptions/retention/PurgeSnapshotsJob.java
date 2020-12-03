/*
 * Copyright (c) 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.retention;

import org.candlepin.subscriptions.exception.JobFailureException;
import org.candlepin.subscriptions.tally.AccountListSourceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A cron job that purges usage snapshots on a configured schedule.
 */
@Component
public class PurgeSnapshotsJob implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(PurgeSnapshotsJob.class);
    private final TallyRetentionController retentionController;

    @Autowired
    public PurgeSnapshotsJob(TallyRetentionController retentionController) {
        this.retentionController = retentionController;
    }

    @Override
    @Scheduled(cron = "${rhsm-subscriptions.jobs.purge-snapshot-schedule}")
    public void run() {
        log.info("Starting snapshot purge.");
        try {
            retentionController.purgeSnapshots();
            log.info("Snapshot purge complete.");
        }
        catch (AccountListSourceException e) {
            throw new JobFailureException("Could not purge snapshots.", e);
        }
    }
}
