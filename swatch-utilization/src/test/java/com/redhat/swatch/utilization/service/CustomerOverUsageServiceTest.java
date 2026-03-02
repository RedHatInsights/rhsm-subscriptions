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
package com.redhat.swatch.utilization.service;

import static com.redhat.swatch.utilization.service.CustomerOverUsageService.OVER_USAGE_METRIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.utilization.configuration.FeatureFlags;
import com.redhat.swatch.utilization.model.Measurement;
import com.redhat.swatch.utilization.model.UtilizationSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@QuarkusTest
class CustomerOverUsageServiceTest {

  @Inject CustomerOverUsageService service;

  @Inject MeterRegistry meterRegistry;

  @InjectMock NotificationsProducer notificationsProducer;

  @InjectMock FeatureFlags featureFlags;

  static MockedStatic<SubscriptionDefinition> subscriptionDefinition;

  // Test data constants
  private static final String ORG_ID = "org123";
  private static final String PRODUCT_ID = "rosa";
  private static final String METRIC_ID = MetricIdUtils.getCores().getValue();
  private static final double CAPACITY = 100.0;
  private static final double USAGE_EXCEEDING_THRESHOLD = 110.0; // 10% over
  private static final double USAGE_BELOW_THRESHOLD = 103.0; // 3% over

  // Test assertion constants
  private static final double EXPECTED_SINGLE_INCREMENT = 1.0;
  private static final double EXPECTED_NO_CHANGE = 0.0;

  // Threshold test constants
  private static final double PRODUCT_SPECIFIC_THRESHOLD = 8.0; // 8% threshold for specific product
  private static final double DEFAULT_THRESHOLD = 5.0; // 5% default threshold
  private static final double NEGATIVE_THRESHOLD = -1.0; // Disables detection

  // Usage calculation constants
  private static final double USAGE_ABOVE_PRODUCT_THRESHOLD =
      107.0; // 7% over capacity, below 8% product threshold
  private static final double USAGE_ABOVE_DEFAULT_THRESHOLD =
      106.0; // 6% over capacity, exceeds default 5% threshold
  private static final double USAGE_EXCEEDING_CAPACITY_100_PERCENT =
      2.0; // 100% over capacity multiplier

  @BeforeAll
  static void beforeAll() {
    // functions as a spy
    subscriptionDefinition =
        Mockito.mockStatic(
            SubscriptionDefinition.class,
            Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
  }

  @AfterAll
  static void afterAll() {
    subscriptionDefinition.close();
  }

  @BeforeEach
  void setUp() {
    meterRegistry.clear();
    subscriptionDefinition.reset();
  }

  @Test
  void shouldIncrementCounter_whenUsageExceedsThreshold() {
    // Given
    when(featureFlags.sendNotifications()).thenReturn(true);
    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ID, METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    // When
    whenCheckSummary(summary);

    // Then
    double count = getCounterValue(PRODUCT_ID, ORG_ID, METRIC_ID);
    assertEquals(EXPECTED_SINGLE_INCREMENT, count, "Counter should be incremented once");
  }

  @Test
  void shouldNotIncrementCounter_whenUsageBelowThreshold() {
    // Given
    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ID, METRIC_ID, CAPACITY, USAGE_BELOW_THRESHOLD);

    // When
    whenCheckSummary(summary);

    // Then
    double count = getCounterValue(PRODUCT_ID, ORG_ID, METRIC_ID);
    assertEquals(EXPECTED_NO_CHANGE, count, "Counter should not be incremented");
  }

  @Test
  void shouldIncrementCounterOnce_forEachMeasurementExceedingThreshold() {
    // Given - two measurements, both exceeding threshold
    when(featureFlags.sendNotifications()).thenReturn(true);
    UtilizationSummary summary =
        new UtilizationSummary()
            .withOrgId(ORG_ID)
            .withProductId(PRODUCT_ID)
            .withGranularity(UtilizationSummary.Granularity.DAILY)
            .withMeasurements(
                List.of(
                    new Measurement()
                        .withMetricId(MetricIdUtils.getCores().getValue())
                        .withCurrentTotal(USAGE_EXCEEDING_THRESHOLD)
                        .withCapacity(CAPACITY)
                        .withUnlimited(false),
                    new Measurement()
                        .withMetricId(MetricIdUtils.getInstanceHours().getValue())
                        .withCurrentTotal(USAGE_EXCEEDING_THRESHOLD)
                        .withCapacity(CAPACITY)
                        .withUnlimited(false)));

    // When
    whenCheckSummary(summary);

    // Then - each measurement creates its own counter, check cores counter
    double coresCount = getCounterValue(PRODUCT_ID, ORG_ID, MetricIdUtils.getCores().getValue());
    assertEquals(EXPECTED_SINGLE_INCREMENT, coresCount, "Cores counter should be incremented");

    // And instance hours counter
    double instanceHoursCount =
        getCounterValue(PRODUCT_ID, ORG_ID, MetricIdUtils.getInstanceHours().getValue());
    assertEquals(
        EXPECTED_SINGLE_INCREMENT,
        instanceHoursCount,
        "Instance hours counter should be incremented");
  }

