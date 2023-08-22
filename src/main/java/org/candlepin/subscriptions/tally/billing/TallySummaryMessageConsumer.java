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

import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * Processes messages on the TallySummary topic and delegates to the BillingProducer for processing.
 */
@Service
@Slf4j
public class TallySummaryMessageConsumer extends SeekableKafkaConsumer {

  private BillableUsageMapper billableUsageMapper;
  private BillableUsageController billableUsageController;
  private RetryTemplate retry;

  @Autowired
  public TallySummaryMessageConsumer(
      @Qualifier("billingProducerTallySummaryTopicProperties")
          TaskQueueProperties tallySummaryTopicProperties,
      KafkaConsumerRegistry kafkaConsumerRegistry,
      BillableUsageMapper billableUsageMapper,
      BillableUsageController billableUsageController,
      @Qualifier("billingProducerKafkaRetryTemplate") RetryTemplate retry) {
    super(tallySummaryTopicProperties, kafkaConsumerRegistry);
    this.billableUsageMapper = billableUsageMapper;
    this.billableUsageController = billableUsageController;
    this.retry = retry;
  }

  @Timed("rhsm-subscriptions.billing-producer.tally-summary")
  @KafkaListener(
      id = "#{__listener.groupId}",
      topics = "#{__listener.topic}",
      containerFactory = "billingProducerKafkaTallySummaryListenerContainerFactory")
  @Transactional
  public void receive(
      @Payload TallySummary tallySummary,
      @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String kafkaMessageKey) {
    log.debug("Tally Summary received w/ key={}. Producing billable usage.", kafkaMessageKey);

    billableUsageMapper
        .fromTallySummary(tallySummary)
        .forEach(
            usage -> retry.execute(
                context -> {
                  billableUsageController.submitBillableUsage(usage);
                  return null;
                }));
  }
}
