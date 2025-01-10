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
package com.redhat.swatch.billable.usage.kafka.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.arc.profile.UnlessBuildProfile;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.Suppressed.BufferConfig;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.WindowStore;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;

@AllArgsConstructor
@ApplicationScoped
@Slf4j
@UnlessBuildProfile("test")
public class StreamTopologyProducer {

  public static final String USAGE_TOTAL_METRIC = "swatch_billable_usage_total";

  private final BillableUsageAggregationStreamProperties properties;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  @Produces
  public Topology buildTopology() {
    StreamsBuilder builder = new StreamsBuilder();

    ObjectMapperSerde<BillableUsage> billableUsageSerde =
        new ObjectMapperSerde<>(BillableUsage.class, objectMapper);
    ObjectMapperSerde<BillableUsageAggregateKey> aggregationKeySerde =
        new ObjectMapperSerde<>(BillableUsageAggregateKey.class, objectMapper);
    ObjectMapperSerde<BillableUsageAggregate> aggregationSerde =
        new ObjectMapperSerde<>(BillableUsageAggregate.class, objectMapper);

    builder.stream(
            properties.getBillableUsageTopicName(),
            Consumed.with(Serdes.String(), billableUsageSerde))
        .groupBy(
            (k, v) -> new BillableUsageAggregateKey(v),
            Grouped.with(aggregationKeySerde, billableUsageSerde))
        .windowedBy(
            TimeWindows.ofSizeAndGrace(
                properties.getWindowDuration(), properties.getGradeDuration()))
        .aggregate(
            BillableUsageAggregate::new,
            (key, value, billableUsageAggregate) -> billableUsageAggregate.updateFrom(value),
            Materialized
                .<BillableUsageAggregateKey, BillableUsageAggregate, WindowStore<Bytes, byte[]>>as(
                    properties.getBillableUsageStoreName())
                .withKeySerde(aggregationKeySerde)
                .withValueSerde(aggregationSerde)
                // we don't need a changelog topic for this since they will be stored in the
                // suppress state store
                .withLoggingDisabled())
        // Need some analysis on BufferConfig size
        .suppress(
            Suppressed.untilWindowCloses(BufferConfig.unbounded())
                .withName(properties.getBillableUsageSuppressStoreName()))
        .toStream()
        .peek(this::traceAggregate)
        .to(properties.getBillableUsageHourlyAggregateTopicName());

    return builder.build();
  }

  private void traceAggregate(
      Windowed<BillableUsageAggregateKey> key, BillableUsageAggregate aggregate) {
    log.info("Sending aggregate to hourly topic: {}", aggregate);
    if (key.key().getProductId() != null && key.key().getMetricId() != null) {
      // add metrics for aggregation
      var counter = Counter.builder(USAGE_TOTAL_METRIC);
      if (key.key().getBillingProvider() != null) {
        counter.tag("billing_provider", key.key().getBillingProvider());
      }

      if (aggregate.getStatus() != null) {
        counter.tag("status", aggregate.getStatus().toString());
      }

      counter
          .withRegistry(meterRegistry)
          .withTags("product", key.key().getProductId(), "metric_id", key.key().getMetricId())
          .increment(aggregate.getTotalValue().doubleValue());
    }
  }
}
