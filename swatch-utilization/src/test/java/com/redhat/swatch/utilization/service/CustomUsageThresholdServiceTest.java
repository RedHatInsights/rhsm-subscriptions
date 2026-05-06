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

import static com.redhat.swatch.utilization.service.CustomUsageThresholdService.CUSTOM_THRESHOLD_METRIC;
import static com.redhat.swatch.utilization.service.CustomUsageThresholdService.EVENT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.utilization.data.OrgUtilizationPreferenceEntity;
import com.redhat.swatch.utilization.data.OrgUtilizationPreferenceRepository;
import com.redhat.swatch.utilization.model.Measurement;
import com.redhat.swatch.utilization.model.Severity;
import com.redhat.swatch.utilization.model.UtilizationSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
class CustomUsageThresholdServiceTest {

  @Inject CustomUsageThresholdService service;

  @Inject MeterRegistry meterRegistry;

  @InjectMock NotificationsProducer notificationsProducer;
  @InjectMock OrgUtilizationPreferenceRepository preferenceRepository;

  static MockedStatic<SubscriptionDefinition> subscriptionDefinition;

  private static final String ORG_ID = "org123";
  private static final String PAYG_PRODUCT_ID = "rosa";
  private static final String NON_PAYG_PRODUCT_ID = "RHEL for x86";
  private static final String PAYG_MIXED_PRODUCT_ID = "ansible-aap-managed";
  private static final String CORES_METRIC_ID = MetricIdUtils.getCores().getValue();
  private static final String SOCKETS_METRIC_ID = MetricIdUtils.getSockets().getValue();
  private static final String MANAGED_NODES_METRIC_ID = MetricIdUtils.getManagedNodes().getValue();
  private static final String INSTANCE_HOURS_METRIC_ID =
      MetricIdUtils.getInstanceHours().getValue();
  private static final double CAPACITY = 100.0;
  private static final int CUSTOM_THRESHOLD = 80;
  private static final OffsetDateTime LAST_UPDATED =
      OffsetDateTime.of(2026, 4, 20, 10, 0, 0, 0, ZoneOffset.UTC);

  private static final double DEFAULT_OVER_USAGE_THRESHOLD = 5.0;
  private static final double USAGE_EXCEEDING_THRESHOLD = 85.0; // 85% > 80% threshold
  private static final double USAGE_AT_THRESHOLD = 80.0; // 80% == 80% threshold
  private static final double USAGE_BELOW_THRESHOLD = 70.0; // 70% < 80% threshold
  private static final double USAGE_TRIGGERING_OVER_USAGE_ALERT = 106.0; // 106% > 105%
  private static final double USAGE_AT_OVER_USAGE_BOUNDARY = 105.0; // 105% == 100% + 5%
  private static final double USAGE_NOT_TRIGGERING_OVER_USAGE_ALERT = 104.0; // 104% < 105%
  private static final double USAGE_AT_FULL_CAPACITY = 100.0; // 100% == 100% capacity

  private static final double EXPECTED_SINGLE_INCREMENT = 1.0;
  private static final double EXPECTED_NO_CHANGE = 0.0;

  @BeforeAll
  static void beforeAll() {
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
  void shouldSendNotification_whenUtilizationExceedsOrgThreshold() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    UtilizationSummary summary =
        givenUtilizationSummary(
            PAYG_PRODUCT_ID, CORES_METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    whenCheckSummary(summary);

    verify(notificationsProducer, times(1))
        .produce(argThat(action -> EVENT_TYPE.equals(action.getEventType())));
    assertEquals(EXPECTED_SINGLE_INCREMENT, getCounterValue(PAYG_PRODUCT_ID, CORES_METRIC_ID));
  }

  @Test
  void shouldSendNotification_whenUtilizationEqualsOrgThreshold() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    UtilizationSummary summary =
        givenUtilizationSummary(PAYG_PRODUCT_ID, CORES_METRIC_ID, CAPACITY, USAGE_AT_THRESHOLD);

    whenCheckSummary(summary);

    verify(notificationsProducer, times(1))
        .produce(argThat(action -> EVENT_TYPE.equals(action.getEventType())));
    assertEquals(EXPECTED_SINGLE_INCREMENT, getCounterValue(PAYG_PRODUCT_ID, CORES_METRIC_ID));
  }

  @Test
  void shouldNotSendNotification_whenUtilizationBelowOrgThreshold() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    UtilizationSummary summary =
        givenUtilizationSummary(
            PAYG_PRODUCT_ID, CORES_METRIC_ID, CAPACITY, USAGE_BELOW_THRESHOLD); // 70% < 80%

    whenCheckSummary(summary);

