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
package com.redhat.swatch.billable.usage.kafka;

import static com.redhat.swatch.billable.usage.kafka.streams.StreamTopologyProducer.USAGE_TOTAL_AGGREGATED_METRIC;
import static org.candlepin.subscriptions.billable.usage.BillableUsageAggregate.FLUSH_ORG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redhat.swatch.billable.usage.kafka.streams.BillableUsageAggregationStreamProperties;
import com.redhat.swatch.billable.usage.kafka.streams.StreamTopologyProducer;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BillableUsageAggregateStreamTopologyTest {

  private static final String BILLABLE_USAGE_TOPIC = "billable-usage-topic";
  private static final String BILLABLE_USAGE_AGGREGATE_TOPIC = "billable-usage-aggregate-topic";
  private static final String BILLABLE_USAGE_STORE = "billable-usage-store";
  private static final String BILLABLE_USAGE_SUPPRESS_STORE = "billable-usage-suppress-store";
  private static final Duration WINDOW_DURATION = Duration.ofSeconds(1);
  private static final Duration GRACE_DURATION = Duration.ofSeconds(0);
  private static final String PRODUCT = "OpenShift-metrics";
  private static final String ORG_ID = "org123";
  private static final String ACCOUNT_ID = "testAccountId";
  private static final String LICENSE_A = "arn:aws:license-manager:us-east-1:1:license:a";
  private static final String LICENSE_B = "arn:aws:license-manager:us-east-1:1:license:b";

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private TopologyTestDriver testDriver;
  private TestInputTopic<String, BillableUsage> inputTopic;
  private TestOutputTopic<BillableUsageAggregateKey, BillableUsageAggregate> outputTopic;

  @BeforeEach
  void initializeTopology() {
    meterRegistry.clear();
    BillableUsageAggregationStreamProperties properties =
        new BillableUsageAggregationStreamProperties();
    properties.setBillableUsageSuppressStoreName(BILLABLE_USAGE_SUPPRESS_STORE);
    properties.setBillableUsageTopicName(BILLABLE_USAGE_TOPIC);
    properties.setBillableUsageHourlyAggregateTopicName(BILLABLE_USAGE_AGGREGATE_TOPIC);
    properties.setBillableUsageStoreName(BILLABLE_USAGE_STORE);
    properties.setWindowDuration(WINDOW_DURATION);
    properties.setGradeDuration(GRACE_DURATION);
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    StreamTopologyProducer topologyProducer =
        new StreamTopologyProducer(properties, objectMapper, meterRegistry);
    testDriver = new TopologyTestDriver(topologyProducer.buildTopology());

    var billableUsageSerde = new ObjectMapperSerde<>(BillableUsage.class, objectMapper);
    var billableUsageAggregateSerde =
        new ObjectMapperSerde<>(BillableUsageAggregate.class, objectMapper);
    var billableUsageAggregateKeySerde =
        new ObjectMapperSerde<>(BillableUsageAggregateKey.class, objectMapper);
    inputTopic =
        testDriver.createInputTopic(
            BILLABLE_USAGE_TOPIC, new StringSerializer(), billableUsageSerde.serializer());
    outputTopic =
        testDriver.createOutputTopic(
            BILLABLE_USAGE_AGGREGATE_TOPIC,
            billableUsageAggregateKeySerde.deserializer(),
            billableUsageAggregateSerde.deserializer());
  }

  @Test
  void testAggregateSingleBillableUsage() {
    var usage = createBillableUsage(ACCOUNT_ID, 36, snapshotAtHour(1));

    whenUsagesAreAggregated(usage);

    var aggregate = thenAggregateIsEmittedFor(usage);
    assertEquals(36.0, aggregate.getTotalValue().doubleValue());
    assertEquals(Set.of(usage.getSnapshotDate()), aggregate.getSnapshotDates());
    assertEquals(usage.getUuid().toString(), aggregate.getRemittanceUuids().get(0));
    assertNotNull(aggregate.getWindowTimestamp());
    assertUsageTotalAggregatedMetricIs(36.0);
  }

  @Test
  void testAggregateMultipleBillableUsage() {
    var usage1 = createBillableUsage(ACCOUNT_ID, 1, snapshotAtHour(1));
    var usage2 = createBillableUsage(ACCOUNT_ID, 3, snapshotAtHour(2));
    var usage3 = createBillableUsage(ACCOUNT_ID, 5, snapshotAtHour(3));

    whenUsagesAreAggregated(usage1, usage2, usage3);

    var aggregate = thenAggregateIsEmittedFor(usage1);
    assertEquals(9.0, aggregate.getTotalValue().doubleValue());
    assertEquals(
        Set.of(usage1.getSnapshotDate(), usage2.getSnapshotDate(), usage3.getSnapshotDate()),
        aggregate.getSnapshotDates());
    assertIterableEquals(remittanceUuidsOf(usage1, usage2, usage3), aggregate.getRemittanceUuids());
    assertNotNull(aggregate.getWindowTimestamp());
    assertUsageTotalAggregatedMetricIs(9.0);
  }

  @Test
  void testAggregateKeepsLatestLicenseIdForSameKey() {
    var usage1 = createBillableUsage(ACCOUNT_ID, 1, snapshotAtHour(1), LICENSE_A);
    var usage2 = createBillableUsage(ACCOUNT_ID, 3, snapshotAtHour(2), LICENSE_B);

    whenUsagesAreAggregated(usage1, usage2);

    var aggregate = thenAggregateIsEmittedFor(usage1);
    assertEquals(new BillableUsageAggregateKey(usage2), new BillableUsageAggregateKey(usage1));
    assertEquals(4.0, aggregate.getTotalValue().doubleValue());
    assertEquals(LICENSE_B, aggregate.getLicenseId());
    assertEquals(
        Set.of(usage1.getSnapshotDate(), usage2.getSnapshotDate()), aggregate.getSnapshotDates());
  }

  @Test
  void testAggregateKeepsNullLicenseIdWhenUsagesHaveNone() {
    var usage1 = createBillableUsage(ACCOUNT_ID, 2, snapshotAtHour(1));
    var usage2 = createBillableUsage(ACCOUNT_ID, 5, snapshotAtHour(2));

    whenUsagesAreAggregated(usage1, usage2);

    var aggregate = thenAggregateIsEmittedFor(usage1);
    assertNull(aggregate.getLicenseId());
    assertEquals(7.0, aggregate.getTotalValue().doubleValue());
  }

  @Test
  void testAggregateIgnoresNullLicenseAfterNonNull() {
    var usage1 = createBillableUsage(ACCOUNT_ID, 2, snapshotAtHour(1), LICENSE_A);
    var usage2 = createBillableUsage(ACCOUNT_ID, 5, snapshotAtHour(2), null);

    whenUsagesAreAggregated(usage1, usage2);

    var aggregate = thenAggregateIsEmittedFor(usage1);
    assertEquals(LICENSE_A, aggregate.getLicenseId());
    assertEquals(7.0, aggregate.getTotalValue().doubleValue());
  }

  @Test
  void testAggregateMultipleSubscriptionsBillableUsage() {
    var firstSubUsage1 = createBillableUsage("testAccountId1", 1, snapshotAtHour(1));
    var firstSubUsage2 = createBillableUsage("testAccountId1", 2, snapshotAtHour(2));
    var secondSubUsage1 = createBillableUsage("testAccountId2", 3, snapshotAtHour(1));
    var secondSubUsage2 = createBillableUsage("testAccountId2", 5, snapshotAtHour(2));

    whenUsagesAreAggregated(firstSubUsage1, secondSubUsage1, firstSubUsage2, secondSubUsage2);

    var firstRecord = outputTopic.readKeyValue();
    var secondRecord = outputTopic.readKeyValue();
    assertEquals(new BillableUsageAggregateKey(firstSubUsage1), firstRecord.key);
    assertEquals(new BillableUsageAggregateKey(secondSubUsage1), secondRecord.key);
    assertEquals(3.0, firstRecord.value.getTotalValue().doubleValue());
    assertEquals(8.0, secondRecord.value.getTotalValue().doubleValue());
    assertIterableEquals(
        remittanceUuidsOf(firstSubUsage1, firstSubUsage2), firstRecord.value.getRemittanceUuids());
    assertIterableEquals(
        remittanceUuidsOf(secondSubUsage1, secondSubUsage2),
        secondRecord.value.getRemittanceUuids());
    assertUsageTotalAggregatedMetricIs(11.0);
  }

  private void whenUsagesAreAggregated(BillableUsage... usages) {
    for (BillableUsage usage : usages) {
      inputTopic.pipeInput(ORG_ID, usage);
    }
    // Advance past the window, then publish the flush org to emit suppressed aggregates.
    inputTopic.advanceTime(WINDOW_DURATION.plusSeconds(5));
    var flushUsage = new BillableUsage();
    flushUsage.setOrgId(FLUSH_ORG);
    inputTopic.pipeInput(FLUSH_ORG, flushUsage);
  }

  private BillableUsageAggregate thenAggregateIsEmittedFor(BillableUsage usage) {
    KeyValue<BillableUsageAggregateKey, BillableUsageAggregate> record = outputTopic.readKeyValue();
    assertEquals(new BillableUsageAggregateKey(usage), record.key);
    return record.value;
  }

  private void assertUsageTotalAggregatedMetricIs(double expectedTotal) {
    var metric =
        getIngestedUsageAggregatedMetric(
            PRODUCT,
            MetricIdUtils.getCores().toUpperCaseFormatted(),
            BillableUsage.BillingProvider.AZURE.toString());
    assertTrue(metric.isPresent());
    assertEquals(metric.get().measure().iterator().next().getValue(), expectedTotal);
  }

  private static OffsetDateTime snapshotAtHour(int hour) {
    return OffsetDateTime.of(2024, 1, 1, hour, 1, 1, 1, ZoneOffset.UTC);
  }

  private static List<String> remittanceUuidsOf(BillableUsage... usages) {
    return Arrays.stream(usages)
        .map(usage -> usage.getUuid().toString())
        .collect(Collectors.toList());
  }

  private BillableUsage createBillableUsage(
      String billingAccountId, int value, OffsetDateTime snapshotDate) {
    return createBillableUsage(billingAccountId, value, snapshotDate, null);
  }

  private BillableUsage createBillableUsage(
      String billingAccountId, int value, OffsetDateTime snapshotDate, String licenseId) {
    var usage = new BillableUsage();
    usage.setOrgId(ORG_ID);
    usage.setProductId(PRODUCT);
    usage.setSnapshotDate(snapshotDate);
    usage.setUsage(BillableUsage.Usage.PRODUCTION);
    usage.setMetricId(MetricIdUtils.getCores().toUpperCaseFormatted());
    usage.setValue((double) value);
    usage.setSla(BillableUsage.Sla.PREMIUM);
    usage.setBillingProvider(BillableUsage.BillingProvider.AZURE);
    usage.setBillingAccountId(billingAccountId);
    usage.setUuid(UUID.randomUUID());
    usage.setLicenseId(licenseId);
    return usage;
  }

  private Optional<Meter> getIngestedUsageAggregatedMetric(
      String productTag, String metricId, String billingProvider) {
    return meterRegistry.getMeters().stream()
        .filter(
            m ->
                USAGE_TOTAL_AGGREGATED_METRIC.equals(m.getId().getName())
                    && productTag.equals(m.getId().getTag("product"))
                    && MetricId.fromString(metricId)
                        .getValue()
                        .equals(m.getId().getTag("metric_id"))
                    && billingProvider.equals(m.getId().getTag("billing_provider")))
        .findFirst();
  }
}
