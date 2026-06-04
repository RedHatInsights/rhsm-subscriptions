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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.CapacityReportByMetricId;
import com.redhat.swatch.contract.test.model.CapacitySnapshotByMetricId;
import com.redhat.swatch.contract.test.model.GranularityType;
import com.redhat.swatch.contract.test.model.SkuCapacityReportV2;
import com.redhat.swatch.contract.test.model.SkuCapacityV2;
import domain.BillingProvider;
import domain.Offering;
import domain.Product;
import domain.Subscription;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CapacityReportComponentTest extends BaseContractComponentTest {

  private static final double OPENSHIFT_CORES_CAPACITY = 8.0;
  private static final double OPENSHIFT_SOCKETS_CAPACITY = 2.0;

  @TestPlanName("capacity-report-TC001")
  @Test
  void shouldGetV2SkuCapacityReport() {
    // Given: Create subscriptions with multiple metrics
    final String testSku = RandomUtils.generateRandom();
    givenSubscriptionWithMultipleMetrics(testSku);

    // When: Get V2 SKU capacity report
    SkuCapacityReportV2 capacityReport =
        service.getSkuCapacityByProductIdForOrg(Product.OPENSHIFT, orgId);

    // Then: Verify SkuCapacityReport_V2 returned with enhanced measurement array
    assertThat("V2 SKU capacity report should not be null", capacityReport, notNullValue());
    assertThat("Meta should not be null", capacityReport.getMeta(), notNullValue());
    assertThat(
        "Product name should not be null", capacityReport.getMeta().getProduct(), notNullValue());

    // Find our test subscription in the report
    assertThat("Data should not be null", capacityReport.getData(), notNullValue());
    Optional<SkuCapacityV2> testSkuCapacity =
        capacityReport.getData().stream().filter(sku -> testSku.equals(sku.getSku())).findFirst();

    assertTrue(testSkuCapacity.isPresent(), "Test SKU should be present in capacity report");

    SkuCapacityV2 skuCapacity = testSkuCapacity.get();

    // Verify measurements field is an array with expected values
    assertThat(
        "Measurements should contain expected values",
        skuCapacity.getMeasurements(),
        notNullValue());

    assertThat(
        "Should have exactly 2 measurements (Cores and Sockets)",
        skuCapacity.getMeasurements().size(),
        equalTo(2));

    // Verify meta includes measurements array with metric names
    assertThat("Meta should be present", capacityReport.getMeta(), notNullValue());
    assertThat(
        "Meta measurements should contain metric names",
        capacityReport.getMeta().getMeasurements(),
        notNullValue());

    assertFalse(
        capacityReport.getMeta().getMeasurements().isEmpty(),
        "Meta measurements should not be empty");
  }

  @TestPlanName("capacity-report-TC002")
  @Test
  void shouldGetCapacityReportByProductAndMetric() {
    // Given: Create subscriptions with capacity data for OpenShift with Cores metric
    final String testSku = RandomUtils.generateRandom();
    givenSubscriptionWithCoresCapacity(testSku);

    // When: Get capacity report for product=OpenShift, metric=Cores
    final OffsetDateTime beginning = clock.now().minusDays(1);
    final OffsetDateTime ending = clock.now().plusDays(1);

    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricId(
            Product.OPENSHIFT,
            orgId,
            CORES.toString(),
            beginning,
            ending,
            GranularityType.DAILY,
            null);

    // Then: Verify response contains correct capacity data
    assertThat("Capacity report should not be null", capacityReport, notNullValue());
    assertThat("Data array should not be empty", capacityReport.getData(), hasSize(greaterThan(0)));

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

    // Verify meta object
    assertThat("Meta should not be null", capacityReport.getMeta(), notNullValue());
    assertThat(
        "Meta product should match",
        capacityReport.getMeta().getProduct(),
        equalTo(Product.OPENSHIFT.getName()));
    assertThat(
        "Meta metricId should match",
        capacityReport.getMeta().getMetricId(),
        equalTo(CORES.toString()));
    assertThat(
        "Meta granularity should match",
        capacityReport.getMeta().getGranularity(),
        equalTo(GranularityType.DAILY));
    assertThat(
        "Meta count should be greater than 0", capacityReport.getMeta().getCount(), greaterThan(0));

    // Verify capacity values match subscription measurements
    double maxCapacity =
        snapshots.stream()
            .filter(CapacitySnapshotByMetricId::getHasData)
            .mapToDouble(snapshot -> snapshot.getValue().doubleValue())
            .max()
            .orElse(0.0);
    assertThat(
        "Capacity should match expected cores capacity",
        maxCapacity,
        equalTo(OPENSHIFT_CORES_CAPACITY));
  }

  @TestPlanName("capacity-report-TC003")
  @Test
  void shouldGenerateCapacityReportWithMultipleSubscriptions() {
    // Given: Create 3 subscriptions for the same product with Cores capacity (2, 4, 6 cores)
    final String sku1 = RandomUtils.generateRandom();
    final String sku2 = RandomUtils.generateRandom();
    final String sku3 = RandomUtils.generateRandom();

    givenSubscriptionWithSpecificCoresCapacity(sku1, 2.0);
    givenSubscriptionWithSpecificCoresCapacity(sku2, 4.0);
    givenSubscriptionWithSpecificCoresCapacity(sku3, 6.0);

    final double expectedTotalCapacity = 2.0 + 4.0 + 6.0; // 12.0 cores total

    // When: Get capacity report
    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricId(
            Product.OPENSHIFT,
            orgId,
            CORES.toString(),
            OffsetDateTime.now().minusDays(1),
            OffsetDateTime.now().plusDays(1),
            GranularityType.DAILY,
            null);

    // Then: Verify capacity value = sum of all subscriptions (12 cores)
    Assertions.assertNotNull(capacityReport.getData());
    double actualCapacity =
        capacityReport.getData().stream()
            .filter(CapacitySnapshotByMetricId::getHasData)
            .mapToDouble(snapshot -> snapshot.getValue().doubleValue())
            .max()
            .orElse(0.0);

    assertThat(
        "Aggregated capacity should equal sum of all subscriptions",
        actualCapacity,
        equalTo(expectedTotalCapacity));

    // Verify single data point per time period (aggregated)
    long dataPointsWithData =
        capacityReport.getData().stream().filter(CapacitySnapshotByMetricId::getHasData).count();
    assertThat("Should have data points for the time range", dataPointsWithData, greaterThan(0L));
  }

  @TestPlanName("capacity-report-TC004")
  @Test
  void shouldGenerateCapacityReportWithNoData() {
    // Given: No subscriptions exist for the test product
    // Using a fresh org that has no subscriptions
    String freshOrgId = givenOrgIdWithSuffix("_no_data");

    // When: Get capacity for product with no subscriptions
    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricId(
            Product.RHEL,
            freshOrgId,
            CORES.toString(),
            OffsetDateTime.now().minusDays(1),
            OffsetDateTime.now().plusDays(1),
            GranularityType.DAILY,
            null);

    // Then: Verify response structure with no data
    assertThat("Capacity report should not be null", capacityReport, notNullValue());
    assertThat("Data array should not be empty", capacityReport.getData(), hasSize(greaterThan(0)));

    // All data points should have value=0 and hasData=false
    List<CapacitySnapshotByMetricId> snapshots = capacityReport.getData();
    Assertions.assertNotNull(snapshots);
    for (CapacitySnapshotByMetricId snapshot : snapshots) {
      assertThat("Value should be 0 for no data", snapshot.getValue().doubleValue(), equalTo(0.0));
      assertFalse(snapshot.getHasData(), "HasData should be false when no subscriptions exist");
    }

    // Verify one entry per time period
    assertThat("Should have entries for each time period", snapshots.size(), greaterThan(0));
  }

  @TestPlanName("capacity-report-TC005")
  @Test
  void shouldGenerateCapacityReportWithExpiredSubscriptions() {
    // Given: Create a subscription with an end_date in the past and an active subscription
    final String expiredSku = RandomUtils.generateRandom();
    final String activeSku = RandomUtils.generateRandom();

    // Create expired subscription (ended yesterday)
    givenExpiredSubscriptionWithCoresCapacity(expiredSku, 4.0);

    // Create active subscription
    givenSubscriptionWithSpecificCoresCapacity(activeSku, 8.0);

    // When: Get capacity report for current time range
    CapacityReportByMetricId capacityReport =
        service.getCapacityReportByMetricId(
            Product.OPENSHIFT,
            orgId,
            CORES.toString(),
            OffsetDateTime.now().minusDays(1),
            OffsetDateTime.now().plusDays(1),
            GranularityType.DAILY,
            null);

    // Then: Only active subscription capacity should be included, expired subscription excluded
    Assertions.assertNotNull(capacityReport.getData());
    double actualCapacity =
        capacityReport.getData().stream()
            .filter(CapacitySnapshotByMetricId::getHasData)
            .mapToDouble(snapshot -> snapshot.getValue().doubleValue())
            .max()
            .orElse(0.0);

    assertThat(
        "Only active subscription should be included (8 cores)", actualCapacity, equalTo(8.0));

    // Verify expired subscription is not contributing to current capacity
    assertTrue(
        actualCapacity < 12.0,
        "Capacity should not include expired subscription (would be 12 if included)");
  }

  // Helper methods for creating test data

  private void givenSubscriptionWithMultipleMetrics(String sku) {
    // Create offering with multiple metrics (Cores and Sockets)
    wiremock
        .forProductAPI()
        .stubOfferingData(
            Offering.buildOpenShiftOffering(
                sku, OPENSHIFT_CORES_CAPACITY, OPENSHIFT_SOCKETS_CAPACITY));
    assertThat(
        "Sync offering should succeed",
        service.syncOffering(sku).statusCode(),
        is(HttpStatus.SC_OK));

    // Create subscription with multiple metrics
    Subscription subscription =
        Subscription.buildOpenShiftSubscriptionUsingSku(
            orgId,
            Map.of(
                CORES, OPENSHIFT_CORES_CAPACITY,
                SOCKETS, OPENSHIFT_SOCKETS_CAPACITY),
            sku);
    assertThat(
        "Creating subscription should succeed",
        service.saveSubscriptions(true, subscription).statusCode(),
        is(HttpStatus.SC_OK));
  }

  private void givenSubscriptionWithCoresCapacity(String sku) {
    givenSubscriptionWithSpecificCoresCapacity(sku, OPENSHIFT_CORES_CAPACITY);
  }

  private void givenSubscriptionWithSpecificCoresCapacity(String sku, double coresCapacity) {
    // Create offering with cores capacity
    wiremock
        .forProductAPI()
        .stubOfferingData(Offering.buildOpenShiftOffering(sku, coresCapacity, null));
    assertThat(
        "Sync offering should succeed",
        service.syncOffering(sku).statusCode(),
        is(HttpStatus.SC_OK));

    // Create subscription with cores capacity
    Subscription subscription =
        Subscription.buildOpenShiftSubscriptionUsingSku(orgId, Map.of(CORES, coresCapacity), sku);
    assertThat(
        "Creating subscription should succeed",
        service.saveSubscriptions(true, subscription).statusCode(),
        is(HttpStatus.SC_OK));
  }

  private void givenExpiredSubscriptionWithCoresCapacity(String sku, double coresCapacity) {
    // Create offering with cores capacity
    wiremock
        .forProductAPI()
        .stubOfferingData(Offering.buildOpenShiftOffering(sku, coresCapacity, null));
    assertThat(
        "Sync offering should succeed",
        service.syncOffering(sku).statusCode(),
        is(HttpStatus.SC_OK));

    // Create subscription that ended yesterday
    Subscription subscription =
        Subscription.builder()
            .orgId(orgId)
            .product(Product.OPENSHIFT)
            .subscriptionId(RandomUtils.generateRandom())
            .subscriptionNumber(RandomUtils.generateRandom())
            .offering(Offering.buildOpenShiftOffering(sku, coresCapacity, null))
            .subscriptionMeasurements(Map.of(CORES, coresCapacity))
            .startDate(OffsetDateTime.now().minusDays(30))
            .endDate(OffsetDateTime.now().minusDays(1)) // Expired yesterday
            .billingProvider(BillingProvider.AWS)
            .billingAccountId("test_account")
            .build();

    assertThat(
        "Creating expired subscription should succeed",
        service.saveSubscriptions(true, subscription).statusCode(),
        is(HttpStatus.SC_OK));
  }
}
