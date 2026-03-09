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

import static com.redhat.swatch.component.tests.utils.Topics.UTILIZATION;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.utilization.test.model.Measurement;
import com.redhat.swatch.utilization.test.model.UtilizationSummary;
import com.redhat.swatch.utilization.test.model.UtilizationSummary.Granularity;
import domain.Product;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Component tests for utilization threshold configuration. */
public class UtilizationConfigComponentTest extends BaseUtilizationComponentTest {

  private static final double BASELINE_CAPACITY = 100.0;
  private static final double USAGE_EXCEEDING_DEFAULT_THRESHOLD = 120.0;

  private UtilizationSummary utilizationSummary;
  private double initialOverUsageCount;

  @BeforeAll
  static void enableSendNotificationsFeatureFlag() {
    unleash.enableSendNotificationsFlag();
  }

  @AfterAll
  static void disableSendNotificationsFeatureFlag() {
    unleash.disableSendNotificationsFlag();
  }

  @TestPlanName("utilization-config-TC001")
  @Test
  void shouldNotSendNotification_whenProductHasNegativeThreshold() {
    // Given: An organization has capacity for metric A of product B
    // The utilization threshold is set to a negative value for that metric (BASILISK)
    givenUtilizationSummaryForProduct(Product.BASILISK);
    givenMeasurementWithCapacity(USAGE_EXCEEDING_DEFAULT_THRESHOLD, BASELINE_CAPACITY, false);

    // When: Generate enough usage to trigger overusage with default threshold
    // Trigger utilization calculation process
    whenUtilizationEventIsReceived();

    // Then: No notification event created for negative threshold products
    thenOverUsageCounterShouldNotChange();
  }

  private void givenUtilizationSummaryForProduct(Product product) {
    utilizationSummary =
        new UtilizationSummary()
            .withOrgId(orgId)
            .withProductId(product.getName())
            .withGranularity(Granularity.HOURLY)
            .withBillingAccountId(RandomUtils.generateRandom())
            .withMeasurements(new ArrayList<>());

    initialOverUsageCount =
        service.getMetricByTags(OVER_USAGE_METRIC, metricIdTag(product.getFirstMetricId()));
  }

  private void givenMeasurementWithCapacity(
      double currentTotal, Double capacity, boolean unlimited) {
    Product product = Product.fromString(utilizationSummary.getProductId());
    utilizationSummary
        .getMeasurements()
        .add(
            new Measurement()
                .withMetricId(product.getFirstMetric().getId())
                .withCurrentTotal(currentTotal)
                .withCapacity(capacity)
                .withUnlimited(unlimited));
  }

  private void whenUtilizationEventIsReceived() {
    kafkaBridge.produceKafkaMessage(UTILIZATION, utilizationSummary);
  }

  private void thenOverUsageCounterShouldNotChange() {
    Product product = Product.fromString(utilizationSummary.getProductId());
    AwaitilityUtils.untilAsserted(
        () -> {
          double currentCount =
              service.getMetricByTags(OVER_USAGE_METRIC, metricIdTag(product.getFirstMetricId()));
          Assertions.assertEquals(
              initialOverUsageCount, currentCount, "Over-usage counter should not change");
        });
  }
}
