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

import java.time.OffsetDateTime;
import java.util.Objects;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.TallyReportDataPoint;

public class TallyReportDataPointAdapter implements ReportFillerAdapter<TallyReportDataPoint> {

  private final ApplicationClock clock;

  public TallyReportDataPointAdapter(ApplicationClock clock) {
    this.clock = clock;
  }

  @Override
  public boolean itemIsLarger(TallyReportDataPoint oldData, TallyReportDataPoint newData) {
    return newData.getValue() >= oldData.getValue();
  }

  @Override
  public TallyReportDataPoint createDefaultItem(
      OffsetDateTime itemDate, TallyReportDataPoint previous, boolean useRunningTotalFormat) {
    var point = new TallyReportDataPoint().date(itemDate).hasData(false).value(0);
    // itemDate is already adjusted in ReportFiller to be at the period start.  ReportFiller is also
    // responsible for ticking through every period contained in the requested range, so we should
    // not see any time gaps in the finished report.
    if (itemDate.isBefore(clock.now()) && useRunningTotalFormat && previous != null) {
      point
          .hasData(previous.getValue() != null)
          .value(Objects.requireNonNullElse(previous.getValue(), 0));
    }

    return point;
  }

  @Override
  public OffsetDateTime getDate(TallyReportDataPoint item) {
    return item.getDate();
  }
}