  @Test
  void shouldIncrementCounterSelectively_whenMixedMeasurements() {
    // Given
    when(featureFlags.sendNotifications()).thenReturn(true);
    UtilizationSummary summary =
        new UtilizationSummary()
            .withOrgId(ORG_ID)
            .withProductId(PRODUCT_ID)
            .withGranularity(UtilizationSummary.Granularity.DAILY)
            .withMeasurements(
                List.of(
                    // Below threshold - should NOT increment
                    new Measurement()
                        .withMetricId(MetricIdUtils.getCores().getValue())
                        .withCurrentTotal(USAGE_BELOW_THRESHOLD)
                        .withCapacity(CAPACITY)
                        .withUnlimited(false),
                    // Above threshold - should increment
                    new Measurement()
                        .withMetricId(MetricIdUtils.getInstanceHours().getValue())
                        .withCurrentTotal(USAGE_EXCEEDING_THRESHOLD)
                        .withCapacity(CAPACITY)
                        .withUnlimited(false)));

    // When
    whenCheckSummary(summary);

    // Then - only instance hours should be incremented
    double coresCount = getCounterValue(PRODUCT_ID, ORG_ID, MetricIdUtils.getCores().getValue());
    assertEquals(EXPECTED_NO_CHANGE, coresCount, "Cores counter should not be incremented");

    double instanceHoursCount =
        getCounterValue(PRODUCT_ID, ORG_ID, MetricIdUtils.getInstanceHours().getValue());
    assertEquals(
        EXPECTED_SINGLE_INCREMENT,
        instanceHoursCount,
        "Instance hours counter should be incremented");
  }

  @Test
  void shouldSendNotification_whenUsageExceedsThresholdAndFeatureFlagEnabled() {
    // Given
    when(featureFlags.sendNotifications()).thenReturn(true);
    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ID, METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    // When
    whenCheckSummary(summary);

    // Then
    verify(notificationsProducer, times(1)).produce(any(Action.class));
  }

  @Test
  void shouldNotSendNotification_whenUsageExceedsThresholdButFeatureFlagDisabled() {
    // Given
    when(featureFlags.sendNotifications()).thenReturn(false);
    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ID, METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    // When
    whenCheckSummary(summary);

    // Then
    verify(notificationsProducer, never()).produce(any(Action.class));
  }

  @Test
  void shouldNotSendNotification_whenUsageBelowThreshold() {
    // Given
    when(featureFlags.sendNotifications()).thenReturn(true);
    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ID, METRIC_ID, CAPACITY, USAGE_BELOW_THRESHOLD);

    // When
    whenCheckSummary(summary);

