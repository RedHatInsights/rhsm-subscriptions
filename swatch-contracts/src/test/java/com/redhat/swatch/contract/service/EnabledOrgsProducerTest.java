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
package com.redhat.swatch.contract.service;

import static com.redhat.swatch.contract.service.EnabledOrgsProducer.SUBSCRIPTION_PRUNE_TASK_TOPIC;
import static com.redhat.swatch.contract.service.EnabledOrgsProducer.SUBSCRIPTION_SYNC_TASK_TOPIC;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.contract.model.EnabledOrgsRequest;
import com.redhat.swatch.contract.test.resources.InMemoryMessageBrokerKafkaResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
class EnabledOrgsProducerTest {

  private static final String ENABLED_ORGS_CHANNEL = "enabled-orgs";

  @ConfigProperty(name = SUBSCRIPTION_SYNC_TASK_TOPIC)
  String subscriptionSyncTaskTopic;

  @ConfigProperty(name = SUBSCRIPTION_PRUNE_TASK_TOPIC)
  String subscriptionPruneTaskTopic;

  @Inject @Any InMemoryConnector connector;
  @Inject EnabledOrgsProducer producer;

  InMemorySink<EnabledOrgsRequest> sink;

  @BeforeEach
  void setup() {
    sink = connector.sink(ENABLED_ORGS_CHANNEL);
    sink.clear();
  }

  @Test
  void testSendTaskForSubscriptionsPrune() {
    producer.sendTaskForSubscriptionsPrune();
    verifyRequestContainsTargetTopic(subscriptionPruneTaskTopic);
  }

  @Test
  void testSendTaskForSubscriptionsSync() {
    producer.sendTaskForSubscriptionsSync();
    verifyRequestContainsTargetTopic(subscriptionSyncTaskTopic);
  }

  private void verifyRequestContainsTargetTopic(String expectedTopic) {
    var message = sink.received().get(0);
    assertEquals(expectedTopic, message.getPayload().getTargetTopic());
  }
}
