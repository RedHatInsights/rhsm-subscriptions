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

import static com.redhat.swatch.contract.config.Channels.IT_SUBSCRIPTION_SYNC;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.contract.config.FeatureFlags;
import com.redhat.swatch.contract.openapi.model.SubscriptionOutboxPayload;
import com.redhat.swatch.contract.test.LoggerCaptor;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@QuarkusTest
class SubscriptionKafkaMessageConsumerTest {

  private static final String SUBSCRIPTION_JSON =
      """
      {
        "eventId": "evt-1",
        "createdAt": 1704067200000,
        "entityType": "Subscription",
        "eventType": "CREATE",
        "eventVersion": "v1.0",
        "payload": {
          "subscriptionNumber": "1234",
          "customerId": "org123",
          "quantity": 10,
          "effectiveStartDate": 1704067200000,
          "effectiveEndDate": 1735689600000,
          "product": {
            "sku": "RH00001",
            "childProducts": [
              {"sku": "RH00002", "status": "Active"}
            ]
          }
        }
      }
      """;

  private static final String CANONICAL_MESSAGE_XML =
      """
      <CanonicalMessage>
        <Payload>
          <Sync>
            <Subscription>
              <Identifiers>
                <Identifier system="SUBSCRIPTION" entity-name="Subscription" qualifier="number">1234</Identifier>
              </Identifiers>
              <Quantity>10</Quantity>
            </Subscription>
          </Sync>
        </Payload>
      </CanonicalMessage>
      """;

  @InjectMock FeatureFlags featureFlags;
  @InjectMock SubscriptionSyncService service;
  @InjectSpy SubscriptionKafkaMessageConsumer consumer;
  @Inject @Any InMemoryConnector connector;

  private InMemorySource<Object> subscriptionKafkaChannel;

  @BeforeAll
  static void configureLogging() {
    LoggerCaptor.registerHandler(SubscriptionKafkaMessageConsumer.class);
  }

  @BeforeEach
  void setUp() {
    subscriptionKafkaChannel = connector.source(IT_SUBSCRIPTION_SYNC);
    LoggerCaptor.clearRecords();
    when(featureFlags.isItSubscriptionServiceKafkaConsumerEnabled()).thenReturn(true);
  }

  @Test
  void shouldProcessOutboxJsonMessage() {
    whenSendMessage(SUBSCRIPTION_JSON);

    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(() -> verify(consumer).consumeSubscription(SUBSCRIPTION_JSON));
    thenKafkaSubscriptionDeserializedSuccessfully();
    ArgumentCaptor<SubscriptionOutboxPayload> captor =
        ArgumentCaptor.forClass(SubscriptionOutboxPayload.class);
    verify(service).saveSubscription(captor.capture());
    SubscriptionOutboxPayload saved = captor.getValue();
    assertEquals("1234", saved.getSubscriptionNumber());
    assertEquals("org123", saved.getCustomerId());
    assertEquals("RH00001", saved.getProduct().getSku());
    assertEquals(10, saved.getQuantity());
    assertEquals(1704067200000L, saved.getEffectiveStartDate());
    assertEquals(1735689600000L, saved.getEffectiveEndDate());
  }

  @Test
  void shouldNotTreatCanonicalMessageXmlAsValid() {
    whenSendMessage(CANONICAL_MESSAGE_XML);

    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(() -> verify(consumer).consumeSubscription(CANONICAL_MESSAGE_XML));
    LoggerCaptor.thenWarnLogWithMessage("Unable to read IT Subscription Kafka message from JSON.");
    verify(service, never()).saveSubscription(any());
  }

  @Test
  void shouldNotLogSuccessWhenMalformedJson() {
    whenSendMessage("this is not json");

    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(() -> verify(consumer).consumeSubscription("this is not json"));
    LoggerCaptor.thenWarnLogWithMessage("Unable to read IT Subscription Kafka message from JSON.");
    verify(service, never()).saveSubscription(any());
  }

  @Test
  void shouldIgnoreNullMessage() throws Exception {
    consumer.consumeMessage(null);

    LoggerCaptor.thenLogNothing();
    verify(service, never()).saveSubscription(any());
  }

  @Test
  void shouldIgnoreMessagesWhenFeatureFlagIsDisabled() throws Exception {
    when(featureFlags.isItSubscriptionServiceKafkaConsumerEnabled()).thenReturn(false);
    whenSendMessage(SUBSCRIPTION_JSON);
    assertMessageIsNotProcessed();
    verify(service, after(500).never()).saveSubscription(any());
  }

  @Test
  void shouldIgnoreNonSubscriptionEntityType() {
    String otherEntity =
        """
        {"eventId":"evt-1","entityType":"Offering","payload":{}}
        """;
    whenSendMessage(otherEntity);

    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(() -> verify(consumer).consumeSubscription(otherEntity));
    verify(service, never()).saveSubscription(any());
  }

  @Test
  void shouldIgnoreNullEventEnvelope() {
    whenSendMessage("null");

    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(() -> verify(consumer).consumeSubscription("null"));
    verify(service, never()).saveSubscription(any());
  }

  private void whenSendMessage(String message) {
    subscriptionKafkaChannel.send(message);
  }

  private void assertMessageIsNotProcessed() throws Exception {
    verify(consumer, after(500).never()).consumeSubscription(SUBSCRIPTION_JSON);
  }

  private void thenKafkaSubscriptionDeserializedSuccessfully() {
    LoggerCaptor.thenInfoLogWithMessage("IT Subscription message consumed: source=kafka");
    LoggerCaptor.thenNoErrorLogWithMessage(
        "Unable to read IT Subscription Kafka message from JSON.");
  }
}