    verify(notificationsProducer, never()).produce(any(Action.class));
    assertEquals(EXPECTED_NO_CHANGE, getCounterValue(PAYG_PRODUCT_ID, CORES_METRIC_ID));
  }

  @Test
  void shouldSkipCustomThreshold_whenOverUsageAlertWillTrigger() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    UtilizationSummary summary =
        givenUtilizationSummary(
            PAYG_PRODUCT_ID, CORES_METRIC_ID, CAPACITY, USAGE_TRIGGERING_OVER_USAGE_ALERT);

    whenCheckSummary(summary);

    verify(notificationsProducer, never()).produce(any(Action.class));
    assertEquals(EXPECTED_NO_CHANGE, getCounterValue(PAYG_PRODUCT_ID, CORES_METRIC_ID));
  }

  @Test
  void shouldSendNotification_whenUsageAtOverUsageBoundaryAndAboveCustomThreshold() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    UtilizationSummary summary =
        givenUtilizationSummary(
            PAYG_PRODUCT_ID, CORES_METRIC_ID, CAPACITY, USAGE_AT_OVER_USAGE_BOUNDARY);

    whenCheckSummary(summary);

    verify(notificationsProducer, times(1))
        .produce(argThat(action -> EVENT_TYPE.equals(action.getEventType())));
    assertEquals(EXPECTED_SINGLE_INCREMENT, getCounterValue(PAYG_PRODUCT_ID, CORES_METRIC_ID));
  }

  @Test
  void shouldSendNotification_whenUsageAboveCustomThresholdAndNotTriggeringOverUsageAlert() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    UtilizationSummary summary =
        givenUtilizationSummary(
            PAYG_PRODUCT_ID, CORES_METRIC_ID, CAPACITY, USAGE_NOT_TRIGGERING_OVER_USAGE_ALERT);

    whenCheckSummary(summary);

    verify(notificationsProducer, times(1))
        .produce(argThat(action -> EVENT_TYPE.equals(action.getEventType())));
    assertEquals(EXPECTED_SINGLE_INCREMENT, getCounterValue(PAYG_PRODUCT_ID, CORES_METRIC_ID));
  }

  @Test
  void shouldSendNotification_whenUsageAtFullCapacityAndAboveCustomThreshold() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    UtilizationSummary summary =
        givenUtilizationSummary(PAYG_PRODUCT_ID, CORES_METRIC_ID, CAPACITY, USAGE_AT_FULL_CAPACITY);

    whenCheckSummary(summary);

    verify(notificationsProducer, times(1))
        .produce(argThat(action -> EVENT_TYPE.equals(action.getEventType())));
    assertEquals(EXPECTED_SINGLE_INCREMENT, getCounterValue(PAYG_PRODUCT_ID, CORES_METRIC_ID));
  }

  @Test
  void shouldSkipCustomThreshold_whenProductSpecificOverUsageThresholdExceeded() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    double productThreshold = 10.0;
    subscriptionDefinition
        .when(() -> SubscriptionDefinition.getOverUsageThreshold(PAYG_PRODUCT_ID))
        .thenReturn(productThreshold);
    // 111% > 100% + 10% product threshold
    UtilizationSummary summary =
        givenUtilizationSummary(PAYG_PRODUCT_ID, CORES_METRIC_ID, CAPACITY, 111.0);

    whenCheckSummary(summary);

    verify(notificationsProducer, never()).produce(any(Action.class));
    assertEquals(EXPECTED_NO_CHANGE, getCounterValue(PAYG_PRODUCT_ID, CORES_METRIC_ID));
  }

  @Test
  void shouldSendNotification_whenBelowProductSpecificOverUsageThreshold() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    double productThreshold = 10.0;
    subscriptionDefinition
        .when(() -> SubscriptionDefinition.getOverUsageThreshold(PAYG_PRODUCT_ID))
        .thenReturn(productThreshold);
    // 109% <= 100% + 10% product threshold
    UtilizationSummary summary =
        givenUtilizationSummary(PAYG_PRODUCT_ID, CORES_METRIC_ID, CAPACITY, 109.0);

    whenCheckSummary(summary);

    verify(notificationsProducer, times(1))
        .produce(argThat(action -> EVENT_TYPE.equals(action.getEventType())));
    assertEquals(EXPECTED_SINGLE_INCREMENT, getCounterValue(PAYG_PRODUCT_ID, CORES_METRIC_ID));
  }

  @Test
  void shouldNotSendNotification_whenNoOrgPreferenceExists() {
    when(preferenceRepository.findByIdOptional(ORG_ID)).thenReturn(Optional.empty());
    UtilizationSummary summary =
        givenUtilizationSummary(
            PAYG_PRODUCT_ID, CORES_METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    whenCheckSummary(summary);

    verify(notificationsProducer, never()).produce(any(Action.class));
    assertEquals(EXPECTED_NO_CHANGE, getCounterValue(PAYG_PRODUCT_ID, CORES_METRIC_ID));
  }

  @Test
  void shouldUseModerateSeverity() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    UtilizationSummary summary =
        givenUtilizationSummary(
            PAYG_PRODUCT_ID, CORES_METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    whenCheckSummary(summary);

    var captor = ArgumentCaptor.forClass(Action.class);
    verify(notificationsProducer, times(1)).produce(captor.capture());
    Action action = captor.getValue();
    assertEquals(
        Severity.MODERATE.name(),
        action.getSeverity(),
        "Custom threshold notifications should use MODERATE severity");
  }

  @Test
  void shouldIncludeLastUpdatedHashInPayload() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    UtilizationSummary summary =
        givenUtilizationSummary(
            PAYG_PRODUCT_ID, CORES_METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    whenCheckSummary(summary);

    var captor = ArgumentCaptor.forClass(Action.class);
    verify(notificationsProducer, times(1)).produce(captor.capture());
    Action action = captor.getValue();

    var eventPayload = action.getEvents().get(0).getPayload().getAdditionalProperties();
    String expectedHash = CustomUsageThresholdService.hashLastUpdated(LAST_UPDATED);
    assertEquals(expectedHash, eventPayload.get("last_updated_hash"));
  }

  @Test
  void shouldProduceDifferentHash_whenLastUpdatedChanges() {
    var later = OffsetDateTime.of(2026, 4, 21, 10, 0, 0, 0, ZoneOffset.UTC);

    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    UtilizationSummary summary =
        givenUtilizationSummary(
            PAYG_PRODUCT_ID, CORES_METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD);
    whenCheckSummary(summary);

    var captor = ArgumentCaptor.forClass(Action.class);
    verify(notificationsProducer, times(1)).produce(captor.capture());
    String firstHash =
        (String)
            captor
                .getValue()
                .getEvents()
                .get(0)
                .getPayload()
                .getAdditionalProperties()
                .get("last_updated_hash");

    Mockito.reset(notificationsProducer);
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, later);
    whenCheckSummary(summary);

    verify(notificationsProducer, times(1)).produce(captor.capture());
    String secondHash =
        (String)
            captor
                .getValue()
                .getEvents()
                .get(0)
                .getPayload()
                .getAdditionalProperties()
                .get("last_updated_hash");

    assertNotNull(firstHash);
    assertNotNull(secondHash);
    assertNotEquals(firstHash, secondHash);
  }

  @Test
  void shouldIncludeUtilizationPercentageInPayload() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    UtilizationSummary summary =
        givenUtilizationSummary(
            PAYG_PRODUCT_ID, CORES_METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD);

    whenCheckSummary(summary);

    var captor = ArgumentCaptor.forClass(Action.class);
    verify(notificationsProducer, times(1)).produce(captor.capture());
    Action action = captor.getValue();

    var eventPayload = action.getEvents().get(0).getPayload().getAdditionalProperties();
    String expectedPercent = String.format("%.2f", USAGE_EXCEEDING_THRESHOLD);
    assertEquals(expectedPercent, eventPayload.get("utilization_percentage"));
  }

  @Test
  void shouldUseCurrentTotal_forCounterMetric() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    // rosa / Cores: value=4 (hourly increment), currentTotal=85 (MTD total exceeds 80% of 100)
    UtilizationSummary summary =
        givenUtilizationSummary(
            PAYG_PRODUCT_ID, CORES_METRIC_ID, CAPACITY, 4.0, USAGE_EXCEEDING_THRESHOLD);

    whenCheckSummary(summary);

    verify(notificationsProducer, times(1)).produce(any(Action.class));
    assertEquals(EXPECTED_SINGLE_INCREMENT, getCounterValue(PAYG_PRODUCT_ID, CORES_METRIC_ID));
  }

  @Test
  void shouldUseValue_forGaugeMetric() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    // RHEL for x86 / Sockets: value=1 (50% of 2 capacity, below 80%), currentTotal=31 (accumulated
    // sum)
    UtilizationSummary summary =
        givenUtilizationSummary(NON_PAYG_PRODUCT_ID, SOCKETS_METRIC_ID, 2.0, 1.0, 31.0);

    whenCheckSummary(summary);

    verify(notificationsProducer, never()).produce(any(Action.class));
    assertEquals(EXPECTED_NO_CHANGE, getCounterValue(NON_PAYG_PRODUCT_ID, SOCKETS_METRIC_ID));
  }

  @Test
  void shouldTriggerNotification_forGaugeMetric_whenValueExceedsThreshold() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    // RHEL for x86 / Sockets: value=9 (90% of 10 capacity, above 80%), currentTotal=270
    // (accumulated sum)
    UtilizationSummary summary =
        givenUtilizationSummary(NON_PAYG_PRODUCT_ID, SOCKETS_METRIC_ID, 10.0, 9.0, 270.0);

    whenCheckSummary(summary);

    verify(notificationsProducer, times(1)).produce(any(Action.class));
    assertEquals(
        EXPECTED_SINGLE_INCREMENT, getCounterValue(NON_PAYG_PRODUCT_ID, SOCKETS_METRIC_ID));
  }

  @Test
  void shouldUseValue_forGaugeMetric_onPaygProduct() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    // ansible-aap-managed / Managed-nodes: value=5 (50% of 10, below 80%), currentTotal=150
    // (accumulated sum)
    UtilizationSummary summary =
        givenUtilizationSummary(PAYG_MIXED_PRODUCT_ID, MANAGED_NODES_METRIC_ID, 10.0, 5.0, 150.0);

    whenCheckSummary(summary);

    verify(notificationsProducer, never()).produce(any(Action.class));
    assertEquals(
        EXPECTED_NO_CHANGE, getCounterValue(PAYG_MIXED_PRODUCT_ID, MANAGED_NODES_METRIC_ID));
  }

  @Test
  void shouldUseCurrentTotal_forCounterMetric_onPaygProduct() {
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    // ansible-aap-managed / Instance-hours: value=4 (hourly increment), currentTotal=85 (MTD, above
    // 80%)
    UtilizationSummary summary =
        givenUtilizationSummary(
            PAYG_MIXED_PRODUCT_ID,
            INSTANCE_HOURS_METRIC_ID,
            CAPACITY,
            4.0,
            USAGE_EXCEEDING_THRESHOLD);

    whenCheckSummary(summary);

    verify(notificationsProducer, times(1)).produce(any(Action.class));
    assertEquals(
        EXPECTED_SINGLE_INCREMENT,
        getCounterValue(PAYG_MIXED_PRODUCT_ID, INSTANCE_HOURS_METRIC_ID));
  }

  static Stream<Arguments> serviceLevelAndUsageContextScenarios() {
    return Stream.of(
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
    givenOrgPreference(ORG_ID, CUSTOM_THRESHOLD, LAST_UPDATED);
    UtilizationSummary summary =
        givenUtilizationSummary(
                PAYG_PRODUCT_ID, CORES_METRIC_ID, CAPACITY, USAGE_EXCEEDING_THRESHOLD)
            .withSla(sla)
            .withUsage(usage);

    whenCheckSummary(summary);

    var captor = ArgumentCaptor.forClass(Action.class);
    verify(notificationsProducer, times(1)).produce(captor.capture());
    var context = captor.getValue().getContext().getAdditionalProperties();
    assertEquals(expectedServiceLevel, context.get("service_level"));
    assertEquals(expectedUsage, context.get("usage"));

    double count = getCounterValue(PAYG_PRODUCT_ID, CORES_METRIC_ID, sla, usage);
    assertEquals(EXPECTED_SINGLE_INCREMENT, count);
  }

  // Helper methods

  private void givenOrgPreference(String orgId, int threshold, OffsetDateTime lastUpdated) {
    var entity = new OrgUtilizationPreferenceEntity();
    entity.setOrgId(orgId);
    entity.setCustomThreshold(threshold);
    entity.setLastUpdated(lastUpdated);
    when(preferenceRepository.findByIdOptional(orgId)).thenReturn(Optional.of(entity));
  }

  private UtilizationSummary givenUtilizationSummary(
      String productId, String metricId, Double capacity, Double currentTotal) {
    return givenUtilizationSummary(productId, metricId, capacity, currentTotal, currentTotal);
  }

  private UtilizationSummary givenUtilizationSummary(
      String productId, String metricId, Double capacity, Double value, Double currentTotal) {
    return new UtilizationSummary()
        .withOrgId(ORG_ID)
        .withProductId(productId)
        .withGranularity(UtilizationSummary.Granularity.DAILY)
        .withMeasurements(
            List.of(
                new Measurement()
                    .withMetricId(metricId)
                    .withValue(value)
                    .withCurrentTotal(currentTotal)
                    .withCapacity(capacity)
                    .withUnlimited(false)));
  }

  private void whenCheckSummary(UtilizationSummary summary) {
    for (Measurement measurement : summary.getMeasurements()) {
      service.check(summary, measurement);
    }
  }

  private double getCounterValue(String productId, String metricId) {
    return getCounterValue(productId, metricId, null, null);
  }

  private double getCounterValue(
      String productId,
      String metricId,
      UtilizationSummary.Sla sla,
      UtilizationSummary.Usage usage) {
    var counter =
        Search.in(meterRegistry)
            .name(CUSTOM_THRESHOLD_METRIC)
            .tag("product", productId)
            .tag("metric_id", metricId)
            .tag("sla", BaseThresholdService.metricSlaLabelValue(sla))
            .tag("usage", BaseThresholdService.metricUsageLabelValue(usage))
            .counter();
    return counter != null ? counter.count() : 0.0;
  }
}
