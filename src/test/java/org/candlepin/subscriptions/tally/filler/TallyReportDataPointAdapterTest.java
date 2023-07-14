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
import org.candlepin.subscriptions.utilization.api.model.TallyReportDataPoint;
import org.junit.jupiter.api.Test;

class TallyReportDataPointAdapterTest {

  TallyReportDataPointAdapter adapter = new TallyReportDataPointAdapter();

  @Test
  void createDefaultItemRunningTotal() {
    var previous = new TallyReportDataPoint().value(10);
    var now = OffsetDateTime.now();
    var point = adapter.createDefaultItem(now, previous, true);
    assertEquals(10, point.getValue());
  }

  @Test
  void createDefaultItemNoRunningTotal() {
    var previous = new TallyReportDataPoint().value(10);
    var now = OffsetDateTime.now();
    var point = adapter.createDefaultItem(now, previous, false);
    assertEquals(0, point.getValue());
  }

  @Test
  void createDefaultItemNullPrevious() {
    var now = OffsetDateTime.now();
    var point = adapter.createDefaultItem(now, null, true);
    assertEquals(0, point.getValue());
  }

  @Test
  void createDefaultItemNullPreviousValue() {
    var previous = new TallyReportDataPoint().value(null);
    var now = OffsetDateTime.now();
    var point = adapter.createDefaultItem(now, previous, true);
    assertEquals(0, point.getValue());
  }
}
