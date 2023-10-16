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
package org.candlepin.subscriptions.rhmarketplace;

import io.micrometer.core.annotation.Timed;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/*
 * A Kafka message consumer that consumes messages from the billable-usage topic and sends
 * the usage data to Red Hat Marketplace.
 */
@Service
@Slf4j
public class RhMarketplaceBillableUsageWorker extends SeekableKafkaConsumer {

  private RhMarketplacePayloadMapper rhMarketplacePayloadMapper;
  private RhMarketplaceProducer producer;

  @Autowired
  RhMarketplaceBillableUsageWorker(
      @Qualifier("rhmBillableUsageTopicProperties") TaskQueueProperties taskQueueProperties,
      RhMarketplaceProducer producer,
      RhMarketplacePayloadMapper rhMarketplacePayloadMapper,
      KafkaConsumerRegistry kafkaConsumerRegistry) {
    super(taskQueueProperties, kafkaConsumerRegistry);
    this.rhMarketplacePayloadMapper = rhMarketplacePayloadMapper;
    this.producer = producer;
  }

  @Timed("rhsm-subscriptions.marketplace.billable-usage")
  @KafkaListener(
      id = "#{__listener.groupId}",
      topics = "#{__listener.topic}",
      containerFactory = "kafkaBillableUsageListenerContainerFactory")
  public void receive(BillableUsage usage) {
    log.debug("Billable Usage received by RHM for orgId {}!", usage.getOrgId());
    Optional.ofNullable(rhMarketplacePayloadMapper.createUsageRequest(usage))
        .filter(s -> !s.getData().isEmpty())
        .ifPresent(producer::submitUsageRequest);
  }
}
