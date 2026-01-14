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
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CapacityReportGranularityComponentTest extends BaseContractComponentTest {

  private static final double RHEL_CORES_CAPACITY = 4.0;
  private static final double RHEL_SOCKETS_CAPACITY = 1.0;

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
    final OffsetDateTime beginning =
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(6).truncatedTo(ChronoUnit.DAYS);
    final OffsetDateTime ending = OffsetDateTime.now(ZoneOffset.UTC).with(LocalTime.MAX);

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
    assertThat("Capacity report should not be null", capacityReport, notNullValue());
    assertThat("Data array should not be empty", capacityReport.getData(), hasSize(7));

    // Verify each snapshot has required fields
    List<CapacitySnapshotByMetricId> snapshots = capacityReport.getData();
    Assertions.assertNotNull(snapshots);

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

    // Verify first snapshot date matches the beginning timestamp
    assertThat(
        "First snapshot date should equal the beginning timestamp",
        snapshots.get(0).getDate(),
        equalTo(beginning));
  }
}
