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
package org.candlepin.subscriptions.metering.service.prometheus;

import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.BaseEvent;
import org.candlepin.subscriptions.json.CleanUpEvent;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/** Component that produces Map<EventKey, Event> events based on prometheus metrics. */
@Service
@Slf4j
public class PrometheusEventsProducer {
  private final String topic;
  private final KafkaTemplate<String, BaseEvent> template;

  @Autowired
  public PrometheusEventsProducer(
      @Qualifier("serviceInstanceTopicProperties")
          TaskQueueProperties taskServiceInstanceTopicProperties,
      @Qualifier("prometheusUsageKafkaTemplate") KafkaTemplate<String, BaseEvent> template) {
    this.topic = taskServiceInstanceTopicProperties.getTopic();
    this.template = template;
  }

  public void produce(BaseEvent event) {
    if (event instanceof Event eventToSend) {
      log.debug(
          "Sending event {} for organization {} to topic {}",
          eventToSend.getEventId(),
          event.getOrgId(),
          topic);
    } else if (event instanceof CleanUpEvent) {
      log.debug("Sending clean-up event for organization {} to topic {}", event.getOrgId(), topic);
    }

    template.send(topic, event.getOrgId(), event);
  }
}
