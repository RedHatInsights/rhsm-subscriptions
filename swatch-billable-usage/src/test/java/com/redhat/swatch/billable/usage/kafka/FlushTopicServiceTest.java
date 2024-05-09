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
package com.redhat.swatch.billable.usage.kafka;

import static com.redhat.swatch.billable.usage.configuration.Channels.BILLABLE_USAGE_AGGREGATION_OUT;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.redhat.swatch.billable.usage.kafka.streams.FlushTopicService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
class FlushTopicServiceTest {

  @Inject @Any InMemoryConnector connector;

  @InjectSpy FlushTopicService service;

  @Test
  void testFlushTopics() {
    InMemorySink<BillableUsage> results = connector.sink(BILLABLE_USAGE_AGGREGATION_OUT);
    service.sendFlushToBillableUsageRepartitionTopic();
    assertFalse(results.received().isEmpty());
  }
}
