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

import java.time.OffsetDateTime;

/**
 * Captures hourly metrics between a given timeframe for a given account
 */
public class CaptureMetricsSnapshotTask implements Task {

    private final String accountNumber;
    private final TallySnapshotController snapshotController;
    private final OffsetDateTime startDateTime;
    private final OffsetDateTime endDateTime;

    public CaptureMetricsSnapshotTask(TallySnapshotController snapshotController, String accountNumber,
        OffsetDateTime startDateTime, OffsetDateTime endDateTime) {
        this.snapshotController = snapshotController;
        this.accountNumber = accountNumber;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;

    }

    @Override
    public void execute() {
        if (startDateTime.isAfter(endDateTime)) {
            throw new IllegalArgumentException(
                "Cannot produce hourly snapshot for account {}.  Invalid date range provided.");
        }
        snapshotController.produceHourlySnapshotsForAccount(accountNumber, startDateTime, endDateTime);
    }
}
