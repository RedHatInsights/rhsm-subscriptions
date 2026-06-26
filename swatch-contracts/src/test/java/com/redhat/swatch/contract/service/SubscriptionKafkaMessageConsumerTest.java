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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.contract.config.FeatureFlags;
import com.redhat.swatch.contract.product.umb.UmbSubscription;
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

@QuarkusTest
class SubscriptionKafkaMessageConsumerTest {

  private static final String SUBSCRIPTION_XML =
      """
      <CanonicalMessage>
        <Payload>
          <Sync>
            <Subscription>
              <Identifiers>
                <Reference system="EBS" entity-name="Account" qualifier="number">account123</Reference>
                <Reference system="WEB" entity-name="Customer" qualifier="id">org123_ICUST</Reference>
                <Identifier system="SUBSCRIPTION" entity-name="Subscription" qualifier="number">1234</Identifier>
              </Identifiers>
              <Quantity>10</Quantity>
              <effectiveStartDate>2024-01-01T00:00:00</effectiveStartDate>
              <effectiveEndDate>2025-01-01T00:00:00</effectiveEndDate>
              <Product>
                <Sku>RH00001</Sku>
                <Product>
                  <Status>
                    <State>Active</State>
                  </Status>
                </Product>
              </Product>
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
  void shouldProcessMessage() {
    whenSendMessage(SUBSCRIPTION_XML);

    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(() -> verify(consumer).consumeSubscription(SUBSCRIPTION_XML));
    thenKafkaSubscriptionDeserializedSuccessfully();
    verify(service).saveUmbSubscription(any(UmbSubscription.class));
  }

  @Test
  void shouldNotLogSuccessWhenMalformedXml() {
    whenSendMessage("this is not xml");

    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(() -> verify(consumer).consumeSubscription("this is not xml"));
    LoggerCaptor.thenWarnLogWithMessage("Unable to process IT Subscription Kafka message");
    verify(service, never()).saveUmbSubscription(any(UmbSubscription.class));
  }

  @Test
  void shouldIgnoreNullMessage() throws Exception {
    consumer.consumeFromKafka(null);

    LoggerCaptor.thenLogNothing();
    verify(service, never()).saveUmbSubscription(any(UmbSubscription.class));
  }

  @Test
  void shouldIgnoreMessagesWhenFeatureFlagIsDisabled() throws Exception {
    when(featureFlags.isItSubscriptionServiceKafkaConsumerEnabled()).thenReturn(false);
    whenSendMessage(SUBSCRIPTION_XML);
    assertMessageIsNotProcessed();
    verify(service, after(500).never()).saveUmbSubscription(any(UmbSubscription.class));
  }

  private void whenSendMessage(String message) {
    subscriptionKafkaChannel.send(message);
  }

  private void assertMessageIsNotProcessed() throws Exception {
    verify(consumer, after(500).never()).consumeSubscription(SUBSCRIPTION_XML);
  }

  private void thenKafkaSubscriptionDeserializedSuccessfully() {
    LoggerCaptor.thenInfoLogWithMessage("IT Subscription message consumed: source=kafka");
    LoggerCaptor.thenNoErrorLogWithMessage("Unable to process IT Subscription Kafka message");
  }
}
