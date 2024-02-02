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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillableUsageDeadLetterConsumerTest {

  @Mock BillableUsageController controller;
  BillableUsageDeadLetterConsumer consumer;

  @BeforeEach
  void init() {
    TaskQueueProperties queueProperties = new TaskQueueProperties();
    KafkaConsumerRegistry kafkaConsumerRegistry = new KafkaConsumerRegistry();
    this.consumer =
        new BillableUsageDeadLetterConsumer(queueProperties, kafkaConsumerRegistry, controller);
  }

  @Test
  void testConsumeMessageWithRetryAttemptHeader() {
    var headers = new ArrayList<Map<String, Object>>();
    var headerMap = new HashMap<String, Object>();
    headerMap.put(
        BillableUsageDeadLetterConsumer.RETRY_AFTER_HEADER,
        OffsetDateTime.now().toString().getBytes());
    headers.add(headerMap);
    consumer.receive(headers, new BillableUsage());
    verify(controller).updateBillableUsageRemittanceWithRetryAfter(any(), any());
  }

  @Test
  void testConsumeMessageWithNoRetryAttemptHeader() {
    var headers = new ArrayList<Map<String, Object>>();
    consumer.receive(headers, new BillableUsage());
    verifyNoInteractions(controller);
  }
}
