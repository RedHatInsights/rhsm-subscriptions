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
  private static final int REPORT_PERIOD_DAYS = 10;
  private static final OffsetDateTime REPORT_BEGINNING = clock.startOfToday().minusDays(9);
  private static final OffsetDateTime REPORT_ENDING = clock.endOfToday();

  @TestPlanName("capacity-report-temporal-boundaries-TC001")
  @Test
  void shouldIncludeCapacityWhenSubscriptionStartsDuringReportPeriod() {
    // Given: A 10-day report period from (today-9) to today
    // And: A subscription starting on snapshot 4 (today-5) and ending after report period
    final int SUBSCRIPTION_START_DAY = 5; // Days before today
    final OffsetDateTime subscriptionStart = clock.startOfToday().minusDays(SUBSCRIPTION_START_DAY);
    final OffsetDateTime subscriptionEnd = clock.endOfToday().plusDays(1);

    givenSubscriptionActiveBetween(subscriptionStart, subscriptionEnd);

    // When: Get daily capacity report for the 10-day period
    var snapshots = whenGetDailyReportSnapshots();

    // Then: Verify capacity report structure
    assertEquals(REPORT_PERIOD_DAYS, snapshots.size());

    // Then: Snapshots 0-3 (days before subscription starts) should have no data
    // Snapshot 0 = today-9, Snapshot 1 = today-8, Snapshot 2 = today-7, Snapshot 3 = today-6
    thenSnapshotsBeforeSubscriptionHaveNoData(snapshots, 0, 3);

    // Then: Snapshots 4-9 (subscription active period) should have capacity data
    // Snapshot 4 = today-5 (subscription START - inclusive), ..., Snapshot 9 = today
    thenSnapshotsWithinSubscriptionPeriodHaveData(snapshots, 4, 9);
  }

  @TestPlanName("capacity-report-temporal-boundaries-TC002")
  @Test
  void shouldExcludeCapacityWhenSubscriptionEndsDuringReportPeriod() {
    // Given: A 10-day report period from (today-9) to today
    // And: A subscription starting before report period and ending at end of snapshot 3 (today-6)
    final int LAST_ACTIVE_DAY = 6; // Last day subscription is active (days before today)
    final OffsetDateTime subscriptionStart = clock.startOfToday().minusDays(15);
    final OffsetDateTime subscriptionEnd = clock.endOfToday().minusDays(LAST_ACTIVE_DAY);

    givenSubscriptionActiveBetween(subscriptionStart, subscriptionEnd);

    // When: Get daily capacity report
    var snapshots = whenGetDailyReportSnapshots();

    // Then: Verify capacity report structure
    assertEquals(REPORT_PERIOD_DAYS, snapshots.size());

    // Then: Snapshots 0-3 (subscription still active) should have capacity data
    // Snapshot 0 = today-9, Snapshot 3 = today-6 (last active day, ends at 23:59:59.999)
    thenSnapshotsWithinSubscriptionPeriodHaveData(snapshots, 0, 3);

    // Then: Snapshots 4-9 (after subscription ends) should have no data
    // Snapshot 4 = today-5 (after subscription end - subscription inactive), Snapshot 9 = today
    thenSnapshotsAfterSubscriptionHaveNoData(snapshots, 4, 9);
  }

  @TestPlanName("capacity-report-temporal-boundaries-TC003")
  @Test
  void shouldExcludeSubscriptionCompletelyOutsideRange() {
    // Given: A 10-day report period from (today-9) to today
    // And: A subscription completely outside the report period (ended 15 days ago)
    final OffsetDateTime subscriptionStart = clock.startOfToday().minusDays(30);
    final OffsetDateTime subscriptionEnd = clock.startOfToday().minusDays(15);
    givenSubscriptionActiveBetween(subscriptionStart, subscriptionEnd);

    // When: Get capacity report
    var snapshots = whenGetDailyReportSnapshots();

    // Then: Subscription not included in any snapshots
    assertFalse(snapshots.isEmpty());
    assertEquals(REPORT_PERIOD_DAYS, snapshots.size());

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
