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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.utilization.model.Measurement;
import com.redhat.swatch.utilization.model.Severity;
import com.redhat.swatch.utilization.model.UtilizationSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@QuarkusTest
class CustomerOverUsageServiceTest {

  @Inject CustomerOverUsageService service;

  @Inject MeterRegistry meterRegistry;

  @InjectMock NotificationsProducer notificationsProducer;

  static MockedStatic<SubscriptionDefinition> subscriptionDefinition;

  // Test data constants
  private static final String ORG_ID = "org123";
  private static final String PRODUCT_ID = "rosa"; // PAYG product, counter metrics
  private static final String NON_PAYG_PRODUCT_ID = "RHEL for x86"; // non-PAYG, gauge metrics
  private static final String PAYG_MIXED_PRODUCT_ID = "ansible-aap-managed"; // PAYG with mixed
  private static final String METRIC_ID = MetricIdUtils.getCores().getValue();
  private static final String SOCKETS_METRIC_ID = MetricIdUtils.getSockets().getValue();
  private static final String MANAGED_NODES_METRIC_ID = MetricIdUtils.getManagedNodes().getValue();
  private static final String INSTANCE_HOURS_METRIC_ID =
      MetricIdUtils.getInstanceHours().getValue();
  private static final double CAPACITY = 100.0;
  private static final double USAGE_EXCEEDING_THRESHOLD = 110.0; // 10% over
  private static final double USAGE_BELOW_THRESHOLD = 103.0; // 3% over

  // Test assertion constants
  private static final double EXPECTED_SINGLE_INCREMENT = 1.0;
  private static final double EXPECTED_NO_CHANGE = 0.0;

  // Threshold test constants
  private static final double PRODUCT_SPECIFIC_THRESHOLD = 8.0; // 8% threshold for specific product
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
    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ID, METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    // When
    whenCheckSummary(summary);

    // Then
    double count = getCounterValue(PRODUCT_ID, METRIC_ID);
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
    double count = getCounterValue(PRODUCT_ID, METRIC_ID);
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
    double coresCount = getCounterValue(PRODUCT_ID, MetricIdUtils.getCores().getValue());
    assertEquals(EXPECTED_SINGLE_INCREMENT, coresCount, "Cores counter should be incremented");

    // And instance hours counter
    double instanceHoursCount =
        getCounterValue(PRODUCT_ID, MetricIdUtils.getInstanceHours().getValue());
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
    double coresCount = getCounterValue(PRODUCT_ID, MetricIdUtils.getCores().getValue());
    assertEquals(EXPECTED_NO_CHANGE, coresCount, "Cores counter should not be incremented");

