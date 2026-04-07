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
package org.candlepin.subscriptions.tally;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.TallyMeasurement;
import org.candlepin.subscriptions.json.TallySummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TallySummaryMapperTest {

  private static final String ROSA_TAG = "rosa";
  private static final String RHEL_X86_TAG = "RHEL for x86";
  private static final String ANSIBLE_AAP_TAG = "ansible-aap-managed";
  private static final String CORES_METRIC = MetricIdUtils.getCores().toUpperCaseFormatted();
  private static final String SOCKETS_METRIC = MetricIdUtils.getSockets().toUpperCaseFormatted();
  private static final String MANAGED_NODES_METRIC =
      MetricIdUtils.getManagedNodes().toUpperCaseFormatted();
  private static final String INSTANCE_HOURS_METRIC =
      MetricIdUtils.getInstanceHours().toUpperCaseFormatted();
  private static final OffsetDateTime START_OF_MONTH =
      OffsetDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

  @Mock TallySnapshotRepository snapshotRepository;
  @Mock ApplicationClock clock;
  @InjectMocks TallySummaryMapper mapper;

  @BeforeEach
  void setUp() {
    lenient().when(clock.startOfMonth(any(OffsetDateTime.class))).thenReturn(START_OF_MONTH);
  }

  @Test
  void shouldUseSumForCounterMetric() {
    double snapshotValue = 4.0;
    double sumValue = 110.0;
    TallySnapshot snapshot =
        buildSnapshot(
            ROSA_TAG, Granularity.HOURLY, BillingProvider.AWS, CORES_METRIC, snapshotValue);
    var key = new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, CORES_METRIC);
    when(snapshotRepository.sumMeasurementValueForPeriod(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(key)))
        .thenReturn(sumValue);

    TallySummary summary = mapper.mapSnapshots(snapshot.getOrgId(), List.of(snapshot));

    TallyMeasurement measurement = findMeasurement(summary, ROSA_TAG, CORES_METRIC);
    assertEquals(snapshotValue, measurement.getValue(), "value should be the snapshot value");
    assertEquals(sumValue, measurement.getCurrentTotal(), "currentTotal should be the SQL SUM");
    verify(snapshotRepository)
        .sumMeasurementValueForPeriod(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(key));
  }

  @Test
  void shouldUseValueForGaugeMetric() {
    double snapshotValue = 1.0;
    TallySnapshot snapshot =
        buildSnapshot(
            RHEL_X86_TAG, Granularity.DAILY, BillingProvider._ANY, SOCKETS_METRIC, snapshotValue);

    TallySummary summary = mapper.mapSnapshots(snapshot.getOrgId(), List.of(snapshot));

    TallyMeasurement measurement = findMeasurement(summary, RHEL_X86_TAG, SOCKETS_METRIC);
    assertEquals(snapshotValue, measurement.getValue(), "value should be the snapshot value");
    assertEquals(
        snapshotValue,
        measurement.getCurrentTotal(),
        "currentTotal should equal value for gauge metrics");
    verify(snapshotRepository, never())
        .sumMeasurementValueForPeriod(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(TallyMeasurementKey.class));
  }

  @Test
  void shouldHandleMixedMetricTypesOnSameProduct() {
    double gaugeValue = 5.0;
    double counterValue = 4.0;
    double counterSum = 150.0;

    TallySnapshot gaugeSnapshot =
        buildSnapshot(
            ANSIBLE_AAP_TAG,
            Granularity.DAILY,
            BillingProvider.AWS,
            MANAGED_NODES_METRIC,
            gaugeValue);
    TallySnapshot counterSnapshot =
        buildSnapshot(
            ANSIBLE_AAP_TAG,
            Granularity.HOURLY,
            BillingProvider.AWS,
            INSTANCE_HOURS_METRIC,
            counterValue);

    var counterKey =
        new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, INSTANCE_HOURS_METRIC);
    when(snapshotRepository.sumMeasurementValueForPeriod(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(counterKey)))
        .thenReturn(counterSum);

    TallySummary summary = mapper.mapSnapshots("org1", List.of(gaugeSnapshot, counterSnapshot));

    TallyMeasurement gaugeMeasurement =
        findMeasurement(summary, ANSIBLE_AAP_TAG, MANAGED_NODES_METRIC);
    assertEquals(
        gaugeValue,
        gaugeMeasurement.getCurrentTotal(),
        "gauge currentTotal should equal snapshot value");

    TallyMeasurement counterMeasurement =
        findMeasurement(summary, ANSIBLE_AAP_TAG, INSTANCE_HOURS_METRIC);
    assertEquals(
        counterSum, counterMeasurement.getCurrentTotal(), "counter currentTotal should be SQL SUM");
  }

  @Test
  void shouldDefaultToCounterForUnknownProduct() {
    double snapshotValue = 42.0;
    double sumValue = 200.0;
    String unknownMetric = "UNKNOWN_METRIC";
    TallySnapshot snapshot =
        buildSnapshot(
            "unknown-product",
            Granularity.DAILY,
            BillingProvider._ANY,
            unknownMetric,
            snapshotValue);
    var key = new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, unknownMetric);
    when(snapshotRepository.sumMeasurementValueForPeriod(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(key)))
        .thenReturn(sumValue);

    TallySummary summary = mapper.mapSnapshots(snapshot.getOrgId(), List.of(snapshot));

    TallyMeasurement measurement = findMeasurement(summary, "unknown-product", unknownMetric);
    assertEquals(
        sumValue,
        measurement.getCurrentTotal(),
        "unknown product should default to counter (SQL SUM)");
    verify(snapshotRepository)
        .sumMeasurementValueForPeriod(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(key));
  }

  @Test
  void shouldMapSnapshotFieldsCorrectly() {
    double snapshotValue = 2.0;
    TallySnapshot snapshot =
        buildSnapshot(
            RHEL_X86_TAG,
            Granularity.DAILY,
            BillingProvider.RED_HAT,
            SOCKETS_METRIC,
            snapshotValue);

    TallySummary summary = mapper.mapSnapshots(snapshot.getOrgId(), List.of(snapshot));

    assertEquals(snapshot.getOrgId(), summary.getOrgId());
    var mapped = findSnapshot(summary, RHEL_X86_TAG).orElseThrow();
    assertEquals(snapshot.getId(), mapped.getId());
    assertEquals(snapshot.getProductId(), mapped.getProductId());
    assertEquals(snapshot.getSnapshotDate(), mapped.getSnapshotDate());
    assertEquals(snapshot.getGranularity().getValue(), mapped.getGranularity().value());
    assertEquals(snapshot.getServiceLevel().getValue(), mapped.getSla().value());
    assertEquals(snapshot.getUsage().getValue(), mapped.getUsage().value());
    assertEquals(snapshot.getBillingProvider().getValue(), mapped.getBillingProvider().value());
  }

  private TallySnapshot buildSnapshot(
      String productId,
      Granularity granularity,
      BillingProvider billingProvider,
      String metricId,
      double value) {
    Map<TallyMeasurementKey, Double> measurements = new HashMap<>();
    measurements.put(new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, metricId), value);

    return TallySnapshot.builder()
        .orgId("org-test")
        .productId(productId)
        .snapshotDate(OffsetDateTime.now())
        .tallyMeasurements(measurements)
        .granularity(granularity)
        .serviceLevel(ServiceLevel.PREMIUM)
        .usage(Usage.PRODUCTION)
        .billingProvider(billingProvider)
        .build();
  }

  private TallyMeasurement findMeasurement(
      TallySummary summary, String productId, String metricId) {
    return summary.getTallySnapshots().stream()
        .filter(s -> productId.equals(s.getProductId()))
        .flatMap(s -> s.getTallyMeasurements().stream())
        .filter(m -> metricId.equals(m.getMetricId()))
        .findFirst()
        .orElseThrow();
  }

  private Optional<org.candlepin.subscriptions.json.TallySnapshot> findSnapshot(
      TallySummary summary, String product) {
    return summary.getTallySnapshots().stream()
        .filter(s -> product.equals(s.getProductId()))
        .findFirst();
  }
}
