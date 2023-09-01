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

import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.SnapshotTimeAdjuster;
import org.candlepin.subscriptions.utilization.api.model.TallySnapshot;

/** Responsible for creating ReportFiller objects based on granularity. */
public class ReportFillerFactory {

  private ReportFillerFactory() {
    throw new IllegalStateException("Utility class; should never be instantiated!");
  }

  /**
   * Creates an instance of a ReportFiller based on a Granularity, for filling in lists of
   * TallySnapshot.
   *
   * @param clock an application clock instance to base dates off of.
   * @param granularity the target granularity
   * @return a ReportFiller instance for the specified granularity.
   */
  public static ReportFiller<TallySnapshot> getInstance(
      ApplicationClock clock, Granularity granularity) {
    SnapshotTimeAdjuster timeAdjuster = SnapshotTimeAdjuster.getTimeAdjuster(clock, granularity);
    return new ReportFiller<>(timeAdjuster, new TallySnapshotAdapter(clock));
  }

  /**
   * Creates an instance of a ReportFiller based on a Granularity, for filling in lists of
   * TallyReportDataPoint.
   *
   * @param clock an application clock instance to base dates off of.
   * @param granularity the target granularity
   * @return a ReportFiller instance for the specified granularity.
   */
  public static ReportFiller<UnroundedTallyReportDataPoint> getDataPointReportFiller(
      ApplicationClock clock, Granularity granularity) {
    SnapshotTimeAdjuster timeAdjuster = SnapshotTimeAdjuster.getTimeAdjuster(clock, granularity);
    return new ReportFiller<>(timeAdjuster, new UnroundedTallyReportDataPointAdapter(clock));
  }
}
