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
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.utilization.test.model.Measurement;
import com.redhat.swatch.utilization.test.model.UtilizationSummary;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CustomerOverUsageComponentTest extends BaseUtilizationComponentTest {

  protected static final String RECEIVED_METRIC = "swatch_utilization_received_total";
  protected static final String OVER_USAGE_METRIC = "swatch_utilization_over_usage_total";

  // Test data constants - aligned with CUSTOMER_OVER_USAGE_DEFAULT_THRESHOLD_PERCENT=5.0
  private static final String PRODUCT_ROSA = "rosa";
  private static final double BASELINE_CAPACITY = 100.0;
  private static final double USAGE_EXCEEDING_THRESHOLD = 110.0; // 10% over capacity
  private static final double USAGE_BELOW_THRESHOLD = 103.0; // 3% over capacity
  private static final double ARBITRARY_USAGE = 200.0;
  private static final double SMALL_USAGE = 10.0;
  private static final double ZERO_CAPACITY = 0.0;

  // Test timing and assertion constants
  private static final Duration MESSAGE_PROCESSING_DELAY = Duration.ofSeconds(2);
  private static final double EXPECTED_SINGLE_INCREMENT = 1.0;

  private final Map<String, Double> initialCounters = new HashMap<>();

  @BeforeAll
  static void enableSendNotificationsFeatureFlag() {
    unleash.enableFlag(SEND_NOTIFICATIONS);
  }

  @AfterAll
  static void disableSendNotificationsFeatureFlag() {
    unleash.disableFlag(SEND_NOTIFICATIONS);
  }

  @BeforeEach
  void initializeCount() {
    for (String metric : List.of(OVER_USAGE_METRIC, RECEIVED_METRIC)) {
      double initialCount =
          service.getMetricByTags(OVER_USAGE_METRIC, metricIdTag(MetricIdUtils.getCores()));
      initialCounters.put(metric, initialCount);
    }
  }

  /**
   * Verify over-usage counter is incremented when usage exceeds capacity by more than threshold.
   */
  @Test
  void shouldIncrementOverUsageCounter_whenUsageExceedsThreshold() {
    // 10% over capacity exceeds the 5% threshold
    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ROSA, BASELINE_CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    whenUtilizationEventIsReceived(summary);

    thenReceivedCounterShouldBeIncremented();
    thenOverUsageCounterShouldBeIncremented();
  }

  /** Verify over-usage counter is not incremented when usage is below threshold. */
  @Test
  void shouldNotIncrementOverUsageCounter_whenUsageBelowThreshold() {
    // 3% over capacity is below the 5% threshold
    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ROSA, BASELINE_CAPACITY, USAGE_BELOW_THRESHOLD);

    whenUtilizationEventIsReceived(summary);

    thenOverUsageCounterShouldNotChange();
  }

  /** Verify over-usage counter is not incremented for unlimited capacity subscriptions. */
  @Test
  void shouldNotIncrementOverUsageCounter_whenCapacityIsUnlimited() {
    UtilizationSummary summary = givenUnlimitedCapacityUtilization(PRODUCT_ROSA, ARBITRARY_USAGE);

    whenUtilizationEventIsReceived(summary);

    thenOverUsageCounterShouldNotChange();
  }

  /** Verify over-usage counter is incremented for daily granularity events. */
  @Test
  void shouldIncrementOverUsageCounter_whenGranularityIsDaily() {
    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ROSA, BASELINE_CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    whenUtilizationEventIsReceived(summary);

    thenOverUsageCounterShouldBeIncremented();
  }

  /** Verify over-usage counter is incremented for hourly granularity events. */
  @Test
  void shouldIncrementOverUsageCounter_whenGranularityIsHourly() {
    UtilizationSummary summary =
        givenHourlyUtilization(PRODUCT_ROSA, BASELINE_CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    whenUtilizationEventIsReceived(summary);

    thenOverUsageCounterShouldBeIncremented();
  }

  /** Verify over-usage counter is not incremented for unsupported granularity events. */
  @Test
  void shouldNotIncrementOverUsageCounter_whenGranularityNotSupported() {
    UtilizationSummary summary =
        givenWeeklyUtilization(PRODUCT_ROSA, BASELINE_CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    whenUtilizationEventIsReceived(summary);

    thenOverUsageCounterShouldNotChange();
  }

  /** Verify no exceptions thrown when measurements list is null. */
  @Test
  void shouldHandleNullMeasurementsList_withoutErrors() {
    UtilizationSummary summary =
        new UtilizationSummary()
            .withOrgId(orgId)
            .withProductId(PRODUCT_ROSA)
            .withGranularity(UtilizationSummary.Granularity.DAILY)
            .withBillingAccountId(RandomUtils.generateRandom())
            .withMeasurements(null);

    whenUtilizationEventIsReceived(summary);

    // Verify counter doesn't change when measurements are null
    thenOverUsageCounterShouldNotChange();
  }

  /** Verify only measurements exceeding threshold increment counter. */
  @Test
  void shouldIncrementCounterOnlyForMeasurementsExceedingThreshold() {
    // Bugfix: not part of SWATCH-3793 - use metric_id tag to isolate the specific counter
    // since org_id tags are not allowed, we filter by metric_id instead
    double initialInstanceHoursCount =
        service.getMetricByTags(OVER_USAGE_METRIC, metricIdTag(MetricIdUtils.getInstanceHours()));

    UtilizationSummary summary =
        new UtilizationSummary()
            .withOrgId(orgId)
            .withProductId(PRODUCT_ROSA)
            .withGranularity(UtilizationSummary.Granularity.DAILY)
            .withBillingAccountId(RandomUtils.generateRandom())
            .withMeasurements(
                List.of(
                    // Below threshold - should NOT increment
                    new Measurement()
                        .withMetricId(MetricIdUtils.getCores().getValue())
                        .withCurrentTotal(USAGE_BELOW_THRESHOLD)
                        .withCapacity(BASELINE_CAPACITY)
                        .withUnlimited(false),
                    // Above threshold - should increment
                    new Measurement()
                        .withMetricId(MetricIdUtils.getInstanceHours().getValue())
                        .withCurrentTotal(USAGE_EXCEEDING_THRESHOLD)
                        .withCapacity(BASELINE_CAPACITY)
                        .withUnlimited(false)));

    whenUtilizationEventIsReceived(summary);

    // Counter should increment by exactly 1 (only the second measurement)
    AwaitilityUtils.untilAsserted(
        () -> {
          double currentCount =
              service.getMetricByTags(
                  OVER_USAGE_METRIC, metricIdTag(MetricIdUtils.getInstanceHours()));
          assertThat(
              "Counter should increment by exactly 1 for the one measurement exceeding threshold",
              currentCount - initialInstanceHoursCount,
              equalTo(EXPECTED_SINGLE_INCREMENT));
        });
  }

  /** Verify over-usage counter is not incremented when capacity is zero. */
  @Test
  void shouldNotIncrementOverUsageCounter_whenCapacityIsZero() {
    UtilizationSummary summary =
        new UtilizationSummary()
            .withOrgId(orgId)
            .withProductId(PRODUCT_ROSA)
            .withGranularity(UtilizationSummary.Granularity.DAILY)
            .withBillingAccountId(RandomUtils.generateRandom())
            .withMeasurements(
                List.of(
                    new Measurement()
                        .withMetricId(MetricIdUtils.getCores().getValue())
                        .withCurrentTotal(SMALL_USAGE)
                        .withCapacity(ZERO_CAPACITY)
                        .withUnlimited(false)));

    whenUtilizationEventIsReceived(summary);

    thenOverUsageCounterShouldNotChange();
  }

  // Helper methods
  // Bugfix: not part of SWATCH-3793 - helper to create metric_id tag for filtering
  private String metricIdTag(com.redhat.swatch.configuration.registry.MetricId metricId) {
    return String.format("metric_id=\"%s\"", metricId.getValue());
  }

  // Given helpers
  UtilizationSummary givenUtilizationSummary(
      String productId, double capacity, double currentTotal) {
    return new UtilizationSummary()
        .withOrgId(orgId)
        .withProductId(productId)
        .withGranularity(UtilizationSummary.Granularity.DAILY)
        .withBillingAccountId(RandomUtils.generateRandom())
        .withMeasurements(
            List.of(
                new Measurement()
                    .withMetricId(MetricIdUtils.getCores().getValue())
                    .withCurrentTotal(currentTotal)
                    .withCapacity(capacity)
                    .withUnlimited(false)));
  }

  UtilizationSummary givenUnlimitedCapacityUtilization(String productId, double currentTotal) {
    return new UtilizationSummary()
        .withOrgId(orgId)
        .withProductId(productId)
        .withGranularity(UtilizationSummary.Granularity.DAILY)
        .withBillingAccountId(RandomUtils.generateRandom())
        .withMeasurements(
            List.of(
                new Measurement()
                    .withMetricId(MetricIdUtils.getCores().getValue())
                    .withCurrentTotal(currentTotal)
                    .withCapacity(BASELINE_CAPACITY)
                    .withUnlimited(true)));
  }

  UtilizationSummary givenHourlyUtilization(
      String productId, double capacity, double currentTotal) {
    return new UtilizationSummary()
        .withOrgId(orgId)
        .withProductId(productId)
        .withGranularity(UtilizationSummary.Granularity.HOURLY)
        .withBillingAccountId(RandomUtils.generateRandom())
        .withMeasurements(
            List.of(
                new Measurement()
                    .withMetricId(MetricIdUtils.getCores().getValue())
                    .withCurrentTotal(currentTotal)
                    .withCapacity(capacity)
                    .withUnlimited(false)));
  }

  UtilizationSummary givenWeeklyUtilization(
      String productId, double capacity, double currentTotal) {
    return new UtilizationSummary()
        .withOrgId(orgId)
        .withProductId(productId)
        .withGranularity(UtilizationSummary.Granularity.WEEKLY)
        .withBillingAccountId(RandomUtils.generateRandom())
        .withMeasurements(
            List.of(
                new Measurement()
                    .withMetricId(MetricIdUtils.getCores().getValue())
                    .withCurrentTotal(currentTotal)
                    .withCapacity(capacity)
                    .withUnlimited(false)));
  }

  // When helpers
  void whenUtilizationEventIsReceived(UtilizationSummary summary) {
    kafkaBridge.produceKafkaMessage(UTILIZATION, summary);
  }

  // Then helpers
  void thenOverUsageCounterShouldBeIncremented() {
    thenCounterShouldBeIncremented(OVER_USAGE_METRIC);
  }

  void thenReceivedCounterShouldBeIncremented() {
    thenCounterShouldBeIncremented(RECEIVED_METRIC);
  }

  void thenCounterShouldBeIncremented(String metric) {
    Double initialCount = initialCounters.getOrDefault(metric, 0.0);
    AwaitilityUtils.untilAsserted(
        () -> {
          double currentCount =
              service.getMetricByTags(metric, metricIdTag(MetricIdUtils.getCores()));
          assertThat(
              metric + " counter should be incremented", currentCount, greaterThan(initialCount));
        });
  }

  void thenOverUsageCounterShouldNotChange() {
    // Wait for message processing and verify counter remains unchanged
    Double initialCount = initialCounters.getOrDefault(OVER_USAGE_METRIC, 0.0);
    await()
        .pollDelay(MESSAGE_PROCESSING_DELAY)
        .untilAsserted(
            () -> {
              double currentCount =
                  service.getMetricByTags(OVER_USAGE_METRIC, metricIdTag(MetricIdUtils.getCores()));
              assertThat(
                  "Over-usage counter should not change", currentCount, equalTo(initialCount));
            });
  }
}
