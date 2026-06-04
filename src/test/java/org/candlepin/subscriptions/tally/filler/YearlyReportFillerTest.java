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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;
import java.util.List;
import org.candlepin.subscriptions.db.model.Granularity;
import org.junit.jupiter.api.Test;

class YearlyReportFillerTest extends BaseReportFillerTest {

  @Override
  Granularity granularity() {
    return Granularity.YEARLY;
  }

  @Test
  void noExistingSnapsShouldFillWithYearlyGranularity() {
    OffsetDateTime start = clock.startOfCurrentYear();
    OffsetDateTime end = start.plusYears(3);

    var filled = whenFillGaps(List.of(), start, end);
    assertEquals(4, filled.size());
    assertDataPointIsEmpty(filled.get(0), start);
    assertDataPointIsEmpty(filled.get(1), start.plusYears(1));
    assertDataPointIsEmpty(filled.get(2), start.plusYears(2));
    assertDataPointIsEmpty(filled.get(3), start.plusYears(3));
  }

  @Test
  void startAndEndDatesForYearlyAreResetWhenDateIsMidYear() {
    // Mid year start
    OffsetDateTime start = clock.now();
    // Mid year end
    OffsetDateTime end = start.plusYears(3);
    // Expected to start on the beginning of the year.
    OffsetDateTime expectedStart = clock.startOfYear(start);

    var filled = whenFillGaps(List.of(), start, end);
    assertEquals(4, filled.size());
    assertDataPointIsEmpty(filled.get(0), expectedStart);
    assertDataPointIsEmpty(filled.get(1), expectedStart.plusYears(1));
    assertDataPointIsEmpty(filled.get(2), expectedStart.plusYears(2));
    assertDataPointIsEmpty(filled.get(3), expectedStart.plusYears(3));
  }

  @Test
  void testSnapshotsIgnoredWhenNoDatesSet() {
    OffsetDateTime start = clock.startOfCurrentYear();
    OffsetDateTime end = start.plusYears(3);

    var points = List.of(point(null, 2.0), point(null, 6.0));
    var filled = whenFillGaps(points, start, end);

    assertEquals(4, filled.size());
    assertDataPointIsEmpty(filled.get(0), start);
    assertDataPointIsEmpty(filled.get(1), start.plusYears(1));
    assertDataPointIsEmpty(filled.get(2), start.plusYears(2));
    assertDataPointIsEmpty(filled.get(3), start.plusYears(3));
  }

  @Test
  void shouldFillGapsBasedOnExistingSnapshotsForYearlyGranularity() {
    OffsetDateTime start = clock.startOfCurrentYear();
    OffsetDateTime point1Date = start.plusYears(1);
    OffsetDateTime end = start.plusYears(3);

    var points = List.of(point(point1Date, 2.0), point(end, 6.0));
    var filled = whenFillGaps(points, start, end);

    assertEquals(4, filled.size());
    assertDataPointIsEmpty(filled.get(0), start);
    assertDataPoint(filled.get(1), start.plusYears(1), 2.0);
    assertDataPointIsEmpty(filled.get(2), start.plusYears(2));
    assertDataPoint(filled.get(3), start.plusYears(3), 6.0);
  }
}
