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
import static org.mockito.Mockito.verifyNoInteractions;

import com.redhat.swatch.configuration.registry.MetricId;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.TallyMeasurement;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.util.MetricIdUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.support.RetryTemplate;

@ExtendWith(MockitoExtension.class)
class SnapshotSummaryProducerTest {

  @Mock private KafkaTemplate<String, TallySummary> kafka;

  @Captor private ArgumentCaptor<TallySummary> summaryCaptor;

  private TallySummaryProperties props;

  private SnapshotSummaryProducer producer;

  @BeforeEach
  void setup() {
    props = new TallySummaryProperties();
    props.setTopic("summary-topic");
    RetryTemplate retryTemplate = new RetryTemplate();
    this.producer =
        new SnapshotSummaryProducer(kafka, retryTemplate, props, new TallySummaryMapper());
  }

  @Test
  void testProduceSummary() {
    Map<String, List<TallySnapshot>> updateMap = new HashMap<>();
    updateMap.put(
        "org1",
        List.of(
            buildSnapshot(
                "a1",
                "org1",
                "OSD",
                Granularity.HOURLY,
                ServiceLevel.PREMIUM,
                Usage.PRODUCTION,
                BillingProvider.RED_HAT,
                MetricIdUtils.getCores().getValue(),
                20.4)));
    updateMap.put(
        "org2",
        List.of(
            buildSnapshot(
                "a2",
                "org2",
                "OCP",
                Granularity.HOURLY,
                ServiceLevel.PREMIUM,
                Usage.PRODUCTION,
                BillingProvider.AWS,
                MetricIdUtils.getCores().getValue(),
                22.2)));
    producer.produceTallySummaryMessages(updateMap);
    verify(kafka, times(2)).send(eq(props.getTopic()), any(), summaryCaptor.capture());

    List<TallySummary> summaries = summaryCaptor.getAllValues();
    assertEquals(2, summaries.size());
    Map<String, List<TallySummary>> results =
        summaries.stream().collect(Collectors.groupingBy(TallySummary::getAccountNumber));
    assertSummary(
        results,
        "a1",
        "org1",
        "OSD",
        Granularity.HOURLY,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        MetricIdUtils.getCores(),
        20.4);
    assertSummary(
        results,
        "a2",
        "org2",
        "OCP",
        Granularity.HOURLY,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        MetricIdUtils.getCores(),
        22.2);
  }

  void assertSummary(
      Map<String, List<TallySummary>> results,
      String account,
      String orgId,
      String product,
      Granularity granularity,
      ServiceLevel sla,
      Usage usage,
      MetricId uom,
      double value) {
    assertTrue(results.containsKey(account));
    List<TallySummary> accountSummaries = results.get(account);
    assertEquals(1, accountSummaries.size());
    TallySummary expectedSummary = accountSummaries.get(0);
    assertEquals(account, expectedSummary.getAccountNumber());
    assertEquals(orgId, expectedSummary.getOrgId());

    assertEquals(1, expectedSummary.getTallySnapshots().size());
    org.candlepin.subscriptions.json.TallySnapshot snapshot =
        expectedSummary.getTallySnapshots().get(0);
    assertEquals(product, snapshot.getProductId());
    assertEquals(granularity.toString(), snapshot.getGranularity().value().toUpperCase());
    assertEquals(sla.toString(), snapshot.getSla().value().toUpperCase());
    assertEquals(usage.toString(), snapshot.getUsage().value().toUpperCase());
    assertEquals(2, snapshot.getTallyMeasurements().size());

    Map<String, List<TallyMeasurement>> measurements =
        snapshot.getTallyMeasurements().stream()
            .collect(Collectors.groupingBy(TallyMeasurement::getHardwareMeasurementType));
    assertMeasurement(measurements, "TOTAL", uom, value);
    assertMeasurement(measurements, "PHYSICAL", uom, value);
  }

  @Test
  void testSummarySkippedWhenItHasNoMeasurements() {
    Map<String, List<TallySnapshot>> updateMap = new HashMap<>();
    updateMap.put(
        "a1",
        List.of(
            buildSnapshot(
                "a1",
                "org_1",
                "OSD",
                Granularity.HOURLY,
                ServiceLevel.PREMIUM,
                Usage.PRODUCTION,
                BillingProvider.RED_HAT,
                MetricIdUtils.getCores().getValue(),
                20.4)));
    updateMap.get("a1").get(0).getTallyMeasurements().clear();
    producer.produceTallySummaryMessages(updateMap);
    verifyNoInteractions(kafka);
  }

  void assertMeasurement(
      Map<String, List<TallyMeasurement>> measurements,
      String hardwareType,
      MetricId metricId,
      double value) {
    Optional<List<TallyMeasurement>> optionalTotal =
        Optional.ofNullable(measurements.get(hardwareType));
    assertTrue(optionalTotal.isPresent());
    assertEquals(1, optionalTotal.get().size());
    TallyMeasurement measurement = optionalTotal.get().get(0);

    assertEquals(hardwareType, measurement.getHardwareMeasurementType());
    assertEquals(metricId.getValue().toUpperCase(), measurement.getUom());
    assertEquals(value, measurement.getValue());
  }

  TallySnapshot buildSnapshot(
      String account,
      String orgId,
      String productId,
      Granularity granularity,
      ServiceLevel sla,
      Usage usage,
      BillingProvider billingProvider,
      String metricId,
      double val) {
    Map<TallyMeasurementKey, Double> measurements = new HashMap<>();
    measurements.put(new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, metricId), val);
    measurements.put(new TallyMeasurementKey(HardwareMeasurementType.TOTAL, metricId), val);
    return TallySnapshot.builder()
        .accountNumber(account)
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
}
