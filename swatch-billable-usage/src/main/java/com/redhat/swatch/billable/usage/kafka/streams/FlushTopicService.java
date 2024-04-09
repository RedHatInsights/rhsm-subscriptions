package com.redhat.swatch.billable.usage.kafka.streams;

import com.redhat.swatch.billable.usage.model.BillableUsage;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;


public class FlushTopicService {


  @ConfigProperty(name = "KAFKA_BILLABLE_USAGE_PARTITIONS")
  int billableUsagePartitionSize;

  @Channel("billable-usage-aggregation-repartition")
  Emitter<BillableUsage> emitter;

  public void sendFlushToBillableUsageRepartitionTopic() {
    var key = new BillableUsageAggregateKey();
    key.setOrgId("flush");
    var usage = new BillableUsage();
    usage.setOrgId("flush");
    for(int partition = 0; partition < billableUsagePartitionSize; partition++) {
      emitter.send( Message.of(usage)
          .addMetadata(OutgoingKafkaRecordMetadata.<BillableUsageAggregateKey>builder()
              .withKey(key)
              .withPartition(partition)
              .build())
      );
    }
  }

}
