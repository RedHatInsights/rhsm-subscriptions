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
package org.candlepin.subscriptions.export;

import static org.candlepin.subscriptions.export.ExportConfiguration.SUBSCRIPTION_EXPORT_QUALIFIER;

import com.redhat.swatch.export.ExportRequestHandler;
import com.redhat.swatch.export.ExportServiceException;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Listener for Export messages from Kafka */
@Service
@Slf4j
public class ExportRequestConsumer extends SeekableKafkaConsumer {

  private final ExportRequestHandler exportService;

  protected ExportRequestConsumer(
      @Qualifier(SUBSCRIPTION_EXPORT_QUALIFIER) TaskQueueProperties taskQueueProperties,
      KafkaConsumerRegistry kafkaConsumerRegistry,
      ExportRequestHandler exportService) {
    super(taskQueueProperties, kafkaConsumerRegistry);
    this.exportService = exportService;
  }

  @Timed("rhsm-subscriptions.exports.upload")
  @Transactional(readOnly = true)
  @KafkaListener(
      id = "#{__listener.groupId}",
      topics = "#{__listener.topic}",
      containerFactory = "exportListenerContainerFactory")
  public void receive(String exportEvent) {
    try {
      exportService.handle(exportEvent);
    } catch (ExportServiceException ex) {
      log.error(
          "Error handling export request: {}. This request will be ignored. "
              + "See the previous errors for further details",
          exportEvent,
          ex);
    }
  }
}
