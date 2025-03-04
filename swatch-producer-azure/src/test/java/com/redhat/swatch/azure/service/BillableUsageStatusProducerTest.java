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
package com.redhat.swatch.azure.service;

import static com.redhat.swatch.azure.configuration.Channels.BILLABLE_USAGE_STATUS;
import static com.redhat.swatch.azure.test.resources.InMemoryMessageBrokerKafkaResource.IN_MEMORY_CONNECTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.azure.test.resources.InMemoryMessageBrokerKafkaResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.inject.Inject;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(InMemoryMessageBrokerKafkaResource.class)
class BillableUsageStatusProducerTest {

  @Inject
  @Connector(IN_MEMORY_CONNECTOR)
  InMemoryConnector connector;

  @Inject BillableUsageStatusProducer producer;
  InMemorySink<BillableUsageAggregate> source;

  @BeforeEach
  void setup() {
    source = connector.sink(BILLABLE_USAGE_STATUS);
    source.clear();
  }

  @Test
  void testSendThenHeaderIsAdded() {
    BillableUsageAggregate usage = new BillableUsageAggregate();
    producer.emitStatus(usage);
    assertEquals(1, source.received().size());
  }
}
