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

import static com.redhat.swatch.contract.config.Channels.IT_OFFERING_SYNC;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.redhat.swatch.contract.test.LoggerCaptor;
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
class ProductStatusKafkaMessageConsumerTest {

  private static final String VALID_JSON_MESSAGE =
      """
        {
          "occurredOn": "2023-05-29T16:00:54.279Z",
          "productCode": "RH0180191",
          "productCategory": "Parent SKU",
          "eventType": "Create"
        }
        """;

  private static final String CHILD_SKU_JSON_MESSAGE =
      """
        {
          "occurredOn": "2023-05-29T16:00:54.279Z",
          "productCode": "SVCRH01",
          "productCategory": "Child SKU",
          "eventType": "Update"
        }
        """;

  @Inject @Any InMemoryConnector connector;
  @InjectSpy ProductStatusKafkaMessageConsumer consumer;

  private InMemorySource<Object> productKafkaChannel;

  @BeforeAll
  static void configureLogging() {
    LoggerCaptor.registerHandler(ProductStatusKafkaMessageConsumer.class);
  }

  @BeforeEach
  void setUp() {
    productKafkaChannel = connector.source(IT_OFFERING_SYNC);
    LoggerCaptor.clearRecords();
  }

  @Test
  void shouldDeserializeAndLogParentSkuMessage() {
    whenSendMessage(VALID_JSON_MESSAGE);
    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              verify(consumer).consumeProduct(VALID_JSON_MESSAGE);
              LoggerCaptor.thenInfoLogWithMessage("IT Product message consumed: source=kafka");
            });
  }

  @Test
  void shouldDeserializeAndLogChildSkuMessage() {
    whenSendMessage(CHILD_SKU_JSON_MESSAGE);
    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              verify(consumer).consumeProduct(CHILD_SKU_JSON_MESSAGE);
              LoggerCaptor.thenInfoLogWithMessage("IT Product message consumed: source=kafka");
            });
  }

  @Test
  void shouldNotLogSuccessWhenMessageIsInvalidJson() {
    whenSendMessage("not-valid-json");
    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              verify(consumer).consumeProduct("not-valid-json");
              LoggerCaptor.thenWarnLogWithMessage("Unable to read IT Product Kafka message");
            });
  }

  @Test
  void shouldIgnoreNullMessage() throws Exception {
    consumer.consumeMessage(null);

    LoggerCaptor.thenLogNothing();
    verify(consumer, never()).consumeProduct(anyString());
  }

  private void whenSendMessage(String message) {
    productKafkaChannel.send(message);
  }
}
