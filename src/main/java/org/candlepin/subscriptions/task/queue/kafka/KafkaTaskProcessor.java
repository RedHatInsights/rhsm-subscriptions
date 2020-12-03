/*
 * Copyright (c) 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.task.queue.kafka;

import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskExecutionException;
import org.candlepin.subscriptions.task.TaskFactory;
import org.candlepin.subscriptions.task.TaskType;
import org.candlepin.subscriptions.task.TaskWorker;
import org.candlepin.subscriptions.task.queue.TaskConsumer;
import org.candlepin.subscriptions.task.queue.kafka.message.TaskMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;

import io.micrometer.core.annotation.Timed;

/**
 * Responsible for receiving task messages from Kafka when they become available.
 */
public class KafkaTaskProcessor implements TaskConsumer {
    private static final Logger log = LoggerFactory.getLogger(KafkaTaskProcessor.class);

    private final TaskWorker worker;
    private final String groupId;
    private final String topic;

    public KafkaTaskProcessor(TaskFactory taskFactory, String groupId, String topic) {
        worker = new TaskWorker(taskFactory);
        this.groupId = groupId;
        this.topic = topic;
    }

    @KafkaListener(id = "#{__listener.groupId}",
        topics = "#{__listener.topic}")
    @Timed("rhsm-subscriptions.task.execution")
    public void receive(TaskMessage taskMessage, Acknowledgment acknowledgment) {
        try {
            log.info("Message received from kafka: {}", taskMessage);
            worker.executeTask(describe(taskMessage));
        }
        catch (TaskExecutionException e) {
            // If a task fails to execute for any reason, it is logged and will
            // not get retried.
            log.error("Failed to execute task: {}", taskMessage, e);
        }
        finally {
            // We always ack the message regardless of if there are failures.
            // There is no need to retry the message on failure since the task
            // can either be manually re-triggered or will run on the next schedule.
            acknowledgment.acknowledge();
        }
    }

    private TaskDescriptor describe(TaskMessage message) throws TaskExecutionException {
        try {
            return TaskDescriptor.builder(TaskType.valueOf(message.getType()), message.getGroupId())
                       .setArgs(message.getArgs()).build();
        }
        catch (IllegalArgumentException | NullPointerException e) {
            throw new TaskExecutionException(
                String.format("Unknown TaskType received from message: %s", message.getType()));
        }
    }

    public String getGroupId() {
        return groupId;
    }

    public String getTopic() {
        return topic;
    }
}
