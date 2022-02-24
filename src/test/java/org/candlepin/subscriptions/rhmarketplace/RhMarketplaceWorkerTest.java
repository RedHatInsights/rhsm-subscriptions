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

import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.rhmarketplace.api.model.UsageEvent;
import org.candlepin.subscriptions.rhmarketplace.api.model.UsageRequest;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RhMarketplaceWorkerTest {

  @Test
  void testWorkerCallsProduceForNonEmptyPayload() {
    TaskQueueProperties properties = new TaskQueueProperties();
    RhMarketplaceProducer producer = mock(RhMarketplaceProducer.class);
    RhMarketplacePayloadMapper payloadMapper = mock(RhMarketplacePayloadMapper.class);
    KafkaConsumerRegistry kafkaConsumerRegistry = new KafkaConsumerRegistry();
    var worker =
        new RhMarketplaceWorker(properties, producer, payloadMapper, kafkaConsumerRegistry);

    UsageRequest usageRequest = new UsageRequest().data(List.of(new UsageEvent()));
    when(payloadMapper.createUsageRequest(any())).thenReturn(usageRequest);

    worker.receive(new TallySummary());

    verify(producer, times(1)).submitUsageRequest(usageRequest);
  }

  @Test
  void testWorkerSkipsEmptyPayloads() {
    TaskQueueProperties properties = new TaskQueueProperties();
    RhMarketplaceProducer producer = mock(RhMarketplaceProducer.class);
    RhMarketplacePayloadMapper payloadMapper = mock(RhMarketplacePayloadMapper.class);
    KafkaConsumerRegistry kafkaConsumerRegistry = mock(KafkaConsumerRegistry.class);
    var worker =
        new RhMarketplaceWorker(properties, producer, payloadMapper, kafkaConsumerRegistry);

    UsageRequest usageRequest = new UsageRequest().data(Collections.emptyList());
    when(payloadMapper.createUsageRequest(any())).thenReturn(usageRequest);

    worker.receive(new TallySummary());

    verify(producer, times(0)).submitUsageRequest(any());
  }

  @Test
  void testWorkerSkipsNullPayloads() {
    TaskQueueProperties properties = new TaskQueueProperties();
    RhMarketplaceProducer producer = mock(RhMarketplaceProducer.class);
    RhMarketplacePayloadMapper payloadMapper = mock(RhMarketplacePayloadMapper.class);
    KafkaConsumerRegistry kafkaConsumerRegistry = mock(KafkaConsumerRegistry.class);
    var worker =
        new RhMarketplaceWorker(properties, producer, payloadMapper, kafkaConsumerRegistry);

    when(payloadMapper.createUsageRequest(any())).thenReturn(null);

    worker.receive(new TallySummary());

    verify(producer, times(0)).submitUsageRequest(any());
  }
}
