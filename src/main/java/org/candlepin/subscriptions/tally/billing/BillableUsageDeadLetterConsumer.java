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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class BillableUsageDeadLetterConsumer extends SeekableKafkaConsumer {

  public static final String RETRY_AFTER_HEADER = "retryAfter";

  private BillableUsageController billableUsageController;

  @Autowired
  public BillableUsageDeadLetterConsumer(
      @Qualifier("billableUsageDeadLetterTopicProperties")
          TaskQueueProperties billableUsageDeadLetterConsumerProperties,
      KafkaConsumerRegistry kafkaConsumerRegistry,
      BillableUsageController billableUsageController) {
    super(billableUsageDeadLetterConsumerProperties, kafkaConsumerRegistry);
    this.billableUsageController = billableUsageController;
  }

  @KafkaListener(
      id = "#{__listener.groupId}",
      topics = "#{__listener.topic}",
      containerFactory = "kafkaBillableUsageDeadLetterListenerContainerFactory")
  @Transactional(noRollbackFor = RuntimeException.class)
  public void receive(
      @Header(KafkaHeaders.BATCH_CONVERTED_HEADERS) List<Map<String, Object>> batchConvertedHeaders,
      @Payload BillableUsage usage) {
    var retryAfterHeader =
        batchConvertedHeaders.stream().map(header -> header.get(RETRY_AFTER_HEADER)).findFirst();
    if (retryAfterHeader.isPresent()) {
      var retryAfter = OffsetDateTime.parse(new String((byte[]) retryAfterHeader.get()));
      billableUsageController.updateBillableUsageRemittanceWithRetryAfter(usage, retryAfter);
    } else {
      log.debug("Message received with no retryAfter: {}", usage.toString());
    }
  }
}
