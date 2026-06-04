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

import static api.MessageValidators.matchesOverageNotification;
import static com.redhat.swatch.component.tests.utils.AwaitilityUtils.untilAsserted;
import static com.redhat.swatch.component.tests.utils.Topics.NOTIFICATIONS;
import static com.redhat.swatch.component.tests.utils.Topics.UTILIZATION;
import static com.redhat.swatch.configuration.util.MetricIdUtils.getCores;
import static com.redhat.swatch.configuration.util.MetricIdUtils.getInstanceHours;
import static com.redhat.swatch.configuration.util.MetricIdUtils.getSockets;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.utilization.test.model.UtilizationSummary;
import com.redhat.swatch.utilization.test.model.UtilizationSummary.Granularity;
import domain.Product;
import java.time.Duration;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class UtilizationMultiComponentTest extends BaseUtilizationComponentTest {

  // Test data constants - aligned with CUSTOMER_OVER_USAGE_DEFAULT_THRESHOLD_PERCENT=5.0
  private static final double CAPACITY = 100.0;
  private static final double USAGE_BELOW_THRESHOLD = 103.0; // 3% over capacity (< 5% threshold)
  private static final double USAGE_ABOVE_THRESHOLD = 110.0; // 10% over capacity (> 5% threshold)

  // Test timing constants
  private static final Duration MESSAGE_PROCESSING_DELAY = Duration.ofSeconds(2);

  @BeforeAll
  static void enableSendNotificationsFeatureFlag() {
    unleash.enableSendNotificationsFlag();
  }

  @AfterAll
  static void disableSendNotificationsFeatureFlag() {
    unleash.disableSendNotificationsFlag();
  }

  /** Verify overusage record created only for product exceeding threshold. */
  @Test
  @TestPlanName("utilization-multi-TC001")
  void shouldCreateOverusageRecordOnlyForProductExceedingThreshold() {
    // Given: Two products with different usage levels
    UtilizationSummary rosaProduct = givenUtilizationSummary(Product.ROSA, Granularity.HOURLY);
    givenMeasurement(rosaProduct, getCores(), USAGE_BELOW_THRESHOLD, CAPACITY, false);

    UtilizationSummary rhelProduct = givenUtilizationSummary(Product.RHEL, Granularity.DAILY);
    givenMeasurement(rhelProduct, getSockets(), USAGE_ABOVE_THRESHOLD, CAPACITY, false);

    double initialRosaCount = overUsageMetricCount(getCores());
    double initialRhelCount = overUsageMetricCount(getSockets());

    // When: Utilization events are processed
    whenUtilizationEventIsReceived(rosaProduct);
    whenUtilizationEventIsReceived(rhelProduct);

    // Then: Only product exceeding threshold triggers notification
    thenOverUsageCounterShouldBeIncremented(initialRhelCount, getSockets());
    thenOverUsageCounterShouldNotChange(initialRosaCount, getCores());
    thenNotificationShouldBeSent(
        rhelProduct.getProductId(), getSockets(), USAGE_ABOVE_THRESHOLD, CAPACITY);
    thenNoNotificationShouldBeSent(rosaProduct.getProductId(), getCores());
  }

  /** Verify notification created only for metric exceeding threshold within same product. */
  @Test
  @TestPlanName("utilization-multi-TC002")
  void shouldCreateNotificationOnlyForMetricExceedingThresholdWithinSameProduct() {
    // Given: One product with multiple metrics at different usage levels
    UtilizationSummary rosaProduct = givenUtilizationSummary(Product.ROSA, Granularity.HOURLY);
    givenMeasurement(rosaProduct, getCores(), USAGE_ABOVE_THRESHOLD, CAPACITY, false);
    givenMeasurement(rosaProduct, getInstanceHours(), USAGE_BELOW_THRESHOLD, CAPACITY, false);

    double initialCoresCount = overUsageMetricCount(getCores());
    double initialInstanceHoursCount = overUsageMetricCount(getInstanceHours());

    // When: Utilization event is processed
    whenUtilizationEventIsReceived(rosaProduct);

    // Then: Only metric exceeding threshold triggers notification
    thenOverUsageCounterShouldBeIncremented(initialCoresCount, getCores());
    thenOverUsageCounterShouldNotChange(initialInstanceHoursCount, getInstanceHours());
    thenNotificationShouldBeSent(
        rosaProduct.getProductId(), getCores(), USAGE_ABOVE_THRESHOLD, CAPACITY);
    thenNoNotificationShouldBeSent(rosaProduct.getProductId(), getInstanceHours());
  }

  /**
   * Different SLA/usage combinations on the same product and metric produce separate counter time
   * series.
   */
  @Test
  @TestPlanName("utilization-multi-TC003")
  void shouldIncrementSeparateOverUsageCounters_forDifferentSlaUsageCombinations() {
    UtilizationSummary premiumProduction =
        givenUtilizationSummary(Product.ROSA, Granularity.HOURLY)
            .withSla(UtilizationSummary.Sla.PREMIUM)
            .withUsage(UtilizationSummary.Usage.PRODUCTION);
    givenMeasurement(premiumProduction, getCores(), USAGE_ABOVE_THRESHOLD, CAPACITY, false);

    UtilizationSummary standardDevTest =
        givenUtilizationSummary(Product.ROSA, Granularity.HOURLY)
            .withSla(UtilizationSummary.Sla.STANDARD)
            .withUsage(UtilizationSummary.Usage.DEVELOPMENT_TEST);
    givenMeasurement(standardDevTest, getCores(), USAGE_ABOVE_THRESHOLD, CAPACITY, false);

    double initialPremiumProd =
        overUsageMetricCount(
            getCores(), UtilizationSummary.Sla.PREMIUM, UtilizationSummary.Usage.PRODUCTION);
    double initialStandardDev =
        overUsageMetricCount(
            getCores(), UtilizationSummary.Sla.STANDARD, UtilizationSummary.Usage.DEVELOPMENT_TEST);

    whenUtilizationEventIsReceived(premiumProduction);
    whenUtilizationEventIsReceived(standardDevTest);

    untilAsserted(
        () -> {
          assertThat(
              overUsageMetricCount(
                      getCores(),
                      UtilizationSummary.Sla.PREMIUM,
                      UtilizationSummary.Usage.PRODUCTION)
                  - initialPremiumProd,
              equalTo(1.0));
          assertThat(
              overUsageMetricCount(
                      getCores(),
                      UtilizationSummary.Sla.STANDARD,
                      UtilizationSummary.Usage.DEVELOPMENT_TEST)
                  - initialStandardDev,
              equalTo(1.0));
        });
  }

  // Given helpers
  /** Creates a UtilizationSummary with given product and granularity. */
  private UtilizationSummary givenUtilizationSummary(Product product, Granularity granularity) {
    return new UtilizationSummary()
        .withOrgId(orgId)
        .withProductId(product.getName())
        .withGranularity(granularity)
        .withBillingAccountId(RandomUtils.generateRandom())
        .withMeasurements(new ArrayList<>());
  }

  // When helpers
  /** Produces a utilization event to Kafka. */
  private void whenUtilizationEventIsReceived(UtilizationSummary utilizationSummary) {
    kafkaBridge.produceKafkaMessage(UTILIZATION, utilizationSummary);
  }

  // Then helpers
  /** Verifies over-usage counter is incremented for the given metric. */
  private void thenOverUsageCounterShouldBeIncremented(double initialCount, MetricId metricId) {
    untilAsserted(
        () -> {
          double currentCount = overUsageMetricCount(metricId);
          assertThat(
              OVER_USAGE_METRIC
                  + " counter should increment by exactly 1 for "
                  + metricId.getValue(),
              currentCount - initialCount,
              equalTo(1.0));
        });
  }

  /** Verifies over-usage counter does not change for the given metric. */
  private void thenOverUsageCounterShouldNotChange(double initialCount, MetricId metricId) {
    await()
        .pollDelay(MESSAGE_PROCESSING_DELAY)
        .untilAsserted(
            () -> {
              double currentCount = overUsageMetricCount(metricId);
              assertThat(
                  "Over-usage counter should not change for " + metricId.getValue(),
                  currentCount,
                  equalTo(initialCount));
            });
  }

  /** Verifies no notification is sent for the given product and metric. */
  private void thenNoNotificationShouldBeSent(String productId, MetricId metricId) {
    // Use a short timeout to verify no notification messages arrive for this product/metric
    var notifications =
        kafkaBridge.waitForKafkaMessage(
            NOTIFICATIONS,
            matchesOverageNotification(orgId, productId, metricId.getValue()),
            0, // Expected count is 0
            AwaitilitySettings.usingTimeout(Duration.ofSeconds(5)));

    assertThat(
        "No notification should be sent for product "
            + productId
            + " and metric "
            + metricId.getValue(),
        notifications,
        empty());
  }
}
