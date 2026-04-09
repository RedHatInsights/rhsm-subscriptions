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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
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
import org.candlepin.subscriptions.json.TallySummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TallySummaryMapperTest {

  @Mock TallySnapshotRepository snapshotRepository;
  @Mock ApplicationClock clock;
  @InjectMocks TallySummaryMapper mapper;
  private Map<TallyMeasurementKey, Double> expectedTotalValues = new HashMap<>();

  @Test
  void testMapSnapshots() {
    String org = "O1";
    TallySnapshot rosa =
        buildSnapshot(
            org,
            "rosa",
            Granularity.HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            MetricIdUtils.getInstanceHours().getValue(),
            2);
    TallySnapshot rhel =
        buildSnapshot(
            org,
            "RHEL for x86",
            Granularity.HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            MetricIdUtils.getSockets().getValue(),
            24);

    var snapshots = List.of(rosa, rhel);
    TallySummary summary = mapper.mapSnapshots(org, snapshots);
    assertEquals(org, summary.getOrgId());
    List<org.candlepin.subscriptions.json.TallySnapshot> summarySnaps = summary.getTallySnapshots();
    assertEquals(snapshots.size(), summarySnaps.size());

    var mappedRosaOptional = findSnapshot(summary, "rosa");
    assertTrue(mappedRosaOptional.isPresent());
    assertMappedSnapshot(rosa, mappedRosaOptional.get());

    var mappedRhelOptional = findSnapshot(summary, "RHEL for x86");
    assertTrue(mappedRhelOptional.isPresent());
    assertMappedSnapshot(rhel, mappedRhelOptional.get());
  }

  void assertMappedSnapshot(
      TallySnapshot expected, org.candlepin.subscriptions.json.TallySnapshot mapped) {
    assertEquals(expected.getId(), mapped.getId());
    assertEquals(expected.getBillingAccountId(), mapped.getBillingAccountId());
    assertEquals(expected.getBillingProvider().getValue(), mapped.getBillingProvider().value());
    assertEquals(expected.getGranularity().getValue(), mapped.getGranularity().value());
    assertEquals(expected.getProductId(), mapped.getProductId());
    assertEquals(expected.getSnapshotDate(), mapped.getSnapshotDate());
    assertEquals(expected.getServiceLevel().getValue(), mapped.getSla().value());
    assertEquals(expected.getUsage().getValue(), mapped.getUsage().value());

    var expectedMeasurements = expected.getTallyMeasurements();
    var mappedMeasurements = mapped.getTallyMeasurements();
    assertEquals(expectedMeasurements.size(), mappedMeasurements.size());

    mappedMeasurements.forEach(
        m -> {
          HardwareMeasurementType type =
              HardwareMeasurementType.valueOf(m.getHardwareMeasurementType());
          TallyMeasurementKey key = new TallyMeasurementKey(type, m.getMetricId());
          assertTrue(expectedMeasurements.containsKey(key));
          Double expectedValue = expectedMeasurements.get(key);
          assertEquals(expectedValue, m.getValue());
          assertEquals(expectedTotalValues.get(key), m.getCurrentTotal());
          verify(snapshotRepository)
              .sumMeasurementValueForPeriod(
                  eq(expected.getOrgId()),
                  eq(expected.getProductId()),
                  eq(Granularity.HOURLY),
                  eq(expected.getServiceLevel()),
                  eq(expected.getUsage()),
                  eq(expected.getBillingProvider()),
                  eq(expected.getBillingAccountId()),
                  any(),
                  eq(expected.getSnapshotDate()),
                  eq(key));
        });
    verify(clock, times(mappedMeasurements.size())).startOfMonth(expected.getSnapshotDate());
  }

  TallySnapshot buildSnapshot(
      String orgId,
      String productId,
      Granularity granularity,
      ServiceLevel sla,
      Usage usage,
      BillingProvider billingProvider,
      String metricId,
      double val) {
    Map<TallyMeasurementKey, Double> measurements = new HashMap<>();
    buildMeasurement(measurements, HardwareMeasurementType.PHYSICAL, metricId, val);
    buildMeasurement(measurements, HardwareMeasurementType.TOTAL, metricId, val);

    return TallySnapshot.builder()
        .orgId(orgId)
        .productId(productId)
        .snapshotDate(OffsetDateTime.now())
        .tallyMeasurements(measurements)
        .granularity(granularity)
        .serviceLevel(sla)
        .usage(usage)
        .billingProvider(billingProvider)
        .build();
  }

  void buildMeasurement(
      Map<TallyMeasurementKey, Double> measurements,
      HardwareMeasurementType hardwareType,
      String metricId,
      double value) {
    var measurementKey = new TallyMeasurementKey(hardwareType, metricId);
    measurements.put(measurementKey, value);
    expectedTotalValues.put(measurementKey, value * 2);
    when(snapshotRepository.sumMeasurementValueForPeriod(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(measurementKey)))
        .thenReturn(value * 2);
  }

  Optional<org.candlepin.subscriptions.json.TallySnapshot> findSnapshot(
      TallySummary summary, String product) {
    return summary.getTallySnapshots().stream()
        .filter(s -> product.equalsIgnoreCase(s.getProductId()))
        .findFirst();
  }

  @Test
  void testFilterInvalidMeasurements() {
    String org = "O1";

    // Create a snapshot with both valid and invalid measurements
    Map<TallyMeasurementKey, Double> measurements = new HashMap<>();

    // Valid measurement for rosa: Instance-hours
    String validMetric = MetricIdUtils.getInstanceHours().getValue();
    buildMeasurement(measurements, HardwareMeasurementType.PHYSICAL, validMetric, 10.0);
    buildMeasurement(measurements, HardwareMeasurementType.TOTAL, validMetric, 10.0);

    // Invalid measurement for rosa: Sockets (this is a RHEL metric, not ROSA)
    String invalidMetric = MetricIdUtils.getSockets().getValue();
    var invalidKey1 = new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, invalidMetric);
    var invalidKey2 = new TallyMeasurementKey(HardwareMeasurementType.TOTAL, invalidMetric);
    measurements.put(invalidKey1, 5.0);
    measurements.put(invalidKey2, 5.0);

    TallySnapshot snapshot =
        TallySnapshot.builder()
            .orgId(org)
            .productId("rosa")
            .snapshotDate(OffsetDateTime.now())
            .tallyMeasurements(measurements)
            .granularity(Granularity.HOURLY)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .billingProvider(BillingProvider.AWS)
            .build();

    // Map the snapshot
    TallySummary summary = mapper.mapSnapshots(org, List.of(snapshot));

    // Verify the summary
    assertEquals(org, summary.getOrgId());
    assertEquals(1, summary.getTallySnapshots().size());

    var mappedSnapshot = summary.getTallySnapshots().getFirst();
    assertEquals("rosa", mappedSnapshot.getProductId());

    // Should only have 2 measurements (PHYSICAL and TOTAL for Instance-hours)
    // The invalid Sockets measurements should be filtered out
    assertEquals(2, mappedSnapshot.getTallyMeasurements().size());

    // Verify all measurements are for the valid metric (in uppercase format)
    String expectedMetricId = MetricIdUtils.toUpperCaseFormatted(validMetric);
    mappedSnapshot
        .getTallyMeasurements()
        .forEach(m -> assertEquals(expectedMetricId, m.getMetricId()));
  }
}
