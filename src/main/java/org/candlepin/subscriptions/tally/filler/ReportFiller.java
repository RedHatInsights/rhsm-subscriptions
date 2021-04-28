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
package org.candlepin.subscriptions.tally.filler;

import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.SnapshotTimeAdjuster;
import org.candlepin.subscriptions.utilization.api.model.TallyReport;
import org.candlepin.subscriptions.utilization.api.model.TallySnapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.annotation.Timed;

import java.time.OffsetDateTime;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Given a granularity, date range and existing snaps for the period, fills out any gaps in a TallyReport.
 *
 */
public class ReportFiller {
    private static final Logger log = LoggerFactory.getLogger(ReportFiller.class);

    private final SnapshotTimeAdjuster timeAdjuster;
    private final ApplicationClock clock;

    public ReportFiller(SnapshotTimeAdjuster timeAdjuster, ApplicationClock applicationClock) {
        this.timeAdjuster = timeAdjuster;
        this.clock = applicationClock;
    }

    @Timed("rhsm-subscriptions.tally.fillReport")
    public void fillGaps(TallyReport report, OffsetDateTime start, OffsetDateTime end,
        boolean useRunningTotalFormat) {
        TemporalAmount offset = timeAdjuster.getSnapshotOffset();

        OffsetDateTime firstDate = timeAdjuster.adjustToPeriodStart(start);
        OffsetDateTime lastDate = timeAdjuster.adjustToPeriodEnd(end);

        List<TallySnapshot> existingSnaps = report.getData();
        if (existingSnaps == null || existingSnaps.isEmpty()) {
            report.setData(fillWithRange(firstDate, lastDate, offset, null,
                useRunningTotalFormat));
        }
        else {
            report.setData(fillAndFilterSnapshots(offset, firstDate, lastDate, existingSnaps,
                useRunningTotalFormat));
        }
    }

    @SuppressWarnings("squid:S2583")
    private List<TallySnapshot> fillAndFilterSnapshots(TemporalAmount offset, OffsetDateTime firstDate,
        OffsetDateTime lastDate, List<TallySnapshot> existingSnaps, boolean useRunningTotalFormat) {

        List<TallySnapshot> result = new ArrayList<>();
        OffsetDateTime nextDate = firstDate;
        TallySnapshot lastSnap = null;
        OffsetDateTime lastSnapDate = null;
        Optional<TallySnapshot> pending = Optional.empty();
        Optional<OffsetDateTime> pendingSnapDate = Optional.empty();

        for (TallySnapshot snapshot : existingSnaps) {
            OffsetDateTime snapDate = snapshot.getDate();

            // Should never happen, but if the Filler is given a snapshot without a date
            // it can't be slotted into the date range. Warn, and skip the snapshot.
            if (snapDate == null) {
                // NOTE: Sonarcloud notes snapDate == null as always resulting to false. This
                // is incorrect so the warning has been suppressed (squid:S2583).
                log.warn("Encountered snapshot without date set. Skipping.");
                continue;
            }

            lastSnapDate = timeAdjuster.adjustToPeriodStart(snapDate);

            if (pending.isPresent() && lastSnapDate.isAfter(pendingSnapDate.get())) {
                result.add(pending.get());
                lastSnap = pending.get();
                pending = Optional.empty();
                pendingSnapDate = Optional.empty();
            }

            // Fill report up until the next snapshot, then add the snapshot to the report list.
            result.addAll(fillWithRange(nextDate, lastSnapDate.minus(offset), offset, lastSnap,
                useRunningTotalFormat));
            if (!pending.isPresent() || snapshotIsLarger(pending.get(), snapshot)) {
                pending = Optional.of(snapshot);
                pendingSnapDate = Optional.of(lastSnapDate);
            }
            nextDate = lastSnapDate.plus(offset);
        }
        if (pending.isPresent()) {
            result.add(pending.get());
            lastSnap = pending.get();
        }

        // If no snaps contain dates, just use the start of the range. Otherwise,
        // fill from the date of the last snapshot found, to the end of the range.
        if (lastSnapDate == null) {
            result.addAll(fillWithRange(firstDate, lastDate, offset, null, useRunningTotalFormat));
        }
        else if (lastSnapDate.isBefore(lastDate)) {
            result.addAll(fillWithRange(lastSnapDate.plus(offset), lastDate, offset, lastSnap,
                useRunningTotalFormat));
        }
        return result;
    }

    private boolean snapshotIsLarger(TallySnapshot oldSnap, TallySnapshot newSnap) {
        return newSnap.getInstanceCount() > oldSnap.getInstanceCount() ||
            newSnap.getCores() > oldSnap.getCores() ||
            newSnap.getSockets() > oldSnap.getSockets();
    }

    private TallySnapshot createDefaultSnapshot(OffsetDateTime snapshotDate, TallySnapshot previous,
        boolean useRunningTotalFormat) {
        if (snapshotDate.isBefore(clock.now()) && useRunningTotalFormat && previous != null) {
            return new TallySnapshot()
                .date(snapshotDate)
                .cores(previous.getCores())
                .sockets(previous.getSockets())
                .instanceCount(previous.getInstanceCount())
                .physicalSockets(previous.getPhysicalSockets())
                .physicalCores(previous.getPhysicalCores())
                .physicalInstanceCount(previous.getPhysicalInstanceCount())
                .hypervisorSockets(previous.getHypervisorSockets())
                .hypervisorCores(previous.getHypervisorCores())
                .hypervisorInstanceCount(previous.getHypervisorInstanceCount())
                .cloudInstanceCount(previous.getCloudInstanceCount())
                .cloudSockets(previous.getCloudSockets())
                .cloudCores(previous.getCloudCores())
                .coreHours(previous.getCoreHours())
                .hasData(true); // has_data = true means that the frontend should show the value in a tooltip
        }
        Integer defaultValueInteger;
        Double defaultValue;
        if (snapshotDate.isBefore(clock.now())) {
            defaultValueInteger = 0;
            defaultValue = 0.0;
        }
        else {
            defaultValueInteger = null;
            defaultValue = null;
        }
        return new TallySnapshot()
            .date(snapshotDate)
            .cores(defaultValueInteger)
            .sockets(defaultValueInteger)
            .instanceCount(defaultValueInteger)
            .physicalSockets(defaultValueInteger)
            .physicalCores(defaultValueInteger)
            .physicalInstanceCount(defaultValueInteger)
            .hypervisorSockets(defaultValueInteger)
            .hypervisorCores(defaultValueInteger)
            .hypervisorInstanceCount(defaultValueInteger)
            .cloudInstanceCount(defaultValueInteger)
            .cloudSockets(defaultValueInteger)
            .cloudCores(defaultValueInteger)
            .coreHours(defaultValue)
            .hasData(false);
    }

    private List<TallySnapshot> fillWithRange(OffsetDateTime start, OffsetDateTime end, TemporalAmount offset,
        TallySnapshot snapshot, boolean useRunningTotalFormat) {
        List<TallySnapshot> result = new ArrayList<>();
        OffsetDateTime next = timeAdjuster.adjustToPeriodStart(OffsetDateTime.from(start));
        while (next.isBefore(end) || next.isEqual(end)) {
            result.add(createDefaultSnapshot(next, snapshot, useRunningTotalFormat));
            next = timeAdjuster.adjustToPeriodStart(next.plus(offset));
        }
        return result;
    }

}
