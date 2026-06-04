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
package org.candlepin.subscriptions.util;

import java.time.OffsetDateTime;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import org.candlepin.clock.ApplicationClock;

/**
 * A ReportFiller instance that will fill the given TallyReport's snapshots based on a MONTHLY
 * granularity.
 */
public class YearlyTimeAdjuster extends SnapshotTimeAdjuster {

  public YearlyTimeAdjuster(ApplicationClock clock) {
    super(clock);
  }

  @Override
  public TemporalAmount getSnapshotOffset() {
    return Period.ofYears(1);
  }

  @Override
  public OffsetDateTime adjustToPeriodStart(OffsetDateTime toAdjust) {
    return clock.startOfYear(toAdjust);
  }

  @Override
  public OffsetDateTime adjustToPeriodEnd(OffsetDateTime toAdjust) {
    return clock.endOfYear(toAdjust);
  }
}
