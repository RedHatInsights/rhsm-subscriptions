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

import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(
    prefix = "rhsm-subscriptions.billing-producer",
    name = "heartbeat-enabled",
    havingValue = "true")
public class BillableUsageHeartbeatProducer {

  private final KafkaTemplate<String, BillableUsage> billableUsageKafkaTemplate;
  private final String billableUsageTopic;

  private final BillableUsage flushUsage = new BillableUsage().withOrgId("flush");

  @Autowired
  public BillableUsageHeartbeatProducer(
      @Qualifier("billableUsageTopicProperties") TaskQueueProperties billableUsageTopicProperties,
      @Qualifier("billableUsageKafkaTemplate")
          KafkaTemplate<String, BillableUsage> billableUsageKafkaTemplate) {
    this.billableUsageKafkaTemplate = billableUsageKafkaTemplate;
    this.billableUsageTopic = billableUsageTopicProperties.getTopic();
  }

  @Scheduled(fixedRate = 500)
  public void produce() {
    billableUsageKafkaTemplate.send(billableUsageTopic, flushUsage);
  }
}
