/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.tally.tasks;

import org.candlepin.subscriptions.tally.TallySnapshotController;
import org.candlepin.subscriptions.task.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;

public class CaptureMetricsSnapshotTask implements Task {
    private static final Logger log = LoggerFactory.getLogger(CaptureMetricsSnapshotTask.class);

    private final String accountNumbers;
    private final TallySnapshotController snapshotController;
    private final OffsetDateTime startTime;
    private final OffsetDateTime endTime;

    public CaptureMetricsSnapshotTask(TallySnapshotController snapshotController,
        String accountNumbers, OffsetDateTime startTime, OffsetDateTime endTime) {
        this.snapshotController = snapshotController;
        this.accountNumbers = accountNumbers;
        this.startTime = startTime;
        this.endTime = endTime;
        
    }

    @Override
    public void execute() {
        snapshotController.produceHourlySnapshotsForAccount(accountNumbers, startTime, endTime);
    }
}
