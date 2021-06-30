package org.candlepin.subscriptions.subscription;

import io.micrometer.core.annotation.Timed;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.kafka.annotation.KafkaListener;

public class SyncSubscriptionsWorker extends SeekableKafkaConsumer {

    protected SyncSubscriptionsWorker(
            TaskQueueProperties taskQueueProperties,
            KafkaConsumerRegistry kafkaConsumerRegistry) {
        super(taskQueueProperties, kafkaConsumerRegistry);
    }

    @Timed("rhsm-subscriptions.marketplace.tally-summary")
    @KafkaListener(
            id = "#{__listener.groupId}",
            topics = "#{__listener.topic}",
            containerFactory = "kafkaTallySummaryListenerContainerFactory")
    public void receive(SyncSubscriptionsTask syncSubscriptionsTask) {
        syncSubscriptionsTask.execute();
    }
}
