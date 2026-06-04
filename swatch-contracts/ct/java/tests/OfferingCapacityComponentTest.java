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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.contract.test.model.SkuCapacityReportV2;
import com.redhat.swatch.contract.test.model.SkuCapacitySubscription;
import com.redhat.swatch.contract.test.model.SkuCapacityV2;
import domain.Offering;
import domain.Product;
import domain.Subscription;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

public class OfferingCapacityComponentTest extends BaseContractComponentTest {

  private static final double OPENSHIFT_CORES_CAPACITY = 8.0;
  private static final double OPENSHIFT_SOCKETS_CAPACITY = 2.0;
  private static final int SUBSCRIPTION_QUANTITY = 3;

  @TestPlanName("offering-capacity-TC001")
  @Test
  void shouldCalculateCapacityForMeteredOfferings() {
    // Given: A metered offering with multiple metrics (Cores and Sockets) and subscription with
    // quantity
    final String sku = RandomUtils.generateRandom();
    Offering offering =
        Offering.buildOpenShiftOffering(sku, OPENSHIFT_CORES_CAPACITY, OPENSHIFT_SOCKETS_CAPACITY);
    Subscription createdSubscription =
        givenSubscriptionIsCreated(sku, offering, Product.OPENSHIFT, SUBSCRIPTION_QUANTITY);

    // When: Querying capacity report for the SKU
    SkuCapacityReportV2 report = service.getSkuCapacityByProductIdForOrg(Product.OPENSHIFT, orgId);
    Optional<SkuCapacityV2> skuCapacity =
        report.getData().stream().filter(d -> sku.equals(d.getSku())).findFirst();

    // Then: Capacity should reflect subscription quantity multiplied by offering metrics
    assertTrue(skuCapacity.isPresent(), "SKU capacity should be present in the report");

    SkuCapacityV2 capacity = skuCapacity.get();
    assertThat("SKU should match", capacity.getSku(), equalTo(sku));
    assertThat(
        "Quantity should match subscription quantity",
        capacity.getQuantity(),
        equalTo(SUBSCRIPTION_QUANTITY));

    // And: Multiple metric dimensions (Cores, Sockets) should be calculated correctly
    assertThat("Measurements should not be null", capacity.getMeasurements(), notNullValue());
    assertThat(
        "Should have measurements for Cores and Sockets", capacity.getMeasurements(), hasSize(2));

    // And: Verify measurements match expected values at correct indices (defined by meta)
    assertThat("Report meta should not be null", report.getMeta(), notNullValue());
    List<String> metricOrder = report.getMeta().getMeasurements();
    List<Double> measurements = capacity.getMeasurements();

    int coresIndex = metricOrder.indexOf("Cores");
    int socketsIndex = metricOrder.indexOf("Sockets");

    double expectedCoresCapacity = SUBSCRIPTION_QUANTITY * OPENSHIFT_CORES_CAPACITY;
    double expectedSocketsCapacity = SUBSCRIPTION_QUANTITY * OPENSHIFT_SOCKETS_CAPACITY;

    assertTrue(
        coresIndex >= 0,
        String.format("Cores metric not found in meta.measurements: %s", metricOrder));
    assertTrue(
        socketsIndex >= 0,
        String.format("Sockets metric not found in meta.measurements: %s", metricOrder));

    assertThat(
        String.format(
            "Cores capacity at index %d should be %d × %.1f = %.1f",
            coresIndex, SUBSCRIPTION_QUANTITY, OPENSHIFT_CORES_CAPACITY, expectedCoresCapacity),
        measurements.get(coresIndex),
        equalTo(expectedCoresCapacity));

    assertThat(
        String.format(
            "Sockets capacity at index %d should be %d × %.1f = %.1f",
            socketsIndex,
            SUBSCRIPTION_QUANTITY,
            OPENSHIFT_SOCKETS_CAPACITY,
            expectedSocketsCapacity),
        measurements.get(socketsIndex),
        equalTo(expectedSocketsCapacity));

    // And: API response should contain exactly one subscription (the one we created)
    assertThat("Subscriptions should not be null", capacity.getSubscriptions(), notNullValue());
    assertThat(
        "Should have exactly one subscription", capacity.getSubscriptions().size(), equalTo(1));

    SkuCapacitySubscription returnedSubscription = capacity.getSubscriptions().get(0);
    assertThat(
        "Subscription ID should match the created subscription",
        returnedSubscription.getId(),
        equalTo(createdSubscription.getSubscriptionId()));
    assertThat(
        "Subscription number should match the created subscription",
        returnedSubscription.getNumber(),
        equalTo(createdSubscription.getSubscriptionNumber()));
  }

