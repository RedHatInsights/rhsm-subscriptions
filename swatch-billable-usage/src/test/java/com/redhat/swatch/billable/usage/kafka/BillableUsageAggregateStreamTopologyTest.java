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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.common.serialization.StringSerializer;
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

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private ObjectMapperSerde<BillableUsage> billableUsageSerde;
  private ObjectMapperSerde<BillableUsageAggregate> billableUsageAggregateSerde;
  private ObjectMapperSerde<BillableUsageAggregateKey> billableUsageAggregateKeySerde;
  private TopologyTestDriver testDriver;

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
    this.testDriver = new TopologyTestDriver(topologyProducer.buildTopology());
    this.billableUsageSerde = new ObjectMapperSerde<>(BillableUsage.class, objectMapper);
    this.billableUsageAggregateSerde =
        new ObjectMapperSerde<>(BillableUsageAggregate.class, objectMapper);
    this.billableUsageAggregateKeySerde =
        new ObjectMapperSerde<>(BillableUsageAggregateKey.class, objectMapper);
  }

  @Test
  void testAggregateSingleBillableUsage() {
    TestInputTopic<String, BillableUsage> inputTopic =
        testDriver.createInputTopic(
            BILLABLE_USAGE_TOPIC, new StringSerializer(), billableUsageSerde.serializer());
    TestOutputTopic<BillableUsageAggregateKey, BillableUsageAggregate> outputTopic =
        testDriver.createOutputTopic(
            BILLABLE_USAGE_AGGREGATE_TOPIC,
            billableUsageAggregateKeySerde.deserializer(),
            billableUsageAggregateSerde.deserializer());
    var snapshotDate = OffsetDateTime.of(2024, 1, 1, 1, 1, 1, 1, ZoneOffset.UTC);
    var usage = createBillableUsage("testAccountId", 36, snapshotDate);
    inputTopic.pipeInput("org123", usage);
    // Make the window pass then input a new message to trigger sending suppressed messages.
    inputTopic.advanceTime(WINDOW_DURATION.plusSeconds(5));
    inputTopic.pipeInput("org124", usage);
    var expectedAggregateKey = new BillableUsageAggregateKey(usage);
    var keyValue = outputTopic.readKeyValue();
    var actualAggregate = keyValue.value;
    assertEquals(expectedAggregateKey, keyValue.key);
    assertEquals(36.0, actualAggregate.getTotalValue().doubleValue());
    assertEquals(Set.of(usage.getSnapshotDate()), actualAggregate.getSnapshotDates());
    assertEquals(usage.getUuid().toString(), actualAggregate.getRemittanceUuids().get(0));
    assertNotNull(actualAggregate.getWindowTimestamp());
    assertUsageTotalAggregatedMetricIs(36.0);
  }

  @Test
  void testAggregateMultipleBillableUsage() {
    TestInputTopic<String, BillableUsage> inputTopic =
        testDriver.createInputTopic(
            BILLABLE_USAGE_TOPIC, new StringSerializer(), billableUsageSerde.serializer());
    TestOutputTopic<BillableUsageAggregateKey, BillableUsageAggregate> outputTopic =
        testDriver.createOutputTopic(
            BILLABLE_USAGE_AGGREGATE_TOPIC,
            billableUsageAggregateKeySerde.deserializer(),
            billableUsageAggregateSerde.deserializer());
    var snapshotDate1 = OffsetDateTime.of(2024, 1, 1, 1, 1, 1, 1, ZoneOffset.UTC);
    var snapshotDate2 = OffsetDateTime.of(2024, 1, 1, 2, 1, 1, 1, ZoneOffset.UTC);
    var snapshotDate3 = OffsetDateTime.of(2024, 1, 1, 3, 1, 1, 1, ZoneOffset.UTC);
    var usage1 = createBillableUsage("testAccountId", 1, snapshotDate1);
    var usage2 = createBillableUsage("testAccountId", 3, snapshotDate2);
    var usage3 = createBillableUsage("testAccountId", 5, snapshotDate3);
    inputTopic.pipeInput("org123", usage1);
    inputTopic.pipeInput("org123", usage2);
    inputTopic.pipeInput("org123", usage3);
    // Make the window pass then input a new message to trigger sending suppressed messages.
    inputTopic.advanceTime(WINDOW_DURATION.plusSeconds(5));
    inputTopic.pipeInput("org123", usage1);
    var expectedAggregateKey = new BillableUsageAggregateKey(usage1);
    var keyValue = outputTopic.readKeyValue();
    var actualAggregate = keyValue.value;
    assertEquals(expectedAggregateKey, keyValue.key);
    assertEquals(9.0, actualAggregate.getTotalValue().doubleValue());
    assertEquals(
        Set.of(snapshotDate1, snapshotDate2, snapshotDate3), actualAggregate.getSnapshotDates());
    assertIterableEquals(
        List.of(
            usage1.getUuid().toString(), usage2.getUuid().toString(), usage3.getUuid().toString()),
        actualAggregate.getRemittanceUuids());
    assertNotNull(actualAggregate.getWindowTimestamp());
    assertUsageTotalAggregatedMetricIs(9.0);
  }

  @Test
  void testAggregateMultipleSubscriptionsBillableUsage() {
    TestInputTopic<String, BillableUsage> inputTopic =
        testDriver.createInputTopic(
            BILLABLE_USAGE_TOPIC, new StringSerializer(), billableUsageSerde.serializer());
    TestOutputTopic<BillableUsageAggregateKey, BillableUsageAggregate> outputTopic =
        testDriver.createOutputTopic(
            BILLABLE_USAGE_AGGREGATE_TOPIC,
            billableUsageAggregateKeySerde.deserializer(),
            billableUsageAggregateSerde.deserializer());
    var snapshotDate1 = OffsetDateTime.of(2024, 1, 1, 1, 1, 1, 1, ZoneOffset.UTC);
    var snapshotDate2 = OffsetDateTime.of(2024, 1, 1, 2, 1, 1, 1, ZoneOffset.UTC);
    var firstSubUsage1 = createBillableUsage("testAccountId1", 1, snapshotDate1);
    var fistSubUsage2 = createBillableUsage("testAccountId1", 2, snapshotDate2);
    var secondSubUsage1 = createBillableUsage("testAccountId2", 3, snapshotDate1);
    var secondSubUsage2 = createBillableUsage("testAccountId2", 5, snapshotDate2);
    inputTopic.pipeInput("org123", firstSubUsage1);
    inputTopic.pipeInput("org123", secondSubUsage1);
    inputTopic.pipeInput("org123", fistSubUsage2);
    inputTopic.pipeInput("org123", secondSubUsage2);
    // Make the window pass then input a new message to trigger sending suppressed messages.
    inputTopic.advanceTime(WINDOW_DURATION.plusSeconds(5));
    inputTopic.pipeInput("org123", firstSubUsage1);
    var expectedFirstAggregateKey = new BillableUsageAggregateKey(firstSubUsage1);
    var expectedSecondAggregateKey = new BillableUsageAggregateKey(secondSubUsage1);
    var firstRecord = outputTopic.readKeyValue();
    var secondRecord = outputTopic.readKeyValue();
    var actualFirstAggregate = firstRecord.value;
    var actualSecondAggregate = secondRecord.value;
    assertEquals(expectedFirstAggregateKey, firstRecord.key);
    assertEquals(expectedSecondAggregateKey, secondRecord.key);
    assertEquals(3.0, actualFirstAggregate.getTotalValue().doubleValue());
    assertEquals(8.0, actualSecondAggregate.getTotalValue().doubleValue());
    assertIterableEquals(
        List.of(firstSubUsage1.getUuid().toString(), fistSubUsage2.getUuid().toString()),
        actualFirstAggregate.getRemittanceUuids());
    assertIterableEquals(
        List.of(secondSubUsage1.getUuid().toString(), secondSubUsage2.getUuid().toString()),
        actualSecondAggregate.getRemittanceUuids());
    assertUsageTotalAggregatedMetricIs(11.0);
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

  private BillableUsage createBillableUsage(
      String billingAccountId, int value, OffsetDateTime snapshotDate) {
    var usage = new BillableUsage();
    usage.setOrgId("org123");
    usage.setProductId(PRODUCT);
    usage.setSnapshotDate(snapshotDate);
    usage.setUsage(BillableUsage.Usage.PRODUCTION);
    usage.setMetricId(MetricIdUtils.getCores().toUpperCaseFormatted());
    usage.setValue((double) value);
    usage.setSla(BillableUsage.Sla.PREMIUM);
    usage.setBillingProvider(BillableUsage.BillingProvider.AZURE);
    usage.setBillingAccountId(billingAccountId);
    usage.setUuid(UUID.randomUUID());
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
