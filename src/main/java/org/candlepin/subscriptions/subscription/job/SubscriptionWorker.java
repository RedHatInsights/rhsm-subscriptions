package org.candlepin.subscriptions.subscription.job;

import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskQueueProperties;

import org.candlepin.subscriptions.task.TaskType;
import org.candlepin.subscriptions.task.queue.kafka.message.TaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.Setter;

@Service
public class SubscriptionWorker {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionWorker.class);

    @Getter
    @Setter
    private String topic;

    private final SubscriptionTaskFactory subscriptionTaskFactory;

    @Autowired
    public SubscriptionWorker(@Qualifier("subscriptionTaskQueueProperties")
        TaskQueueProperties taskQueueProperties, SubscriptionTaskFactory subscriptionTaskFactory) {
        log.info("Got topic:{}", taskQueueProperties.getTopic());
        this.topic = taskQueueProperties.getTopic();
        this.subscriptionTaskFactory = subscriptionTaskFactory;
    }

    @KafkaListener(id = "subscription-worker", topics = "#{__listener.topic}")
    public void handleSubscriptionTasks(TaskMessage taskMessage) {
        log.info("Got message: {}", taskMessage);
        final TaskDescriptor descriptor = TaskDescriptor.builder(TaskType.valueOf(taskMessage.getType()),
            topic).setArgs(taskMessage.getArgs()).build();

        subscriptionTaskFactory.build(descriptor).execute();
    }
}