  @TestPlanName("offering-capacity-TC002")
  @Test
  void shouldHandleUnlimitedCapacityOffering() {
    // Given: An unlimited capacity offering (metered="n", has_unlimited_usage=True) and
    // subscription
    final String sku = RandomUtils.generateRandom();
    Offering unlimitedOffering = Offering.buildRhelUnlimitedOffering(sku);
    Subscription createdSubscription =
        givenSubscriptionIsCreated(sku, unlimitedOffering, Product.RHEL, 1);

    // When: Querying capacity report for the unlimited offering SKU
    Optional<SkuCapacityV2> skuCapacity =
        service.getSkuCapacityByProductIdForOrgAndSku(Product.RHEL, orgId, sku);

    // Then: API response should indicate unlimited capacity status
    assertTrue(
        skuCapacity.isPresent(), "SKU capacity should be present in the report for unlimited SKU");

    SkuCapacityV2 capacity = skuCapacity.get();

    // And: Unlimited capacity flag should be set
    assertThat(
        "hasInfiniteQuantity should not be null",
        capacity.getHasInfiniteQuantity(),
        notNullValue());
    assertTrue(
        capacity.getHasInfiniteQuantity(),
        "hasInfiniteQuantity flag should be true for unlimited offering");

    // And: Subscription should be correctly linked to unlimited offering
    assertThat("SKU should match", capacity.getSku(), equalTo(sku));
    assertThat("Subscriptions should not be null", capacity.getSubscriptions(), notNullValue());
    assertThat(
        "Should have exactly one subscription", capacity.getSubscriptions().size(), equalTo(1));

    SkuCapacitySubscription returnedSubscription = capacity.getSubscriptions().get(0);
    assertThat(
        "Subscription ID should match the created subscription",
        returnedSubscription.getId(),
        equalTo(createdSubscription.getSubscriptionId()));
    assertThat(
        "Subscription number should match the created subscription",
        returnedSubscription.getNumber(),
        equalTo(createdSubscription.getSubscriptionNumber()));

    // And: Verify the product name matches the unlimited offering description
    assertThat(
        "Product name should match offering description",
        capacity.getProductName(),
        equalTo("Test offering with unlimited usage"));
  }

  /**
   * Helper method to create an offering with subscription for capacity testing.
   *
   * @param sku the SKU to create
   * @param offering the offering to stub
   * @param product the product for the subscription
   * @param quantity the subscription quantity
   * @return the created subscription
   */
  private Subscription givenSubscriptionIsCreated(
      String sku, Offering offering, Product product, Integer quantity) {
    // Stub the offering data in external product API
    wiremock.forProductAPI().stubOfferingData(offering);

    // Sync offering to persist it
    assertThat(
        "Sync offering should succeed",
        service.syncOffering(sku).statusCode(),
        is(HttpStatus.SC_OK));

    // Create subscription measurements map based on offering
    Map<MetricId, Double> measurements =
        Map.of(
            MetricIdUtils.getCores(),
            offering.getCores() != null ? offering.getCores().doubleValue() : 0.0,
            MetricIdUtils.getSockets(),
            offering.getSockets() != null ? offering.getSockets().doubleValue() : 0.0);

    // Create subscription with the specified quantity
    Subscription subscription =
        Subscription.builder()
            .orgId(orgId)
            .product(product)
            .subscriptionId(RandomUtils.generateRandom())
            .subscriptionNumber(RandomUtils.generateRandom())
            .offering(offering)
            .subscriptionMeasurements(measurements)
            .startDate(OffsetDateTime.now().minusDays(1))
            .endDate(OffsetDateTime.now().plusDays(1))
            .quantity(quantity)
            .build();

    assertThat(
        "Creating subscription should succeed",
        service.saveSubscriptions(true, subscription).statusCode(),
        is(HttpStatus.SC_OK));

    return subscription;
  }
}
