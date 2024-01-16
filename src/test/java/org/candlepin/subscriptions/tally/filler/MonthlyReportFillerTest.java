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

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.util.List;
import org.candlepin.subscriptions.db.model.Granularity;
import org.junit.jupiter.api.Test;

class MonthlyReportFillerTest extends BaseReportFillerTest {

  @Override
  Granularity granularity() {
    return Granularity.MONTHLY;
  }

  @Test
  void noExistingSnapsShouldFillWithMonthlyGranularity() {
    OffsetDateTime start = clock.startOfCurrentMonth();
    OffsetDateTime end = start.plusMonths(3);

    var filled = whenFillGaps(List.of(), start, end);
    assertEquals(4, filled.size());
    assertDataPointIsEmpty(filled.get(0), start);
    assertDataPointIsEmpty(filled.get(1), start.plusMonths(1));
    assertDataPointIsEmpty(filled.get(2), start.plusMonths(2));
    assertDataPointIsEmpty(filled.get(3), start.plusMonths(3));
  }

  @Test
  void startAndEndDatesForMonthlyAreResetWhenDateIsMidMonth() {
    OffsetDateTime start = clock.now();
    OffsetDateTime end = start.plusMonths(3);

    // Expected to start on the beginning of the month.
    OffsetDateTime expectedStart = clock.startOfMonth(start);

    var filled = whenFillGaps(List.of(), start, end);
    assertEquals(4, filled.size());
    assertDataPointIsEmpty(filled.get(0), expectedStart);
    assertDataPointIsEmpty(filled.get(1), expectedStart.plusMonths(1));
    assertDataPointIsEmpty(filled.get(2), expectedStart.plusMonths(2));
    assertDataPointIsEmpty(filled.get(3), expectedStart.plusMonths(3));
  }

  @Test
  void testSnapshotsIgnoredWhenNoDatesSet() {
    OffsetDateTime start = clock.startOfCurrentMonth();
    OffsetDateTime end = start.plusMonths(3);

    var points = List.of(point(null, 2.0), point(null, 6.0));
    var filled = whenFillGaps(points, start, end);

    assertEquals(4, filled.size());
    assertDataPointIsEmpty(filled.get(0), start);
    assertDataPointIsEmpty(filled.get(1), start.plusMonths(1));
    assertDataPointIsEmpty(filled.get(2), start.plusMonths(2));
    assertDataPointIsEmpty(filled.get(3), start.plusMonths(3));
  }

  @Test
  void shouldFillGapsBasedOnExistingSnapshotsForMonthlyGranularity() {
    OffsetDateTime start = clock.startOfCurrentMonth();
    OffsetDateTime point1Date = start.plusMonths(1);
    OffsetDateTime end = start.plusMonths(3);

    var points = List.of(point(point1Date, 2.0), point(end, 6.0));
    var filled = whenFillGaps(points, start, end);

    assertEquals(4, filled.size());
    assertDataPointIsEmpty(filled.get(0), start);
    assertDataPoint(filled.get(1), start.plusMonths(1), 2.0);
    assertDataPointIsEmpty(filled.get(2), start.plusMonths(2));
    assertDataPoint(filled.get(3), start.plusMonths(3), 6.0);
  }
}
