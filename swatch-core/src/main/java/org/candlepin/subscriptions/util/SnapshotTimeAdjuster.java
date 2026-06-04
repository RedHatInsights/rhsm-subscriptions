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
import java.time.temporal.TemporalAmount;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.model.Granularity;

/** Abstract class for adjusting time to a given report period. */
public abstract class SnapshotTimeAdjuster {
  protected final ApplicationClock clock;

  SnapshotTimeAdjuster(ApplicationClock clock) {
    this.clock = clock;
  }

  /**
   * Get the TemporalAmount that represents the offset period between each report snapshot. A
   * snapshot offset is defined by the amount of time between each snapshot in a report. For
   * example, filling in a report for Daily granularity, the offset would be 1 DAY given that each
   * daily snapshot spans one full day.
   *
   * @return a temporal amount representing the period offset.
   */
  public abstract TemporalAmount getSnapshotOffset();

  /**
   * Adjust the given date to the start of this filler's snapshot period. For example, filling in a
   * report for Daily granularity, the adjusted date would be at the start of the day.
   *
   * @param toAdjust the date to adjust
   * @return the adjusted date instance
   */
  public abstract OffsetDateTime adjustToPeriodStart(OffsetDateTime toAdjust);

  /**
   * Adjust the given date to the end of this filler's snapshot period. For example, filling in a
   * report for Daily granularity, the adjusted date would be at the end of the day.
   *
   * @param toAdjust the date to adjust
   * @return the adjusted date instance
   */
  public abstract OffsetDateTime adjustToPeriodEnd(OffsetDateTime toAdjust);

  /**
   * Create a SnapshotTimeAdjuster that works for the given granularity.
   *
   * @param clock application reference clock
   * @param granularity granularity to adjust against
   * @return an instance of SnapshotTimeAdjuster that handles the passed granularity
   */
  public static SnapshotTimeAdjuster getTimeAdjuster(
      ApplicationClock clock, Granularity granularity) {
    switch (granularity) {
      case HOURLY:
        return new HourlyTimeAdjuster(clock);
      case DAILY:
        return new DailyTimeAdjuster(clock);
      case WEEKLY:
        return new WeeklyTimeAdjuster(clock);
      case MONTHLY:
        return new MonthlyTimeAdjuster(clock);
      case YEARLY:
        return new YearlyTimeAdjuster(clock);
      case QUARTERLY:
        return new QuarterlyTimeAdjuster(clock);
      default:
        throw new IllegalArgumentException(
            String.format("Unsupported granularity: %s", granularity));
    }
  }
}
