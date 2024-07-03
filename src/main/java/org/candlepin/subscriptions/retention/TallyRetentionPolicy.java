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
package org.candlepin.subscriptions.retention;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import lombok.AllArgsConstructor;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.model.Granularity;
import org.springframework.stereotype.Component;

/**
 * Calculates cutoff dates given a retention policy.
 *
 * <p>Note that retention policy is defined in terms of how many *complete* units of time are kept.
 * For example, if the retention policy is 3 months, then the previous 3 months are retained, in
 * addition to the current incomplete month.
 */
@Component
@AllArgsConstructor
public class TallyRetentionPolicy {

  private final ApplicationClock applicationClock;
  private final TallyRetentionPolicyProperties config;

  /**
   * Get the cutoff date for the passed granularity.
   *
   * <p>Any snapshots of this granularity older than the cutoff date should be removed.
   *
   * @param granularity
   * @return cutoff date (i.e. dates less than this are candidates for removal), or null
   */
  public OffsetDateTime getCutoffDate(Granularity granularity) {
    OffsetDateTime today =
        OffsetDateTime.now(applicationClock.getClock()).truncatedTo(ChronoUnit.DAYS);
    switch (granularity) {
      case HOURLY:
        if (config.getHourly() == null) {
          return null;
        }
        return applicationClock.startOfCurrentHour().minusHours(config.getHourly());
      case DAILY:
        if (config.getDaily() == null) {
          return null;
        }
        return today.minusDays(config.getDaily());
      case WEEKLY:
        if (config.getWeekly() == null) {
          return null;
        }
        OffsetDateTime nearestPreviousSunday =
            today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        return nearestPreviousSunday.minusWeeks(config.getWeekly());
      case MONTHLY:
        if (config.getMonthly() == null) {
          return null;
        }
        OffsetDateTime firstDayOfMonth = today.with(TemporalAdjusters.firstDayOfMonth());
        return firstDayOfMonth.minusMonths(config.getMonthly());
      case QUARTERLY:
        if (config.getQuarterly() == null) {
          return null;
        }
        firstDayOfMonth = today.with(TemporalAdjusters.firstDayOfMonth());
        return firstDayOfMonth
            .with(
                ChronoField.MONTH_OF_YEAR,
                firstDayOfMonth.getMonth().firstMonthOfQuarter().getValue())
            .minusMonths(3L * config.getQuarterly());
      case YEARLY:
        if (config.getYearly() == null) {
          return null;
        }
        return today.with(TemporalAdjusters.firstDayOfYear()).minusYears(config.getYearly());
      default:
        throw new IllegalArgumentException(
            String.format("Unsupported granularity: %s", granularity));
    }
  }

  public long getSnapshotsToDeleteInBatches() {
    return config.getSnapshotsToDeleteInBatches();
  }
}
