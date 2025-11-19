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
package org.candlepin.subscriptions.tally.events;

import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.tally.TallySnapshotController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for the "lburnett" topic that processes individual events for metric usage
 * collection instead of batch processing.
 */
@Service
@Slf4j
public class LburnettEventConsumer {

  private final TallySnapshotController tallySnapshotController;

  @Autowired
  public LburnettEventConsumer(TallySnapshotController tallySnapshotController) {
    this.tallySnapshotController = tallySnapshotController;
  }

  /**
   * Consumes events from the "lburnett" topic and processes them individually for metric usage
   * collection and calculation.
   *
   * @param event the Event to process for metric usage calculation
   */
  @KafkaListener(
      topics = "lburnett",
      groupId = "lburnett-event-processor",
      containerFactory = "eventKafkaListenerContainerFactory")
  @Transactional
  public void consumeEvent(@Payload Event event) {
    try {
      log.debug(
          "Received event from lburnett topic: eventId={}, orgId={}, serviceType={}",
          event.getEventId(),
          event.getOrgId(),
          event.getServiceType());

      // Process the individual event for metric usage collection
      tallySnapshotController.processIndividualEvent(event);

      log.debug(
          "Successfully processed event: eventId={}, orgId={}",
          event.getEventId(),
          event.getOrgId());
    } catch (Exception e) {
      log.error(
          "Failed to process event from lburnett topic: eventId={}, orgId={}",
          event.getEventId(),
          event.getOrgId(),
          e);
      throw e; // Re-throw to trigger retry/dead letter handling
    }
  }
}
