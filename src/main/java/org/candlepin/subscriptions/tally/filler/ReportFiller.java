/*
 * Copyright Red Hat, Inc.
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

import io.micrometer.core.annotation.Timed;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.candlepin.subscriptions.util.SnapshotTimeAdjuster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Given a granularity, date range and existing snaps for the period, fills out any gaps in a List
 * of T.
 *
 * <p>An instance of ReportFillerAdapter is used to compare, create default items, and extract
 * dates.
 */
public class ReportFiller<T> {
  private static final Logger log = LoggerFactory.getLogger(ReportFiller.class);

  private final SnapshotTimeAdjuster timeAdjuster;
  private final ReportFillerAdapter<T> reportFillerAdapter;

  public ReportFiller(
      SnapshotTimeAdjuster timeAdjuster, ReportFillerAdapter<T> reportFillerAdapter) {
    this.timeAdjuster = timeAdjuster;
    this.reportFillerAdapter = reportFillerAdapter;
  }

  @Timed("rhsm-subscriptions.tally.fillReport")
  public List<T> fillGaps(
      List<T> existingSnaps,
      OffsetDateTime start,
      OffsetDateTime end,
      boolean useRunningTotalFormat) {
    TemporalAmount offset = timeAdjuster.getSnapshotOffset();

    OffsetDateTime firstDate = timeAdjuster.adjustToPeriodStart(start);
    OffsetDateTime lastDate = timeAdjuster.adjustToPeriodEnd(end);

    if (existingSnaps == null || existingSnaps.isEmpty()) {
      return fillWithRange(firstDate, lastDate, offset, null, useRunningTotalFormat);
    } else {
      return fillAndFilterSnapshots(
          offset, firstDate, lastDate, existingSnaps, useRunningTotalFormat);
    }
  }

  @SuppressWarnings("squid:S2583")
  private List<T> fillAndFilterSnapshots(
      TemporalAmount offset,
      OffsetDateTime firstDate,
      OffsetDateTime lastDate,
      List<T> existingSnaps,
      boolean useRunningTotalFormat) {

    List<T> result = new ArrayList<>();
    OffsetDateTime nextDate = firstDate;
    T lastSnap = null;
    OffsetDateTime lastSnapDate = null;
    Optional<T> pending = Optional.empty();
    Optional<OffsetDateTime> pendingSnapDate = Optional.empty();

    for (T snapshot : existingSnaps) {
      OffsetDateTime snapDate = reportFillerAdapter.getDate(snapshot);

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
      result.addAll(
          fillWithRange(
              nextDate, lastSnapDate.minus(offset), offset, lastSnap, useRunningTotalFormat));
      if (pending.isEmpty() || reportFillerAdapter.itemIsLarger(pending.get(), snapshot)) {
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
    } else if (lastSnapDate.isBefore(lastDate)) {
      result.addAll(
          fillWithRange(
              lastSnapDate.plus(offset), lastDate, offset, lastSnap, useRunningTotalFormat));
    }
    return result;
  }

  private List<T> fillWithRange(
      OffsetDateTime start,
      OffsetDateTime end,
      TemporalAmount offset,
      T snapshot,
      boolean useRunningTotalFormat) {
    List<T> result = new ArrayList<>();
    OffsetDateTime next = timeAdjuster.adjustToPeriodStart(OffsetDateTime.from(start));
    while (next.isBefore(end) || next.isEqual(end)) {
      result.add(reportFillerAdapter.createDefaultItem(next, snapshot, useRunningTotalFormat));
      next = timeAdjuster.adjustToPeriodStart(next.plus(offset));
    }
    return result;
  }
}
