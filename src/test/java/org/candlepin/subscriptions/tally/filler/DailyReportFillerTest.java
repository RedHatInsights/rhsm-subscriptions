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

import static org.candlepin.subscriptions.tally.filler.Assertions.assertSnapshot;
import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.candlepin.subscriptions.utilization.api.model.TallyReport;
import org.candlepin.subscriptions.utilization.api.model.TallySnapshot;
import org.junit.jupiter.api.Test;

class DailyReportFillerTest {

  private ApplicationClock clock;
  private ReportFiller filler;

  public DailyReportFillerTest() {
    clock = new TestClockConfiguration().adjustableClock();
    filler = ReportFillerFactory.getInstance(clock, Granularity.DAILY);
  }

  @Test
  void noExistingSnapsShouldfillWithDailyGranularity() {
    OffsetDateTime start = clock.now();
    OffsetDateTime end = start.plusDays(3);

    TallyReport report = new TallyReport();
    report.setData(filler.fillGaps(report.getData(), start, end, false));

    List<TallySnapshot> filled = report.getData();
    assertEquals(4, filled.size());
    assertSnapshot(filled.get(0), clock.startOfDay(start), 0, 0, 0, false);
    assertSnapshot(filled.get(1), clock.startOfDay(start.plusDays(1)), null, null, null, false);
    assertSnapshot(filled.get(2), clock.startOfDay(start.plusDays(2)), null, null, null, false);
    assertSnapshot(filled.get(3), clock.startOfDay(start.plusDays(3)), null, null, null, false);
  }

  @Test
  void shouldFillGapsBasedOnExistingSnapshotsForDailyGranularity() {
    OffsetDateTime start = clock.now();
    OffsetDateTime snap1Date = start.plusDays(1);
    OffsetDateTime end = start.plusDays(3);

    TallySnapshot snap1 =
        new TallySnapshot().date(snap1Date).cores(2).sockets(3).instanceCount(4).hasData(true);
    TallySnapshot snap2 =
        new TallySnapshot().date(end).cores(5).sockets(6).instanceCount(7).hasData(true);
    List<TallySnapshot> snaps = Arrays.asList(snap1, snap2);

    TallyReport report = new TallyReport().data(snaps);
    report.setData(filler.fillGaps(report.getData(), start, end, false));

    List<TallySnapshot> filled = report.getData();
    assertEquals(4, filled.size());
    assertSnapshot(filled.get(0), clock.startOfDay(start), 0, 0, 0, false);
    assertSnapshot(
        filled.get(1),
        snap1.getDate(),
        snap1.getCores(),
        snap1.getSockets(),
        snap1.getInstanceCount(),
        true);
    assertSnapshot(filled.get(2), clock.startOfDay(start.plusDays(2)), null, null, null, false);
    assertSnapshot(
        filled.get(3),
        snap2.getDate(),
        snap2.getCores(),
        snap2.getSockets(),
        snap2.getInstanceCount(),
        true);
  }

  @Test
  void testSnapshotsIgnoredWhenNoDatesSet() {
    OffsetDateTime start = clock.startOfToday();
    OffsetDateTime end = start.plusDays(3);

    TallySnapshot snap1 = new TallySnapshot().cores(2).sockets(3).instanceCount(4).hasData(true);
    TallySnapshot snap2 = new TallySnapshot().cores(5).sockets(6).instanceCount(7).hasData(true);
    List<TallySnapshot> snaps = Arrays.asList(snap1, snap2);

    TallyReport report = new TallyReport().data(snaps);
    report.setData(filler.fillGaps(report.getData(), start, end, false));

    List<TallySnapshot> filled = report.getData();
    assertEquals(4, filled.size());
    assertSnapshot(filled.get(0), start, 0, 0, 0, false);
    assertSnapshot(filled.get(1), start.plusDays(1), null, null, null, false);
    assertSnapshot(filled.get(2), start.plusDays(2), null, null, null, false);
    assertSnapshot(filled.get(3), start.plusDays(3), null, null, null, false);
  }

  @Test
  void testNoRedundantSnapshotsEmittedAscending() {
    OffsetDateTime start = clock.now();
    OffsetDateTime snap1Date = start.plusDays(1);
    OffsetDateTime snap2Date = start.plusDays(1).plusMinutes(2);
    OffsetDateTime end = start.plusDays(3);

    TallySnapshot snap1 =
        new TallySnapshot().date(snap1Date).cores(2).sockets(3).instanceCount(4).hasData(true);
    TallySnapshot snap2 =
        new TallySnapshot().date(snap2Date).cores(5).sockets(6).instanceCount(7).hasData(true);
    List<TallySnapshot> snaps = Arrays.asList(snap1, snap2);

    TallyReport report = new TallyReport().data(snaps);
    report.setData(filler.fillGaps(report.getData(), start, end, false));

    List<TallySnapshot> filled = report.getData();
    assertEquals(4, filled.size());
    assertSnapshot(filled.get(0), clock.startOfDay(start), 0, 0, 0, false);
    assertSnapshot(
        filled.get(1),
        snap2.getDate(),
        snap2.getCores(),
        snap2.getSockets(),
        snap2.getInstanceCount(),
        true);
    assertSnapshot(filled.get(2), clock.startOfDay(start.plusDays(2)), null, null, null, false);
    assertSnapshot(filled.get(3), clock.startOfDay(start.plusDays(3)), null, null, null, false);
  }

  @Test
  void testNoRedundantSnapshotsEmittedDescending() {
    OffsetDateTime start = clock.now();
    OffsetDateTime snap1Date = start.plusDays(1);
    OffsetDateTime snap2Date = start.plusDays(1).plusMinutes(2);
    OffsetDateTime end = start.plusDays(3);

    TallySnapshot snap1 =
        new TallySnapshot().date(snap1Date).cores(2).sockets(3).instanceCount(4).hasData(true);
    TallySnapshot snap2 =
        new TallySnapshot().date(snap2Date).cores(5).sockets(6).instanceCount(7).hasData(true);
    List<TallySnapshot> snaps = Arrays.asList(snap2, snap1);

    TallyReport report = new TallyReport().data(snaps);
    report.setData(filler.fillGaps(report.getData(), start, end, false));

    List<TallySnapshot> filled = report.getData();
    assertEquals(4, filled.size());
    assertSnapshot(filled.get(0), clock.startOfDay(start), 0, 0, 0, false);
    assertSnapshot(
        filled.get(1),
        snap2.getDate(),
        snap2.getCores(),
        snap2.getSockets(),
        snap2.getInstanceCount(),
        true);
    assertSnapshot(filled.get(2), clock.startOfDay(start.plusDays(2)), null, null, null, false);
    assertSnapshot(filled.get(3), clock.startOfDay(start.plusDays(3)), null, null, null, false);
  }
}
