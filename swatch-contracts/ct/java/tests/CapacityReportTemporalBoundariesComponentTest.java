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
package tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.CapacityReportByMetricId;
import com.redhat.swatch.contract.test.model.CapacitySnapshotByMetricId;
import com.redhat.swatch.contract.test.model.GranularityType;
import domain.Product;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CapacityReportTemporalBoundariesComponentTest extends BaseContractComponentTest {

  private static final double RHEL_SOCKETS_CAPACITY = 1.0;
  private static final OffsetDateTime REPORT_BEGINNING = clock.startOfToday().minusDays(9);
  private static final OffsetDateTime REPORT_ENDING = clock.endOfToday();

  @TestPlanName("capacity-report-temporal-boundaries-TC001")
  @Test
  void shouldIncludeCapacityWhenSubscriptionStartsDuringReportPeriod() {
    // Given: A subscription starting on day 5 of the 10-day report period
    givenSubscriptionActiveBetween(
        clock.startOfToday().minusDays(5), clock.endOfToday().plusDays(1));

    // When: Get daily capacity report for the 10-day period
    var snapshots = whenGetDailyReportSnapshots();

    // Then: Verify capacity report structure
    assertEquals(10, snapshots.size());

    // Then: Days 1-4 should have no data or zero capacity (before subscription starts)
    thenSnapshotsBeforeSubscriptionHaveNoData(snapshots, 0, 4);

    // Then: Days 5-10 should have subscription capacity and hasData=true
    thenSnapshotsWithinSubscriptionPeriodHaveData(snapshots, 5, 9);
  }

  @TestPlanName("capacity-report-temporal-boundaries-TC002")
  @Test
  void shouldExcludeCapacityWhenSubscriptionEndsDuringReportPeriod() {
    // Given: A subscription ending on day 5 of 10 days
    final OffsetDateTime subscriptionStart = clock.startOfToday().minusDays(15);
    final OffsetDateTime subscriptionEnd = clock.startOfToday().minusDays(5);

    givenSubscriptionActiveBetween(subscriptionStart, subscriptionEnd);

    // When: Get daily capacity report
    var snapshots = whenGetDailyReportSnapshots();

    // Then: Verify capacity report structure
    assertEquals(10, snapshots.size());

    // Then: Days 1-4 should have capacity included
    thenSnapshotsWithinSubscriptionPeriodHaveData(snapshots, 0, 3);

    // Then: Days 5-10 should have capacity excluded (after subscription ends)
    thenSnapshotsAfterSubscriptionHaveNoData(snapshots, 4, 9);
  }

  @TestPlanName("capacity-report-temporal-boundaries-TC003")
  @Test
  void shouldExcludeSubscriptionCompletelyOutsideRange() {
    // Given: A subscription with dates completely outside the report range
    final OffsetDateTime subscriptionStart = clock.startOfToday().minusDays(30);
    final OffsetDateTime subscriptionEnd = clock.startOfToday().minusDays(15);
    givenSubscriptionActiveBetween(subscriptionStart, subscriptionEnd);

    // When: Get capacity report
    var snapshots = whenGetDailyReportSnapshots();

    // Then: Subscription not included in any snapshots
    assertFalse(snapshots.isEmpty());

    // Then: All snapshots should have value=0 or hasData=false
    thenAllSnapshotsHaveNoData(snapshots);
  }

  private void givenSubscriptionActiveBetween(OffsetDateTime from, OffsetDateTime to) {
    String testSku = RandomUtils.generateRandom();
    givenPhysicalSubscriptionIsCreated(testSku, 4.0, RHEL_SOCKETS_CAPACITY, from, to);
  }

  private List<CapacitySnapshotByMetricId> whenGetDailyReportSnapshots() {
    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricId(
            Product.RHEL,
            orgId,
            SOCKETS.toString(),
            REPORT_BEGINNING,
            REPORT_ENDING,
            GranularityType.DAILY,
            null);

    assertNotNull(capacityReport);
    assertNotNull(capacityReport.getData());
    return capacityReport.getData();
  }

  private void thenSnapshotsBeforeSubscriptionHaveNoData(
      List<CapacitySnapshotByMetricId> snapshots, int startIndex, int endIndex) {
    for (int i = startIndex; i <= endIndex && i < snapshots.size(); i++) {
      CapacitySnapshotByMetricId snapshot = snapshots.get(i);
      assertNotNull(snapshot.getDate(), "Snapshot " + i + " date should not be null");

      // Before subscription starts: either hasData=false or value=0
      if (snapshot.getHasData()) {
        assertEquals(
            0.0,
            snapshot.getValue().doubleValue(),
            "Snapshot "
                + i
                + " (before subscription) should have zero capacity or no data, but hasData=true");
      } else {
        assertFalse(
            snapshot.getHasData(),
            "Snapshot " + i + " (before subscription) should have hasData=false");
      }
    }
  }

  private void thenSnapshotsAfterSubscriptionHaveNoData(
      List<CapacitySnapshotByMetricId> snapshots, int startIndex, int endIndex) {
    for (int i = startIndex; i <= endIndex && i < snapshots.size(); i++) {
      CapacitySnapshotByMetricId snapshot = snapshots.get(i);
      assertNotNull(snapshot.getDate(), "Snapshot " + i + " date should not be null");

      // After subscription ends: either hasData=false or value=0
      if (snapshot.getHasData()) {
        assertEquals(
            0.0,
            snapshot.getValue().doubleValue(),
            "Snapshot "
                + i
                + " (after subscription ends) should have zero capacity or no data, but hasData=true");
      } else {
        assertFalse(
            snapshot.getHasData(),
            "Snapshot " + i + " (after subscription ends) should have hasData=false");
      }
    }
  }

  private void thenSnapshotsWithinSubscriptionPeriodHaveData(
      List<CapacitySnapshotByMetricId> snapshots, int startIndex, int endIndex) {
    for (int i = startIndex; i <= endIndex && i < snapshots.size(); i++) {
      CapacitySnapshotByMetricId snapshot = snapshots.get(i);
      assertNotNull(snapshot.getDate(), "Snapshot " + i + " date should not be null");

      // Within subscription period: should have data
      assertTrue(
          snapshot.getHasData(),
          "Snapshot " + i + " (within subscription period) should have hasData=true");

      assertEquals(
          RHEL_SOCKETS_CAPACITY,
          snapshot.getValue().doubleValue(),
          "Snapshot " + i + " (within subscription period) should have expected capacity value");
    }
  }

  private void thenAllSnapshotsHaveNoData(List<CapacitySnapshotByMetricId> snapshots) {
    for (int i = 0; i < snapshots.size(); i++) {
      CapacitySnapshotByMetricId snapshot = snapshots.get(i);
      assertNotNull(snapshot.getDate(), "Snapshot " + i + " date should not be null");

      // All snapshots should have value=0 or hasData=false
      if (snapshot.getHasData()) {
        assertEquals(
            0.0,
            snapshot.getValue().doubleValue(),
            "Snapshot " + i + " should have zero capacity when subscription is outside range");
      } else {
        assertFalse(
            snapshot.getHasData(),
            "Snapshot " + i + " should have hasData=false when subscription is outside range");
      }
    }
  }
}
