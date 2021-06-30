package org.candlepin.subscriptions.subscription;

import io.micrometer.core.annotation.Timed;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.kafka.annotation.KafkaListener;

public class SubscriptionWorker extends SeekableKafkaConsumer {

    protected SubscriptionWorker(
            TaskQueueProperties taskQueueProperties,
            KafkaConsumerRegistry kafkaConsumerRegistry) {
        super(taskQueueProperties, kafkaConsumerRegistry);
    }

    @KafkaListener(id = "subscription-worker", topics = "#{__listener.topic}")
    public void receive(SyncSubscriptionsTask syncSubscriptionsTask) {
        syncSubscriptionsTask.execute();
    }
}