    // Then
    verify(notificationsProducer, never()).produce(any(Action.class));
  }

  @Test
  void shouldSendMultipleNotifications_whenMultipleMeasurementsExceedThreshold() {
    // Given
    when(featureFlags.sendNotifications()).thenReturn(true);
    UtilizationSummary summary =
        new UtilizationSummary()
            .withOrgId(ORG_ID)
            .withProductId(PRODUCT_ID)
            .withGranularity(UtilizationSummary.Granularity.DAILY)
            .withMeasurements(
                List.of(
                    new Measurement()
                        .withMetricId(MetricIdUtils.getCores().getValue())
                        .withCurrentTotal(USAGE_EXCEEDING_THRESHOLD)
                        .withCapacity(CAPACITY)
                        .withUnlimited(false),
                    new Measurement()
                        .withMetricId(MetricIdUtils.getInstanceHours().getValue())
                        .withCurrentTotal(USAGE_EXCEEDING_THRESHOLD)
                        .withCapacity(CAPACITY)
                        .withUnlimited(false)));

    // When
    whenCheckSummary(summary);

    // Then - should send one notification per measurement
    verify(notificationsProducer, times(2)).produce(any(Action.class));
  }

  @Test
  void shouldUseProductSpecificThreshold_whenConfigured() {
    // Given - mock product has specific threshold configured
    subscriptionDefinition
        .when(() -> SubscriptionDefinition.getOverUsageThreshold(PRODUCT_ID))
        .thenReturn(PRODUCT_SPECIFIC_THRESHOLD);

    double usage = USAGE_ABOVE_PRODUCT_THRESHOLD; // 7% over capacity, below 8% product threshold
    UtilizationSummary summary = givenUtilizationSummary(PRODUCT_ID, METRIC_ID, CAPACITY, usage);

    // When
    whenCheckSummary(summary);

    // Then - should NOT increment because 7% < 8% product threshold
    double count = getCounterValue(PRODUCT_ID, ORG_ID, METRIC_ID);
    assertEquals(
        EXPECTED_NO_CHANGE,
        count,
        "Counter should not be incremented when usage is below product-specific threshold");

    // Verify no notifications sent (below threshold)
    verify(notificationsProducer, never()).produce(any(Action.class));
  }

  @Test
  void shouldUseDefaultThreshold_whenProductThresholdNotConfigured() {
    // Given - mock returns null for product threshold, should fall back to default
    subscriptionDefinition
        .when(() -> SubscriptionDefinition.getOverUsageThreshold(PRODUCT_ID))
        .thenReturn(null);
    when(featureFlags.sendNotifications()).thenReturn(true);

    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ID, METRIC_ID, CAPACITY, USAGE_ABOVE_DEFAULT_THRESHOLD);

    // When
    whenCheckSummary(summary);

    // Then - should increment because 6% > 5% default threshold
    double count = getCounterValue(PRODUCT_ID, ORG_ID, METRIC_ID);
    assertEquals(
        EXPECTED_SINGLE_INCREMENT,
        count,
        "Counter should be incremented when using default threshold");

    // Verify the static method was called and notification was sent
    verify(notificationsProducer, times(1)).produce(any(Action.class));
  }

  @Test
  void shouldSendNotification_whenOrgIsAllowlistedAndGlobalFlagDisabled() {
    // Given - the global flag disabled but org is in allowlist
    when(featureFlags.sendNotifications()).thenReturn(false);
    when(featureFlags.isOrgAllowlistedForNotifications(ORG_ID)).thenReturn(true);
    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ID, METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    // When
    whenCheckSummary(summary);

    // Then - notification should be sent because org is allowlisted
    verify(notificationsProducer, times(1)).produce(any(Action.class));
  }

  @Test
  void shouldNotSendNotification_whenOrgIsNotAllowlistedAndGlobalFlagDisabled() {
    // Given - global flag disabled and org NOT in allowlist
    when(featureFlags.sendNotifications()).thenReturn(false);
    when(featureFlags.isOrgAllowlistedForNotifications(ORG_ID)).thenReturn(false);
    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ID, METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    // When
    whenCheckSummary(summary);

    // Then - no notification because global flag is off and org is not allowlisted
    verify(notificationsProducer, never()).produce(any(Action.class));
  }

  @Test
  void shouldSendNotification_whenOrgIsAllowlistedAndGlobalFlagEnabled() {
    // Given - both global flag and allowlist allow sending
    when(featureFlags.sendNotifications()).thenReturn(true);
    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ID, METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    // When
    whenCheckSummary(summary);

    // Then - notification sent (global flag takes priority, no need to check allowlist)
    verify(notificationsProducer, times(1)).produce(any(Action.class));
  }

  @Test
  void shouldSkipOverUsageDetection_whenThresholdIsNegative() {
    // Given - negative threshold disables over-usage detection
    subscriptionDefinition
        .when(() -> SubscriptionDefinition.getOverUsageThreshold(PRODUCT_ID))
        .thenReturn(NEGATIVE_THRESHOLD);

    double usage =
        CAPACITY
            * USAGE_EXCEEDING_CAPACITY_100_PERCENT; // 100% over capacity, but detection disabled
    UtilizationSummary summary = givenUtilizationSummary(PRODUCT_ID, METRIC_ID, CAPACITY, usage);

    // When
    whenCheckSummary(summary);

    // Then - should NOT increment because negative threshold disables detection
    double count = getCounterValue(PRODUCT_ID, ORG_ID, METRIC_ID);
    assertEquals(
        EXPECTED_NO_CHANGE,
        count,
        "Counter should not be incremented when threshold is negative (detection disabled)");

    // Verify no notifications sent (detection disabled)
    verify(notificationsProducer, never()).produce(any(Action.class));
  }

  // Helper methods
  private UtilizationSummary givenUtilizationSummary(
      String productId, String metricId, Double capacity, Double currentTotal) {
    return new UtilizationSummary()
        .withOrgId(ORG_ID)
        .withProductId(productId)
        .withGranularity(UtilizationSummary.Granularity.DAILY)
        .withMeasurements(
            List.of(
                new Measurement()
                    .withMetricId(metricId)
                    .withCurrentTotal(currentTotal)
                    .withCapacity(capacity)
                    .withUnlimited(false)));
  }

  private void whenCheckSummary(UtilizationSummary summary) {
    for (Measurement measurement : summary.getMeasurements()) {
      service.check(summary, measurement);
    }
  }

  // Bugfix: not part of SWATCH-3793 - removed org_id tag search to align with metric tagging
  // standards
  private double getCounterValue(String productId, String orgId, String metricId) {
    var counter =
        Search.in(meterRegistry)
            .name(OVER_USAGE_METRIC)
            .tag("product", productId)
            .tag("metric_id", metricId)
            .counter();
    return counter != null ? counter.count() : 0.0;
  }
}
