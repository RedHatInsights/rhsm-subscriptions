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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CustomerOverUsageServiceTest {

  @Inject CustomerOverUsageService service;

  @Inject MeterRegistry meterRegistry;

  @InjectMock NotificationsProducer notificationsProducer;

  @InjectMock FeatureFlags featureFlags;

  // Test data constants
  private static final String ORG_ID = "org123";
  private static final String PRODUCT_ID = "rosa";
  private static final String METRIC_ID = MetricIdUtils.getCores().getValue();
  private static final double CAPACITY = 100.0;
  private static final double USAGE_EXCEEDING_THRESHOLD = 110.0; // 10% over
  private static final double USAGE_BELOW_THRESHOLD = 103.0; // 3% over
  private static final double ZERO_CAPACITY = 0.0;

  // Test assertion constants
  private static final double EXPECTED_SINGLE_INCREMENT = 1.0;
  private static final double EXPECTED_NO_CHANGE = 0.0;

  @BeforeEach
  void setUp() {
    meterRegistry.clear();
  }

  @Test
  void shouldIncrementCounter_whenUsageExceedsThreshold() {
    // Given
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
