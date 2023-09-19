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
package org.candlepin.subscriptions.tally.billing;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ServiceInstanceMessageConsumer extends SeekableKafkaConsumer {

  private final EventController eventController;

  @Autowired
  public ServiceInstanceMessageConsumer(
      @Qualifier("serviceInstanceTopicProperties")
          TaskQueueProperties taskServiceInstanceTopicProperties,
      KafkaConsumerRegistry kafkaConsumerRegistry,
      EventController eventController) {
    super(taskServiceInstanceTopicProperties, kafkaConsumerRegistry);
    this.eventController = eventController;
  }

  @KafkaListener(
      id = "#{__listener.groupId}",
      topics = "#{__listener.topic}",
      containerFactory = "kafkaServiceInstanceListenerContainerFactory")
  @Transactional(noRollbackFor = RuntimeException.class)
  public void receive(@Payload Set<String> events) {
    log.info("Events received w/ event list size={}. Consuming events.", events.size());
    eventController.persistServiceInstances(events);
  }
}
