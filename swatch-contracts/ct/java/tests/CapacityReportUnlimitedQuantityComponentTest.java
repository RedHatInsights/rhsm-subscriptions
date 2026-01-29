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

import static com.redhat.swatch.component.tests.utils.RandomUtils.generateRandom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.contract.test.model.CapacityReportByMetricId;
import com.redhat.swatch.contract.test.model.CapacitySnapshotByMetricId;
import com.redhat.swatch.contract.test.model.GranularityType;
import domain.Offering;
import domain.Product;
import domain.Subscription;
import java.time.OffsetDateTime;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class CapacityReportUnlimitedQuantityComponentTest extends BaseContractComponentTest {

  @Disabled(value = "To be fixed by SWATCH-4518")
  @TestPlanName("capacity-report-unlimited-quantity-TC001")
  @Test
  void shouldReportHasInfiniteQuantityForUnlimitedSubscription() {
    // Given: A subscription with has_unlimited_usage=true for RHEL
    givenUnlimitedSubscriptionIsCreated();

    // When: Get capacity report by metric
    var capacityReport = whenGetCapacityReport();

    // Then: Report should contain snapshots
    assertNotNull(capacityReport, "Capacity report should not be null");
    assertNotNull(capacityReport.getData(), "Data array should not be null");
    assertFalse(capacityReport.getData().isEmpty(), "Data array should not be empty");

    // And: At least one snapshot should have hasInfiniteQuantity=true
    boolean hasInfiniteQuantityFound =
        capacityReport.getData().stream()
            .anyMatch(
                snapshot ->
                    snapshot.getHasInfiniteQuantity() != null && snapshot.getHasInfiniteQuantity());

    assertTrue(
        hasInfiniteQuantityFound,
        "At least one snapshot should have hasInfiniteQuantity=true for unlimited subscription");

    // And: Verify the data comes from the subscription we created
    CapacitySnapshotByMetricId todaySnapshot =
        capacityReport.getData().get(capacityReport.getData().size() - 1);
    assertTrue(isInfiniteQuantity(todaySnapshot));
  }

  @Disabled(value = "To be fixed by SWATCH-4518")
  @TestPlanName("capacity-report-unlimited-quantity-TC002")
  @Test
  void shouldReportHasInfiniteQuantityForMixedSubscriptions() {
    // Given: An unlimited subscription active in a limited time range
    givenUnlimitedSubscriptionWithCustomDates(clock.now().minusDays(5), clock.now().minusDays(2));

    // And: Regular subscriptions with defined capacity (active throughout the entire period)
    Subscription regularSub1 = givenRhelSubscriptionWithSpecificCoresCapacity(4.0);
    Subscription regularSub2 = givenRhelSubscriptionWithSpecificCoresCapacity(8.0);

    // When: Get capacity report covering both inside and outside the unlimited subscription period
    CapacityReportByMetricId capacityReport =
        whenGetCapacityReport(clock.now().minusDays(6), clock.now().plusDays(1));

    // Then: Report should contain snapshots
    assertNotNull(capacityReport);
    assertNotNull(capacityReport.getData());
    assertFalse(capacityReport.getData().isEmpty());

    // And: Snapshots during unlimited subscription period should have hasInfiniteQuantity=true
    boolean hasInfiniteQuantityFoundInRange =
        capacityReport.getData().stream()
            .filter(
                snapshot ->
                    !snapshot.getDate().isBefore(clock.now().minusDays(5))
                        && !snapshot.getDate().isAfter(clock.now().minusDays(2)))
            .anyMatch(CapacityReportUnlimitedQuantityComponentTest::isInfiniteQuantity);

    assertTrue(
        hasInfiniteQuantityFoundInRange,
        "Snapshots within unlimited subscription period should have hasInfiniteQuantity=true");

    // And: Snapshots OUTSIDE unlimited subscription period should have hasInfiniteQuantity=false
    boolean allOutsideRangeAreFalse =
        capacityReport.getData().stream()
            .filter(
                snapshot ->
                    snapshot.getDate().isBefore(clock.now().minusDays(5))
                        || snapshot.getDate().isAfter(clock.now().minusDays(2)))
            .allMatch(
                snapshot ->
                    snapshot.getHasInfiniteQuantity() == null
                        || !snapshot.getHasInfiniteQuantity());

    assertTrue(
        allOutsideRangeAreFalse,
        "Snapshots outside unlimited subscription period should have hasInfiniteQuantity=false");

    // And: Verify capacity value includes regular subscriptions (which are active the whole time)
    CapacitySnapshotByMetricId todaySnapshot =
        capacityReport.getData().get(capacityReport.getData().size() - 1);
    assertNotNull(todaySnapshot.getValue(), "Capacity value should not be null");
    assertTrue(
        todaySnapshot.getValue()
            >= regularSub1.getOffering().getCores() + regularSub2.getOffering().getCores(),
        "Capacity should include regular subscriptions");

    // And: Today's snapshot should have hasInfiniteQuantity=false (unlimited sub ended 2 days ago)
    assertFalse(isInfiniteQuantity(todaySnapshot));
  }

  private Subscription givenRhelSubscriptionWithSpecificCoresCapacity(double coresCapacity) {
    String sku = generateRandom();
    // Create offering with cores capacity for RHEL
    wiremock.forProductAPI().stubOfferingData(Offering.buildRhelOffering(sku, coresCapacity, null));
    assertEquals(
        HttpStatus.SC_OK,
        service.syncOffering(sku).statusCode(),
        "Sync RHEL offering should succeed");

    // Create subscription with cores capacity for RHEL
    Subscription subscription =
        Subscription.buildRhelSubscriptionUsingSku(orgId, Map.of(CORES, coresCapacity), sku);
    assertEquals(
        HttpStatus.SC_OK,
        service.saveSubscriptions(true, subscription).statusCode(),
        "Creating RHEL subscription should succeed");

    return subscription;
  }

  private void givenUnlimitedSubscriptionIsCreated() {
    givenUnlimitedSubscriptionWithCustomDates(clock.now().minusDays(2), clock.now().plusDays(2));
  }

  private void givenUnlimitedSubscriptionWithCustomDates(
      OffsetDateTime startDate, OffsetDateTime endDate) {
    String sku = generateRandom();
    Offering offering = Offering.buildRhelUnlimitedOffering(sku);

    // Stub the offering data in external product API
    wiremock.forProductAPI().stubOfferingData(offering);

    // Sync offering to persist it
    assertEquals(
        HttpStatus.SC_OK,
        service.syncOffering(sku).statusCode(),
        "Sync unlimited offering should succeed");

    // Create unlimited subscription (no measurements for unlimited offerings)
    Subscription subscription =
        Subscription.builder()
            .orgId(orgId)
            .product(Product.RHEL)
            .subscriptionId(generateRandom())
            .subscriptionNumber(generateRandom())
            .offering(offering)
            .subscriptionMeasurements(Map.of())
            .startDate(startDate)
            .endDate(endDate)
            .quantity(1)
            .build();

    assertEquals(
        HttpStatus.SC_OK,
        service.saveSubscriptions(true, subscription).statusCode(),
        "Creating unlimited subscription should succeed");
  }

  private CapacityReportByMetricId whenGetCapacityReport() {
    OffsetDateTime beginning = clock.now().minusDays(1);
    OffsetDateTime ending = clock.now().plusDays(1);
    return whenGetCapacityReport(beginning, ending);
  }

  private CapacityReportByMetricId whenGetCapacityReport(
      OffsetDateTime beginning, OffsetDateTime ending) {
    return service.getCapacityReportByMetricId(
        Product.RHEL, orgId, CORES.toString(), beginning, ending, GranularityType.DAILY, null);
  }

  private static boolean isInfiniteQuantity(CapacitySnapshotByMetricId snapshot) {
    return snapshot.getHasInfiniteQuantity() != null && snapshot.getHasInfiniteQuantity();
  }
}
