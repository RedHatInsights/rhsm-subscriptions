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
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.test.TestClockConfiguration;

abstract class BaseReportFillerTest {

  protected final ApplicationClock clock;
  protected final ReportFiller<UnroundedTallyReportDataPoint> filler;

  public BaseReportFillerTest() {
    clock = new TestClockConfiguration().adjustableClock();
    filler = ReportFillerFactory.getDataPointReportFiller(clock, granularity());
  }

  abstract Granularity granularity();

  List<UnroundedTallyReportDataPoint> whenFillGaps(
      List<UnroundedTallyReportDataPoint> existing, OffsetDateTime start, OffsetDateTime end) {
    return filler.fillGaps(existing, start, end, false);
  }

  void assertDataPoint(UnroundedTallyReportDataPoint point, OffsetDateTime date, double value) {
    assertEquals(date, point.date(), "Invalid point date");
    assertEquals(value, point.value(), "Invalid point value");
    assertEquals(value != 0.0, point.hasData());
  }

  void assertDataPointIsEmpty(UnroundedTallyReportDataPoint point, OffsetDateTime date) {
    assertDataPoint(point, date, 0.0);
  }

  UnroundedTallyReportDataPoint point(OffsetDateTime date, Double value) {
    return new UnroundedTallyReportDataPoint(date, value, value != null);
  }
}
