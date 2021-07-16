package org.candlepin.subscriptions.subscription;

import io.micrometer.core.annotation.Timed;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionWorker extends SeekableKafkaConsumer {

    protected SubscriptionWorker(
            @Qualifier("subscriptionTasks") TaskQueueProperties taskQueueProperties,
            KafkaConsumerRegistry kafkaConsumerRegistry) {
        super(taskQueueProperties, kafkaConsumerRegistry);
    }

    String task;

    @KafkaListener(id = "subscription-worker",
            topics = "platform.rhsm-subscriptions.sync",
            containerFactory = "subscriptionSyncListenerContainerFactory")
    public void receive(SyncSubscriptionsTask syncSubscriptionsTask) {
        task = syncSubscriptionsTask.toString();
        System.out.println("received sync subscriptions task"+task.toString());
        syncSubscriptionsTask.execute();
    }
}
