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

class HourlyReportFillerTest extends BaseReportFillerTest {

  @Override
  Granularity granularity() {
    return Granularity.HOURLY;
  }

  @Test
  void noExistingSnapsShouldFillWithDailyGranularity() {
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusHours(3);

    var filled = whenFillGaps(List.of(), start, end);

    assertEquals(4, filled.size());
    assertDataPointIsEmpty(filled.get(0), start);
    assertDataPointIsEmpty(filled.get(1), start.plusHours(1));
    assertDataPointIsEmpty(filled.get(2), start.plusHours(2));
    assertDataPointIsEmpty(filled.get(3), start.plusHours(3));
  }

  @Test
  void shouldFillGapsBasedOnExistingSnapshotsForDailyGranularity() {
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime point1Date = start.plusHours(2);
    OffsetDateTime end = start.plusHours(4);

    var points = List.of(point(point1Date, 2.0), point(end, 6.0));
    var filled = whenFillGaps(points, start, end);

    assertEquals(5, filled.size());
    assertDataPointIsEmpty(filled.get(0), start);
    assertDataPointIsEmpty(filled.get(1), start.plusHours(1));
    assertDataPoint(filled.get(2), start.plusHours(2), 2.0);
    assertDataPointIsEmpty(filled.get(3), start.plusHours(3));
    assertDataPoint(filled.get(4), start.plusHours(4), 6.0);
  }

  @Test
  void testSnapshotsIgnoredWhenNoDatesSet() {
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusHours(3);

    var points = List.of(point(null, 2.0), point(null, 6.0));
    var filled = whenFillGaps(points, start, end);

    assertEquals(4, filled.size());
    assertDataPointIsEmpty(filled.get(0), start);
    assertDataPointIsEmpty(filled.get(1), start.plusHours(1));
    assertDataPointIsEmpty(filled.get(2), start.plusHours(2));
    assertDataPointIsEmpty(filled.get(3), start.plusHours(3));
  }

  @Test
  void testNoRedundantSnapshotsEmittedAscending() {
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime point1Date = start.plusHours(1);
    OffsetDateTime point2Date = start.plusHours(1).plusMinutes(2);
    OffsetDateTime end = start.plusHours(3);

    var points = List.of(point(point1Date, 2.0), point(point2Date, 5.0));
    var filled = whenFillGaps(points, start, end);

    assertEquals(4, filled.size());
    assertDataPointIsEmpty(filled.get(0), start);
    assertDataPoint(filled.get(1), point2Date, 5.0);
    assertDataPointIsEmpty(filled.get(2), start.plusHours(2));
    assertDataPointIsEmpty(filled.get(3), start.plusHours(3));
  }

  @Test
  void testNoRedundantSnapshotsEmittedDescending() {
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime point1Date = start.plusHours(1);
    OffsetDateTime point2Date = start.plusHours(1).plusMinutes(2);
    OffsetDateTime end = start.plusHours(3);

    var points = List.of(point(point2Date, 5.0), point(point1Date, 2.0));
    var filled = whenFillGaps(points, start, end);

    assertEquals(4, filled.size());
    assertDataPointIsEmpty(filled.get(0), start);
    assertDataPoint(filled.get(1), point2Date, 5.0);
    assertDataPointIsEmpty(filled.get(2), start.plusHours(2));
    assertDataPointIsEmpty(filled.get(3), start.plusHours(3));
  }
}