    double instanceHoursCount =
        getCounterValue(PRODUCT_ID, MetricIdUtils.getInstanceHours().getValue());
    assertEquals(
        EXPECTED_SINGLE_INCREMENT,
        instanceHoursCount,
        "Instance hours counter should be incremented");
  }

  @Test
  void shouldInvokeNotificationsProducer_whenUsageExceedsThreshold() {
    // Given
    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ID, METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    // When
    whenCheckSummary(summary);

    // Then
    verify(notificationsProducer, times(1)).produce(any(Action.class));
  }

  @Test
  void shouldSendOverusageNotification_withImportantSeverity() {
    // Given
    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ID, METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    // When
    whenCheckSummary(summary);

    // Then
    var captor = ArgumentCaptor.forClass(Action.class);
    verify(notificationsProducer, times(1)).produce(captor.capture());
    Action action = captor.getValue();
    assertNotNull(action.getSeverity(), "Notification action should define severity");
    assertEquals(
        Severity.IMPORTANT.name(),
        action.getSeverity(),
        "Over-usage notifications should use IMPORTANT severity");
  }

  @Test
  void shouldNotSendNotification_whenUsageBelowThreshold() {
    // Given
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
    double count = getCounterValue(PRODUCT_ID, METRIC_ID);
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

    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ID, METRIC_ID, CAPACITY, USAGE_ABOVE_DEFAULT_THRESHOLD);

    // When
    whenCheckSummary(summary);

    // Then - should increment because 6% > 5% default threshold
    double count = getCounterValue(PRODUCT_ID, METRIC_ID);
    assertEquals(
        EXPECTED_SINGLE_INCREMENT,
        count,
        "Counter should be incremented when using default threshold");

    // Verify the static method was called and notification was sent
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
    double count = getCounterValue(PRODUCT_ID, METRIC_ID);
    assertEquals(
        EXPECTED_NO_CHANGE,
        count,
        "Counter should not be incremented when threshold is negative (detection disabled)");

    // Verify no notifications sent (detection disabled)
    verify(notificationsProducer, never()).produce(any(Action.class));
  }

  static Stream<Arguments> serviceLevelAndUsageContextScenarios() {
    return Stream.of(
        // sla, usage, expectedServiceLevel, expectedUsage
        Arguments.of(
            UtilizationSummary.Sla.PREMIUM,
            UtilizationSummary.Usage.PRODUCTION,
            "Premium",
            "Production"),
        Arguments.of(
            UtilizationSummary.Sla.STANDARD,
            UtilizationSummary.Usage.DEVELOPMENT_TEST,
            "Standard",
            "Development/Test"),
        Arguments.of(UtilizationSummary.Sla.ANY, UtilizationSummary.Usage.ANY, null, null),
        Arguments.of(
            UtilizationSummary.Sla.__EMPTY__, UtilizationSummary.Usage.__EMPTY__, null, null),
        Arguments.of(UtilizationSummary.Sla.PREMIUM, null, "Premium", null),
        Arguments.of(null, UtilizationSummary.Usage.PRODUCTION, null, "Production"),
        Arguments.of(
            UtilizationSummary.Sla.__EMPTY__,
            UtilizationSummary.Usage.PRODUCTION,
            null,
            "Production"),
        Arguments.of(
            UtilizationSummary.Sla.PREMIUM, UtilizationSummary.Usage.__EMPTY__, "Premium", null),
        Arguments.of(
            UtilizationSummary.Sla.ANY, UtilizationSummary.Usage.PRODUCTION, null, "Production"),
        Arguments.of(
            UtilizationSummary.Sla.PREMIUM, UtilizationSummary.Usage.ANY, "Premium", null));
  }

  @ParameterizedTest
  @MethodSource("serviceLevelAndUsageContextScenarios")
  void shouldPopulateContextWithServiceLevelAndUsage(
      UtilizationSummary.Sla sla,
      UtilizationSummary.Usage usage,
      String expectedServiceLevel,
      String expectedUsage) {
    // Given
    UtilizationSummary summary =
        givenUtilizationSummary(PRODUCT_ID, METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD)
            .withSla(sla)
            .withUsage(usage);

    // When
    whenCheckSummary(summary);

    // Then
    var captor = ArgumentCaptor.forClass(Action.class);
    verify(notificationsProducer, times(1)).produce(captor.capture());
    var context = captor.getValue().getContext().getAdditionalProperties();
    assertEquals(expectedServiceLevel, context.get("service_level"));
    assertEquals(expectedUsage, context.get("usage"));

    double count = getCounterValue(PRODUCT_ID, METRIC_ID, sla, usage);
    assertEquals(
        EXPECTED_SINGLE_INCREMENT,
        count,
        "Counter should record one increment for this sla/usage label combination");
  }

  @Test
  void shouldUseValue_forGaugeMetric_nonPaygProduct() {
    // RHEL for x86 / Sockets is a gauge metric on a non-PAYG product.
    // value=1 socket (below 2 capacity), currentTotal=31 (meaningless sum of daily gauges)
    UtilizationSummary summary =
        new UtilizationSummary()
            .withOrgId(ORG_ID)
            .withProductId(NON_PAYG_PRODUCT_ID)
            .withGranularity(UtilizationSummary.Granularity.DAILY)
            .withMeasurements(
                List.of(
                    new Measurement()
                        .withMetricId(SOCKETS_METRIC_ID)
                        .withValue(1.0)
                        .withCurrentTotal(31.0)
                        .withCapacity(2.0)
                        .withUnlimited(false)));

    whenCheckSummary(summary);

    verify(notificationsProducer, never()).produce(any(Action.class));
  }

  @Test
  void shouldTriggerNotification_forGaugeMetric_whenValueExceedsCapacity() {
    UtilizationSummary summary =
        new UtilizationSummary()
            .withOrgId(ORG_ID)
            .withProductId(NON_PAYG_PRODUCT_ID)
            .withGranularity(UtilizationSummary.Granularity.DAILY)
            .withMeasurements(
                List.of(
                    new Measurement()
                        .withMetricId(SOCKETS_METRIC_ID)
                        .withValue(3.0)
                        .withCurrentTotal(93.0)
                        .withCapacity(2.0)
                        .withUnlimited(false)));

    whenCheckSummary(summary);

    verify(notificationsProducer, times(1)).produce(any(Action.class));
  }

  @Test
  void shouldUseCurrentTotal_forCounterMetric_paygProduct() {
    // rosa / Cores is a counter metric on a PAYG product.
    // value=4 (hourly increment), currentTotal=110 (MTD cumulative, exceeds 100 capacity)
    UtilizationSummary summary =
        new UtilizationSummary()
            .withOrgId(ORG_ID)
            .withProductId(PRODUCT_ID)
            .withGranularity(UtilizationSummary.Granularity.HOURLY)
            .withMeasurements(
                List.of(
                    new Measurement()
                        .withMetricId(METRIC_ID)
                        .withValue(4.0)
                        .withCurrentTotal(USAGE_EXCEEDING_THRESHOLD)
                        .withCapacity(CAPACITY)
                        .withUnlimited(false)));

    whenCheckSummary(summary);

    verify(notificationsProducer, times(1)).produce(any(Action.class));
  }

  @Test
  void shouldUseValue_forGaugeMetric_onPaygProduct() {
    // ansible-aap-managed / Managed-nodes is a gauge metric on a PAYG product.
    // value=5 (below 10 capacity), currentTotal=150 (meaningless sum of daily gauges)
    UtilizationSummary summary =
        new UtilizationSummary()
            .withOrgId(ORG_ID)
            .withProductId(PAYG_MIXED_PRODUCT_ID)
            .withGranularity(UtilizationSummary.Granularity.DAILY)
            .withMeasurements(
                List.of(
                    new Measurement()
                        .withMetricId(MANAGED_NODES_METRIC_ID)
                        .withValue(5.0)
                        .withCurrentTotal(150.0)
                        .withCapacity(10.0)
                        .withUnlimited(false)));

    whenCheckSummary(summary);

    verify(notificationsProducer, never()).produce(any(Action.class));
  }

  @Test
  void shouldUseCurrentTotal_forCounterMetric_onSamePaygProduct() {
    // ansible-aap-managed / Instance-hours is a counter metric on the same PAYG product.
    // value=4 (hourly increment), currentTotal=110 (MTD cumulative, exceeds 100 capacity)
    UtilizationSummary summary =
        new UtilizationSummary()
            .withOrgId(ORG_ID)
            .withProductId(PAYG_MIXED_PRODUCT_ID)
            .withGranularity(UtilizationSummary.Granularity.HOURLY)
            .withMeasurements(
                List.of(
                    new Measurement()
                        .withMetricId(INSTANCE_HOURS_METRIC_ID)
                        .withValue(4.0)
                        .withCurrentTotal(USAGE_EXCEEDING_THRESHOLD)
                        .withCapacity(CAPACITY)
                        .withUnlimited(false)));

    whenCheckSummary(summary);

    verify(notificationsProducer, times(1)).produce(any(Action.class));
  }

  @Test
  void shouldFallBackToValue_forUnknownMetricOnKnownProduct() {
    // Known product but unrecognized metric → defaults to GAUGE → uses value
    UtilizationSummary summary =
        new UtilizationSummary()
            .withOrgId(ORG_ID)
            .withProductId(NON_PAYG_PRODUCT_ID)
            .withGranularity(UtilizationSummary.Granularity.DAILY)
            .withMeasurements(
                List.of(
                    new Measurement()
                        .withMetricId("Unknown-metric")
                        .withValue(1.0)
                        .withCurrentTotal(500.0)
                        .withCapacity(2.0)
                        .withUnlimited(false)));

    whenCheckSummary(summary);

    verify(notificationsProducer, never()).produce(any(Action.class));
  }

  @Test
  void shouldFallBackToValue_forUnknownProduct() {
    // Unknown product → lookupSubscriptionByTag returns empty → defaults to GAUGE → uses value
    UtilizationSummary summary =
        new UtilizationSummary()
            .withOrgId(ORG_ID)
            .withProductId("totally-unknown-product")
            .withGranularity(UtilizationSummary.Granularity.DAILY)
            .withMeasurements(
                List.of(
                    new Measurement()
                        .withMetricId(SOCKETS_METRIC_ID)
                        .withValue(1.0)
                        .withCurrentTotal(500.0)
                        .withCapacity(2.0)
                        .withUnlimited(false)));

    whenCheckSummary(summary);

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

  /**
   * Reads the over-usage counter for the time series where both SLA and usage labels are {@code
   * _ANY} (payload has no specific dimensions—{@code null}, {@code ANY}, or empty for both).
   * Equivalent to {@link #getCounterValue(String, String, UtilizationSummary.Sla,
   * UtilizationSummary.Usage)} with null {@code sla} and {@code usage}.
   */
  private double getCounterValue(String productId, String metricId) {
    return getCounterValue(productId, metricId, null, null);
  }

  /**
   * @param sla nullable; when not a specific value on the payload, the {@code sla} label is {@code
   *     _ANY} (same as {@code null}, {@code ANY}, or empty enum value)
   * @param usage same semantics as {@code sla} for usage type
   */
  private double getCounterValue(
      String productId,
      String metricId,
      UtilizationSummary.Sla sla,
      UtilizationSummary.Usage usage) {
    var counter =
        Search.in(meterRegistry)
            .name(OVER_USAGE_METRIC)
            .tag("product", productId)
            .tag("metric_id", metricId)
            .tag("sla", CustomerOverUsageService.metricSlaLabelValue(sla))
            .tag("usage", CustomerOverUsageService.metricUsageLabelValue(usage))
            .counter();
    return counter != null ? counter.count() : 0.0;
  }
}
