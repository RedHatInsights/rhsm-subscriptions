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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.Test;

class SnapshotTimeAdjusterTest {

  private ApplicationClock clock;

  SnapshotTimeAdjusterTest() {
    clock = new TestClockConfiguration().adjustableClock();
  }

  @Test
  void testDailyTimeAdjusterCreation() {
    SnapshotTimeAdjuster adjuster = SnapshotTimeAdjuster.getTimeAdjuster(clock, Granularity.DAILY);
    assertTrue(adjuster instanceof DailyTimeAdjuster);
  }

  @Test
  void testWeeklyTimeAdjusterCreation() {
    SnapshotTimeAdjuster adjuster = SnapshotTimeAdjuster.getTimeAdjuster(clock, Granularity.WEEKLY);
    assertTrue(adjuster instanceof WeeklyTimeAdjuster);
  }

  @Test
  void testMonthlyTimeAdjusterCreation() {
    SnapshotTimeAdjuster adjuster =
        SnapshotTimeAdjuster.getTimeAdjuster(clock, Granularity.MONTHLY);
    assertTrue(adjuster instanceof MonthlyTimeAdjuster);
  }

  @Test
  void testYearlyTimeAdjusterCreation() {
    SnapshotTimeAdjuster adjuster = SnapshotTimeAdjuster.getTimeAdjuster(clock, Granularity.YEARLY);
    assertTrue(adjuster instanceof YearlyTimeAdjuster);
  }

  @Test
  void testQuarterlyTimeAdjusterCreation() {
    SnapshotTimeAdjuster adjuster =
        SnapshotTimeAdjuster.getTimeAdjuster(clock, Granularity.QUARTERLY);
    assertTrue(adjuster instanceof QuarterlyTimeAdjuster);
  }
}
