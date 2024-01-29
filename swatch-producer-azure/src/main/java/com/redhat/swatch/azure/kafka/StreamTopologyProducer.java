package com.redhat.swatch.azure.kafka;

import com.redhat.swatch.azure.openapi.model.BillableUsage;
import com.redhat.swatch.azure.service.BillableUsageConsumer;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.HashMap;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.Suppressed.BufferConfig;
import org.apache.kafka.streams.kstream.Suppressed.StrictBufferConfig;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.WindowedSerdes;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;

@ApplicationScoped
public class StreamTopologyProducer {

  static final String BILLABLE_USAGE_STORE = "billable-usage-store";

  private static final String BILLABLE_USAGE_TOPIC = "platform.rhsm-subscriptions.billable-usage";

  @Inject
  private BillableUsageConsumer billableUsageConsumer;

  @Produces
  public Topology buildTopology() {
    StreamsBuilder builder = new StreamsBuilder();

    ObjectMapperSerde<BillableUsage> billableUsageSerde = new ObjectMapperSerde<>(
        BillableUsage.class);
    //var windowedAggregationKeySerde = WindowedSerdes.timeWindowedSerdeFrom(BillableUsageAggregationKey.class, TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10)).size());
   var aggregationKeySerde = new ObjectMapperSerde<>(BillableUsageAggregationKey.class);
    ObjectMapperSerde<BillableUsageAggregation> aggregationSerde = new ObjectMapperSerde<>(BillableUsageAggregation.class);

    KeyValueBytesStoreSupplier storeSupplier = Stores.persistentKeyValueStore(
        BILLABLE_USAGE_STORE);


    builder.stream(
            BILLABLE_USAGE_TOPIC,
            Consumed.with(Serdes.String(), billableUsageSerde)
        )
        .groupBy((k, v) -> new BillableUsageAggregationKey(v))
        .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10)))
        .aggregate(
            BillableUsageAggregation::new,
            (key, value, billableUsageAggregation) -> billableUsageAggregation.updateFrom(value),
            Materialized.<BillableUsageAggregationKey, BillableUsageAggregation, WindowStore<Bytes, byte[]>>as(BILLABLE_USAGE_STORE)
                .withKeySerde(aggregationKeySerde)
                .withValueSerde(aggregationSerde)
        )
        //Need some analysis on BufferConfig size
        .suppress(Suppressed.untilTimeLimit(Duration.ofSeconds(10), BufferConfig.unbounded()))
        .toStream()
        .process(new BillableUsageAggregateProcessorSupplier(billableUsageConsumer), storeSupplier.name());

    return builder.build();
  }

}
