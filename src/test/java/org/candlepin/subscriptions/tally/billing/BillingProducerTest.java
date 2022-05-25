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

import static org.mockito.Mockito.verify;

import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class BillingProducerTest {

  @Mock private KafkaTemplate<String, BillableUsage> kafka;

  private TaskQueueProperties billableUsageTopicProps;
  private BillingProducer producer;

  @BeforeEach
  void setupTest() {
    billableUsageTopicProps = new TaskQueueProperties();
    billableUsageTopicProps.setTopic("billable-usage");

    this.producer = new BillingProducer(billableUsageTopicProps, kafka,
        billableUsageRemittanceRepository);
  }

  @Test
  void testTallySummaryPassThrough() {
    BillableUsage usage = new BillableUsage();
    producer.produce(usage);
    verify(kafka).send(billableUsageTopicProps.getTopic(), usage);
  }
}
