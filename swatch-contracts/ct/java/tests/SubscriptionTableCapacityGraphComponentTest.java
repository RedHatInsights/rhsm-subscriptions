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

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThan;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.contract.test.model.GranularityType;
import com.redhat.swatch.contract.test.model.SkuCapacityReportV2;
import domain.Product;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class SubscriptionTableCapacityGraphComponentTest extends BaseContractComponentTest {

  /**
   * SWATCH-15: Subscription table and capacity graph must agree.
   *
   * <p>This test verifies that the subscription table API and capacity graph API return consistent
   * capacity values.
   */
  @TestPlanName("subscription-table-capacity-graph-TC001")
  @ParameterizedTest
  @MethodSource("provideProductsAndMetrics")
  void testSubscriptionTableMatchesCapacityGraph(Product product, MetricId metric) {
    final String sku = RandomUtils.generateRandom();
    final double capacity = 2.0;

    givenSubscriptionStartingYesterday(product, sku, metric, capacity);

    await("Table and graph capacity should match")
        .atMost(2, MINUTES)
        .pollInterval(2, SECONDS)
        .untilAsserted(
            () -> {
              double tableCapacity = getTableCapacity(product, metric);
              double graphCapacity = getTodayGraphCapacity(product, metric);

              assertThat(
                  "Capacity should be greater than 0 to confirm subscription was added",
                  tableCapacity,
                  greaterThan(0.0));

              assertThat(
                  "Table and graph capacity must match for " + metric,
                  tableCapacity,
                  closeTo(graphCapacity, 0.01));
            });
  }

  private static Stream<Arguments> provideProductsAndMetrics() {
    return Stream.of(Arguments.of(Product.RHEL, SOCKETS), Arguments.of(Product.OPENSHIFT, CORES));
  }

  private void givenSubscriptionStartingYesterday(
      Product product, String sku, MetricId metric, double capacity) {
    double coresCapacity = metric.equals(CORES) ? capacity : 0.0;
    double socketsCapacity = metric.equals(SOCKETS) ? capacity : 0.0;

    switch (product) {
      case RHEL -> givenPhysicalSubscriptionIsCreated(sku, coresCapacity, socketsCapacity);
      case OPENSHIFT -> givenOpenshiftSubscriptionIsCreated(sku, coresCapacity, socketsCapacity);
      default -> throw new IllegalArgumentException("Unsupported product: " + product);
    }
  }

  private double getTableCapacity(Product product, MetricId metric) {
    SkuCapacityReportV2 report = service.getSkuCapacityByProductIdForOrg(product, orgId);
    List<String> metricOrder = report.getMeta().getMeasurements();
    int metricIndex = metricOrder.indexOf(metric.toString());
    if (metricIndex < 0) {
      return 0.0;
    }
    return report.getData().stream()
        .mapToDouble(
            sku -> {
              List<Double> measurements = sku.getMeasurements();
              if (measurements != null && metricIndex < measurements.size()) {
                Double value = measurements.get(metricIndex);
                return value != null ? value : 0.0;
              }
              return 0.0;
            })
        .sum();
  }

  private double getTodayGraphCapacity(Product product, MetricId metric) {
    OffsetDateTime now = clock.now();
    var report =
        service.getCapacityReportByMetricId(
            product, orgId, metric.toString(), now, now.plusDays(1), GranularityType.DAILY, null);
    return getCapacityValueFromReport(report);
  }
}
