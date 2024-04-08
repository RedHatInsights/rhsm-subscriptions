package com.redhat.swatch.azure.kafka.streams;

import com.redhat.swatch.azure.openapi.model.BillableUsage;
import io.smallrye.mutiny.Multi;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

public class KafkaFlushService {

  @ConfigProperty(name = "KSTREAM_BILLABLE_USAGE_FLUSH_PARTITION_SIZE")
  int flushPartitionSize;

  @Channel("billable-usage-aggregation-repartition")
  Emitter<Message<BillableUsage>> emitter;

  public void sendFlushToBillableUsageRepartitionTopic() {
    var key = new BillableUsageAggregateKey();
    key.setOrgId("flush");
    var usage = new BillableUsage();
    usage.setOrgId("flush");
    for(int partition = 0; partition < flushPartitionSize; partition++) {
      emitter.send( Message.of(usage)
          .addMetadata(OutgoingKafkaRecordMetadata.<BillableUsageAggregateKey>builder()
              .withKey(key)
              .withPartition(partition)
              .build())
      );
    }
  }


}
