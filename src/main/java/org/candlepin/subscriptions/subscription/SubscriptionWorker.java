package org.candlepin.subscriptions.subscription;

import io.micrometer.core.annotation.Timed;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

public class SubscriptionWorker extends SeekableKafkaConsumer {

    protected SubscriptionWorker(
            TaskQueueProperties taskQueueProperties,
            KafkaConsumerRegistry kafkaConsumerRegistry) {
        super(taskQueueProperties, kafkaConsumerRegistry);
    }

    String task;

    @KafkaListener(id = "subscription-worker", topics = "platform.rhsm-subscriptions.sync")
    public void receive(SyncSubscriptionsTask syncSubscriptionsTask) {
        task = syncSubscriptionsTask.toString();
        syncSubscriptionsTask.execute();
    }
}
