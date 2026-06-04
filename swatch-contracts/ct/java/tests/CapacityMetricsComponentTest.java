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
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.contract.test.model.ReportCategory;
import domain.Offering;
import domain.Product;
import domain.Subscription;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CapacityMetricsComponentTest extends BaseContractComponentTest {

  private OffsetDateTime beginning;
  private OffsetDateTime ending;

  @BeforeEach
  void setUp() {
    super.setUp();
    beginning = clock.now().minusDays(1);
    ending = clock.now().plusDays(1);
  }

  @TestPlanName("capacity-metrics-TC001")
  @Test
  void shouldCalculateCoresMetricCapacity() {
    // Given: An offering with cores=8 and a subscription with quantity=5
    final String sku = RandomUtils.generateRandom();
    final double offeringCores = 8.0;
    final int quantity = 5;

    givenRhelOfferingAndSubscriptionWithCores(sku, offeringCores, quantity);

    // When: Reconcile capacity for the offering
    Response response = service.forceReconcileOffering(sku);
    assertEquals(HttpStatus.SC_OK, response.statusCode(), "Force reconcile should succeed");

    // Then: PHYSICAL Cores measurement should equal 40 (8 * 5)
    double expectedCapacity = offeringCores * quantity;
    thenCapacityIsEqualTo(
        expectedCapacity,
        () -> getDailyPhysicalCapacityByMetricId(CORES),
        "Physical cores capacity should be calculated correctly");
  }

  @TestPlanName("capacity-metrics-TC002")
  @Test
  void shouldCalculateSocketsMetricCapacity() {
    // Given: An offering with sockets=4 and a subscription with quantity=10
    final String sku = RandomUtils.generateRandom();
    final double offeringSockets = 4.0;
    final int quantity = 10;

    givenRhelOfferingAndSubscriptionWithSockets(sku, offeringSockets, quantity);

    // When: Reconcile capacity for the offering
    Response response = service.forceReconcileOffering(sku);
    assertEquals(HttpStatus.SC_OK, response.statusCode(), "Force reconcile should succeed");

    // Then: PHYSICAL Sockets measurement should equal 40 (4 * 10)
    double expectedCapacity = offeringSockets * quantity;
    thenCapacityIsEqualTo(
        expectedCapacity,
        () -> getDailyPhysicalCapacityByMetricId(SOCKETS),
        "Physical sockets capacity should be calculated correctly");
  }

  @TestPlanName("capacity-metrics-TC003")
  @Test
  void shouldCalculateHypervisorCoresCapacity() {
    // Given: An offering with hypervisorCores=16 and a subscription with quantity=2
    final String sku = RandomUtils.generateRandom();
    final double offeringHypervisorCores = 16.0;
    final int quantity = 2;

    givenRhelHypervisorOfferingAndSubscriptionWithCores(sku, offeringHypervisorCores, quantity);

    // When: Reconcile capacity for the offering
    Response response = service.forceReconcileOffering(sku);
    assertEquals(HttpStatus.SC_OK, response.statusCode(), "Force reconcile should succeed");

    // Then: HYPERVISOR Cores measurement should equal 32 (16 * 2)
    double expectedCapacity = offeringHypervisorCores * quantity;
    thenCapacityIsEqualTo(
        expectedCapacity,
        () -> getDailyHypervisorCapacityByMetricId(CORES),
        "Hypervisor cores capacity should be calculated correctly");
  }

  @TestPlanName("capacity-metrics-TC004")
  @Test
  void shouldCalculateHypervisorSocketsCapacity() {
    // Given: An offering with hypervisorSockets=2 and a subscription with quantity=20
    final String sku = RandomUtils.generateRandom();
    final double offeringHypervisorSockets = 2.0;
    final int quantity = 20;

    givenRhelHypervisorOfferingAndSubscriptionWithSockets(sku, offeringHypervisorSockets, quantity);

    // When: Reconcile capacity for the offering
    Response response = service.forceReconcileOffering(sku);
    assertEquals(HttpStatus.SC_OK, response.statusCode(), "Force reconcile should succeed");

    // Then: HYPERVISOR Sockets measurement should equal 40 (2 * 20)
    double expectedCapacity = offeringHypervisorSockets * quantity;
    thenCapacityIsEqualTo(
        expectedCapacity,
        () -> getDailyHypervisorCapacityByMetricId(SOCKETS),
        "Hypervisor sockets capacity should be calculated correctly");
  }

  /** Stubs and syncs an offering with the product API. */
  private void givenOfferingIsSynced(Offering offering) {
    wiremock.forProductAPI().stubOfferingData(offering);
    Response syncResponse = service.syncOffering(offering.getSku());
    assertThat("Sync offering should succeed", syncResponse.statusCode(), is(HttpStatus.SC_OK));
  }

  /** Creates a subscription with a specific metric and quantity. */
  private Subscription givenSubscriptionWithMetricAndQuantity(
      String sku, MetricId metricId, double metricValue, int quantity) {
    Subscription subscription =
        Subscription.buildRhelSubscriptionUsingSku(orgId, Map.of(metricId, metricValue), sku)
            .toBuilder()
            .quantity(quantity)
            .build();
    Response saveResponse = service.saveSubscriptions(true, subscription);
    assertThat(
        "Creating subscription should succeed", saveResponse.statusCode(), is(HttpStatus.SC_OK));
    return subscription;
  }

  private void givenRhelOfferingAndSubscriptionWithCores(String sku, double cores, int quantity) {
    givenOfferingIsSynced(Offering.buildRhelOffering(sku, cores, null));
    givenSubscriptionWithMetricAndQuantity(sku, CORES, cores, quantity);
  }

  private void givenRhelOfferingAndSubscriptionWithSockets(
      String sku, double sockets, int quantity) {
    givenOfferingIsSynced(Offering.buildRhelOffering(sku, null, sockets));
    givenSubscriptionWithMetricAndQuantity(sku, SOCKETS, sockets, quantity);
  }

  private void givenRhelHypervisorOfferingAndSubscriptionWithCores(
      String sku, double hypervisorCores, int quantity) {
    givenOfferingIsSynced(Offering.buildRhelHypervisorOffering(sku, hypervisorCores, null));
    givenSubscriptionWithMetricAndQuantity(sku, CORES, hypervisorCores, quantity);
  }

  private void givenRhelHypervisorOfferingAndSubscriptionWithSockets(
      String sku, double hypervisorSockets, int quantity) {
    givenOfferingIsSynced(Offering.buildRhelHypervisorOffering(sku, null, hypervisorSockets));
    givenSubscriptionWithMetricAndQuantity(sku, SOCKETS, hypervisorSockets, quantity);
  }

  private void thenCapacityIsEqualTo(
      double expectedCapacity, Supplier<Double> capacitySupplier, String message) {
    AwaitilityUtils.until(
        capacitySupplier,
        capacity -> Math.abs(capacity - expectedCapacity) < 0.01,
        AwaitilitySettings.defaults().timeoutMessage(message));
  }

  private double getDailyHypervisorCapacityByMetricId(MetricId metricId) {
    return getDailyCapacityByCategoryAndMetric(
        Product.RHEL, orgId, beginning, ending, ReportCategory.HYPERVISOR, metricId);
  }

  private double getDailyPhysicalCapacityByMetricId(MetricId metricId) {
    return getDailyCapacityByCategoryAndMetric(
        Product.RHEL, orgId, beginning, ending, ReportCategory.PHYSICAL, metricId);
  }
}
