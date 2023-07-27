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

import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.TallyReportDataPoint;
import org.junit.jupiter.api.Test;

class TallyReportDataPointAdapterTest {

  ApplicationClock clock = new TestClockConfiguration().adjustableClock();
  TallyReportDataPointAdapter adapter = new TallyReportDataPointAdapter(clock);

  @Test
  void createDefaultItemRunningTotalPast() {
    var previous = new TallyReportDataPoint().value(10);
    var itemDate = clock.startOfDay(clock.now());
    var point = adapter.createDefaultItem(itemDate, previous, true);
    assertEquals(10, point.getValue());
    assertTrue(point.getHasData());
  }

  @Test
  void createDefaultItemRunningTotalFuture() {
    var previous = new TallyReportDataPoint().value(10);
    var itemDate = clock.startOfDay(clock.now().plusDays(1));
    var point = adapter.createDefaultItem(itemDate, previous, true);
    assertEquals(0, point.getValue());
    assertFalse(point.getHasData());
  }

  @Test
  void createDefaultItemNoRunningTotal() {
    var previous = new TallyReportDataPoint().value(10);
    var itemDate = clock.startOfDay(clock.now());
    var point = adapter.createDefaultItem(itemDate, previous, false);
    assertEquals(0, point.getValue());
    assertFalse(point.getHasData());
  }

  @Test
  void createDefaultItemNullPrevious() {
    var itemDate = clock.startOfDay(clock.now());
    var point = adapter.createDefaultItem(itemDate, null, true);
    assertEquals(0, point.getValue());
    assertFalse(point.getHasData());
  }

  @Test
  void createDefaultItemNullPreviousValue() {
    var previous = new TallyReportDataPoint().value(null);
    var itemDate = clock.startOfDay(clock.now());
    var point = adapter.createDefaultItem(itemDate, previous, true);
    assertEquals(0, point.getValue());
    assertFalse(point.getHasData());
  }
}
