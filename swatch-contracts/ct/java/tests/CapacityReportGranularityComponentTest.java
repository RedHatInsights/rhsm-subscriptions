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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.CapacityReportByMetricId;
import com.redhat.swatch.contract.test.model.CapacitySnapshotByMetricId;
import com.redhat.swatch.contract.test.model.GranularityType;
import domain.Product;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CapacityReportGranularityComponentTest extends BaseContractComponentTest {

  private static final double RHEL_CORES_CAPACITY = 4.0;
  private static final double RHEL_SOCKETS_CAPACITY = 1.0;
  private static final double ROSA_CORES_CAPACITY = 8.0;

  /**
   * Helper method to verify capacity report contains expected number of snapshots and has valid
   * data.
   *
   * @param capacityReport The capacity report to validate
   * @param expectedSize Expected number of snapshots
   * @return List of snapshots for further validation
   */
  private List<CapacitySnapshotByMetricId> thenCapacityReportShouldContainSnapshots(
      CapacityReportByMetricId capacityReport, int expectedSize) {
    assertThat("Capacity report should not be null", capacityReport, notNullValue());
    assertThat(
        "Data array should have expected size", capacityReport.getData(), hasSize(expectedSize));

    List<CapacitySnapshotByMetricId> snapshots = capacityReport.getData();
    Assertions.assertNotNull(snapshots);
    thenAtLeastOneSnapshotHasValidCapacity(snapshots);

    return snapshots;
  }

  /**
   * Helper method to verify that at least one snapshot in the list contains valid capacity data.
   *
   * @param snapshots List of capacity snapshots to validate
   */
  private void thenAtLeastOneSnapshotHasValidCapacity(List<CapacitySnapshotByMetricId> snapshots) {
    boolean hasValidData =
        snapshots.stream()
            .anyMatch(
                snapshot -> {
                  assertThat("Date should not be null", snapshot.getDate(), notNullValue());
                  assertThat("Value should not be null", snapshot.getValue(), notNullValue());
                  assertThat("HasData should not be null", snapshot.getHasData(), notNullValue());
                  return snapshot.getHasData() && snapshot.getValue() > 0;
                });
    assertTrue(hasValidData, "Should have at least one snapshot with valid capacity data");
  }

  /**
   * Helper method to verify the first snapshot date matches the expected start timestamp.
   *
   * @param snapshots List of capacity snapshots
   * @param expectedStart Expected start timestamp
   */
  private void thenFirstSnapshotShouldStartAt(
      List<CapacitySnapshotByMetricId> snapshots, OffsetDateTime expectedStart) {
    assertThat(
        "First snapshot date should equal the beginning timestamp",
        snapshots.get(0).getDate(),
        equalTo(expectedStart));
  }

  /**
   * Helper method to verify the last snapshot date matches the expected end timestamp.
   *
   * @param snapshots List of capacity snapshots
   * @param expectedEnd Expected end timestamp
   */
  private void thenLastSnapshotShouldEndAt(
      List<CapacitySnapshotByMetricId> snapshots, OffsetDateTime expectedEnd) {
    assertThat(
        "Last snapshot date should equal the expected timestamp",
        snapshots.get(snapshots.size() - 1).getDate(),
        equalTo(expectedEnd));
  }

  /**
   * Helper method to verify all snapshots are aligned to hour boundaries.
   *
   * @param snapshots List of capacity snapshots to validate
   */
  private void thenAllSnapshotsAreAlignedToHourBoundaries(
      List<CapacitySnapshotByMetricId> snapshots) {
    for (int i = 0; i < snapshots.size(); i++) {
      OffsetDateTime snapshotDate = snapshots.get(i).getDate();
      assertThat(
          "Snapshot " + i + " should be aligned to hour boundary (minutes, seconds, nanos = 0)",
          snapshotDate,
          equalTo(clock.startOfHour(snapshotDate)));
    }
  }

  /**
   * Helper method to verify all snapshots with data have consistent capacity values.
   *
   * @param snapshots List of capacity snapshots to validate
   */
  private void thenAllSnapshotsHaveConsistentCapacity(List<CapacitySnapshotByMetricId> snapshots) {
    List<Double> capacityValues =
        snapshots.stream()
            .filter(snapshot -> Boolean.TRUE.equals(snapshot.getHasData()))
            .map(snapshot -> snapshot.getValue().doubleValue())
            .distinct()
            .toList();

    assertThat(
        "All snapshots with data should have the same capacity value", capacityValues, hasSize(1));
  }

  /*
  capacity-report-granularity-TC001 - Hourly Granularity Report

    Description: Verify capacity report with hourly granularity
    Setup:
        User authenticated with a valid org_id
    Action: GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}
    Test Steps:
        Create a subscription with capacity
        GET capacity with granularity=HOURLY for 24-hour range
    Expected Results:
        24 data points returned (one per hour)
        Each snapshot is aligned to the hour boundary
        Consistent capacity values across hours

   */

  @TestPlanName("capacity-report-granularity-TC001")
  @Test
  void shouldGetCapacityReportHourlyGranularity() {
    // Given: Create ROSA contract with capacity data for Cores metric
    final String testSku = RandomUtils.generateRandom();
    givenRosaContractIsCreated(testSku, ROSA_CORES_CAPACITY);

    // When: Get capacity report for product=ROSA, metric=Cores
    final OffsetDateTime beginning = clock.startOfCurrentHour().minusHours(23);
    final OffsetDateTime ending = clock.endOfCurrentHour();

    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricId(
            Product.ROSA, orgId, CORES.toString(), beginning, ending, GranularityType.HOURLY, null);

    // Then: Verify response contains correct capacity data
    List<CapacitySnapshotByMetricId> snapshots =
        thenCapacityReportShouldContainSnapshots(capacityReport, 24);
    thenFirstSnapshotShouldStartAt(snapshots, beginning);
    thenAllSnapshotsAreAlignedToHourBoundaries(snapshots);
    thenAllSnapshotsHaveConsistentCapacity(snapshots);
  }

  /*
  capacity-report-granularity-TC002 - Daily Granularity Report

      Description: Verify the daily granularity capacity report
      Setup:
          User authenticated with a valid org_id
      Action: GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}
      Test Steps:
          Create a subscription with capacity
          GET capacity with granularity=DAILY for 7-day range
      Expected Results:
          7 data points (one per day)
          Timestamps aligned to the day start
   */

  @TestPlanName("capacity-report-granularity-TC002")
  @Test
  void shouldGetCapacityReportDailyGranularity() {
    // Given: Create subscriptions with capacity data for RHEL with Sockets metric
    final String testSku = RandomUtils.generateRandom();
    givenPhysicalSubscriptionIsCreated(testSku, RHEL_CORES_CAPACITY, RHEL_SOCKETS_CAPACITY);

    // When: Get capacity report for product=RHEL, metric=Sockets
    final OffsetDateTime beginning = clock.startOfToday().minusDays(6);
    final OffsetDateTime ending = clock.endOfToday();

    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricId(
            Product.RHEL,
            orgId,
            SOCKETS.toString(),
            beginning,
            ending,
            GranularityType.DAILY,
            null);

    // Then: Verify response contains correct capacity data
    List<CapacitySnapshotByMetricId> snapshots =
        thenCapacityReportShouldContainSnapshots(capacityReport, 7);
    thenFirstSnapshotShouldStartAt(snapshots, beginning);
  }

  /*
  capacity-report-granularity-TC003 - Weekly Granularity Report

    Description: Verify capacity report with weekly granularity
    Setup:
        User authenticated with a valid org_id
    Action: GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}
    Test Steps:
        Create a subscription with capacity for the last week only
        GET capacity with granularity=WEEKLY for past 4 weeks
    Expected Results:
        4 data points returned (one per week, Sunday snapshots)
        Each snapshot is aligned to Sunday 00:00 UTC
        Only the last week (most recent) shows capacity = 1
   */

  @TestPlanName("capacity-report-granularity-TC003")
  @Test
  void shouldGetCapacityReportWeeklyGranularity() {
    // Given: Create subscriptions with capacity data for RHEL with Sockets metric
    final String testSku = RandomUtils.generateRandom();
    final int weekRange = 4;

    // Create subscription with start date before the last week to ensure it's active at snapshot
    // time
    final OffsetDateTime subscriptionStart = clock.startOfCurrentWeek().minusWeeks(1).minusDays(3);
    final OffsetDateTime subscriptionEnd = clock.startOfCurrentWeek().minusWeeks(1).plusDays(1);
    givenPhysicalSubscriptionIsCreated(
        testSku, RHEL_CORES_CAPACITY, RHEL_SOCKETS_CAPACITY, subscriptionStart, subscriptionEnd);

    // When: Get capacity report for product=RHEL, metric=Sockets
    final OffsetDateTime beginning = clock.startOfCurrentWeek().minusWeeks(weekRange);
    final OffsetDateTime ending = clock.endOfCurrentWeek().minusWeeks(1);

    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricId(
            Product.RHEL,
            orgId,
            SOCKETS.toString(),
            beginning,
            ending,
            GranularityType.WEEKLY,
            null);

    // Then: Verify response contains correct capacity data
    List<CapacitySnapshotByMetricId> snapshots =
        thenCapacityReportShouldContainSnapshots(capacityReport, weekRange);
    thenFirstSnapshotShouldStartAt(snapshots, beginning);
    thenLastSnapshotShouldEndAt(snapshots, beginning.plusWeeks(weekRange - 1));
  }

  /*
  capacity-report-granularity-TC004 - Monthly Granularity Report

    Description: Verify monthly granularity
    Setup:
        User authenticated with a valid org_id
    Action: GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}
    Test Steps:
        Create a subscription with capacity
        GET capacity with granularity=MONTHLY for a 6-month range
    Expected Results:
        6 data points
        Month boundaries are correctly handled

   */

  @TestPlanName("capacity-report-granularity-TC004")
  @Test
  void shouldGetCapacityReportMonthlyGranularity() {
    // Given: Create subscriptions with capacity data for RHEL with Sockets metric
    final String testSku = RandomUtils.generateRandom();
    final int monthRange = 6;

    // Create subscription with start date before the last month to ensure it's active at snapshot
    // time
    final OffsetDateTime subscriptionStart =
        clock.startOfCurrentMonth().minusMonths(1).minusDays(3);
    final OffsetDateTime subscriptionEnd = clock.startOfCurrentMonth().minusMonths(1).plusDays(1);
    givenPhysicalSubscriptionIsCreated(
        testSku, RHEL_CORES_CAPACITY, RHEL_SOCKETS_CAPACITY, subscriptionStart, subscriptionEnd);

    // When: Get capacity report for product=RHEL, metric=Sockets
    final OffsetDateTime beginning = clock.startOfCurrentMonth().minusMonths(monthRange);
    final OffsetDateTime ending = clock.endOfCurrentMonth().minusMonths(1);

    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricId(
            Product.RHEL,
            orgId,
            SOCKETS.toString(),
            beginning,
            ending,
            GranularityType.MONTHLY,
            null);

    // Then: Verify response contains correct capacity data
    List<CapacitySnapshotByMetricId> snapshots =
        thenCapacityReportShouldContainSnapshots(capacityReport, monthRange);
    thenFirstSnapshotShouldStartAt(snapshots, beginning);
    thenLastSnapshotShouldEndAt(snapshots, beginning.plusMonths(monthRange - 1));
  }

  /*
  capacity-report-granularity-TC005 - Quarterly Granularity Report

    Description: Verify quarterly granularity
    Setup:
        User authenticated with a valid org_id
    Action: GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}
    Test Steps:
        Create a subscription with capacity
        GET capacity with granularity=QUARTERLY for a 1-year range
    Expected Results:
        4 data points
        Quarter boundaries (Jan 1, Apr 1, Jul 1, Oct 1)

   */

  @TestPlanName("capacity-report-granularity-TC005")
  @Test
  void shouldGetCapacityReportQuarterlyGranularity() {
    // Given: Create subscriptions with capacity data for RHEL with Sockets metric
    final String testSku = RandomUtils.generateRandom();
    final int quarterRange = 4;

    // Create subscription with start date before the last quarter to ensure it's active at snapshot
    // time
    final OffsetDateTime subscriptionStart =
        clock.startOfCurrentQuarter().minusMonths(3).minusDays(3);
    final OffsetDateTime subscriptionEnd = clock.startOfCurrentQuarter().minusMonths(3).plusDays(1);
    givenPhysicalSubscriptionIsCreated(
        testSku, RHEL_CORES_CAPACITY, RHEL_SOCKETS_CAPACITY, subscriptionStart, subscriptionEnd);

    // When: Get capacity report for product=RHEL, metric=Sockets
    final OffsetDateTime beginning = clock.startOfCurrentQuarter().minusMonths(9);
    final OffsetDateTime ending = clock.endOfCurrentQuarter().minusMonths(3);

    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricId(
            Product.RHEL,
            orgId,
            SOCKETS.toString(),
            beginning,
            ending,
            GranularityType.QUARTERLY,
            null);

    // Then: Verify response contains correct capacity data
    List<CapacitySnapshotByMetricId> snapshots =
        thenCapacityReportShouldContainSnapshots(capacityReport, quarterRange);
    thenFirstSnapshotShouldStartAt(snapshots, beginning);
    thenLastSnapshotShouldEndAt(snapshots, beginning.plusMonths(9));
  }

  /*
  capacity-report-granularity-TC006 - Yearly Granularity Report

    Description: Verify yearly granularity
    Setup:
        User authenticated with a valid org_id
    Action: GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}
    Test Steps:
        Create a subscription with capacity
        GET capacity with granularity=YEARLY for a 3-year range
    Expected Results:
        3 data points
        Year boundaries aligned

   */

  @TestPlanName("capacity-report-granularity-TC006")
  @Test
  void shouldGetCapacityReportYearlyGranularity() {
    // Given: Create subscriptions with capacity data for RHEL with Sockets metric
    final String testSku = RandomUtils.generateRandom();
    final int yearRange = 3;

    // Create subscription with start date before the last year to ensure it's active at snapshot
    // time
    final OffsetDateTime subscriptionStart = clock.startOfCurrentYear().minusYears(1).minusDays(3);
    final OffsetDateTime subscriptionEnd = clock.startOfCurrentYear().minusYears(1).plusDays(1);
    givenPhysicalSubscriptionIsCreated(
        testSku, RHEL_CORES_CAPACITY, RHEL_SOCKETS_CAPACITY, subscriptionStart, subscriptionEnd);

    // When: Get capacity report for product=RHEL, metric=Sockets
    final OffsetDateTime beginning = clock.startOfCurrentYear().minusYears(3);
    final OffsetDateTime ending = clock.endOfCurrentYear().minusYears(1);

    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricId(
            Product.RHEL,
            orgId,
            SOCKETS.toString(),
            beginning,
            ending,
            GranularityType.YEARLY,
            null);

    // Then: Verify response contains correct capacity data
    List<CapacitySnapshotByMetricId> snapshots =
        thenCapacityReportShouldContainSnapshots(capacityReport, yearRange);
    thenFirstSnapshotShouldStartAt(snapshots, beginning);
    thenLastSnapshotShouldEndAt(snapshots, beginning.plusYears(yearRange - 1));
  }
}
