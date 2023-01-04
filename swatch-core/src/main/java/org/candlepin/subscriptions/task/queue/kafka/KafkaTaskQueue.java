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
package org.candlepin.subscriptions.task.queue.kafka;

import org.candlepin.subscriptions.task.JsonTaskMessage;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.queue.TaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * A task queue implementation that is backed by a kafka. Messages are sent to kafka when queued.
 * The topic that a task is published on is defined by TaskDescriptor.groupId.
 */
public class KafkaTaskQueue implements TaskQueue {

  private static final Logger log = LoggerFactory.getLogger(KafkaTaskQueue.class);

  private final KafkaTemplate<String, JsonTaskMessage> producer;

  public KafkaTaskQueue(KafkaTemplate<String, JsonTaskMessage> producer) {
    this.producer = producer;
    log.info("Creating Kafka task queue...");
  }

  @SuppressWarnings("squid:S4449")
  @Override
  public void enqueue(TaskDescriptor taskDescriptor) {
    log.info("Queuing task: {}", taskDescriptor);

    JsonTaskMessage msg =
        JsonTaskMessage.builder()
            .type(taskDescriptor.getTaskType().name())
            .groupId(taskDescriptor.getGroupId())
            .args(taskDescriptor.getTaskArgs())
            .build();

    // Message key is auto-generated.
    producer.send(taskDescriptor.getGroupId(), null, msg);
  }
}
