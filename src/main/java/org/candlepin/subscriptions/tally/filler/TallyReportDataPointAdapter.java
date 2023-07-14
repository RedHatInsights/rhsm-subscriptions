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
import org.candlepin.subscriptions.utilization.api.model.TallyReportDataPoint;

public class TallyReportDataPointAdapter implements ReportFillerAdapter<TallyReportDataPoint> {

  @Override
  public boolean itemIsLarger(TallyReportDataPoint oldData, TallyReportDataPoint newData) {
    return newData.getValue() >= oldData.getValue();
  }

  @Override
  public TallyReportDataPoint createDefaultItem(
      OffsetDateTime itemDate, TallyReportDataPoint previous, boolean useRunningTotalFormat) {
    var point = new TallyReportDataPoint().date(itemDate).hasData(false).value(0);
    if (useRunningTotalFormat) {
      // Guard against previous being null and against a previous object with a value field equal
      // to null
      var prevValue = (previous == null) ? 0 : Objects.requireNonNullElse(previous.getValue(), 0);
      point.value(prevValue);
    }
    return point;
  }

  @Override
  public OffsetDateTime getDate(TallyReportDataPoint item) {
    return item.getDate();
  }
}
