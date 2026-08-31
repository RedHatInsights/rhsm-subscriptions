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

import static com.redhat.swatch.contract.config.Channels.OFFERING_SYNC_TASK_SERVICE_UMB;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.contract.config.FeatureFlags;
import com.redhat.swatch.contract.openapi.model.OperationalProductEvent;
import com.redhat.swatch.contract.test.LoggerCaptor;
import com.redhat.swatch.contract.test.resources.EnableUmbResource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
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
@TestProfile(EnableUmbResource.class)
class ProductStatusUMBMessageConsumerTest {

  static final String VALID_JSON_MESSAGE =
      """
        {
          "occurredOn": "2023-05-29T16:00:54.279Z",
          "productCode": "RH0180191",
          "productCategory": "Parent SKU",
          "eventType": "Create"
        }
        """;

  @InjectMock OfferingSyncService offeringSyncService;
  @InjectMock FeatureFlags featureFlags;
  @Inject @Any InMemoryConnector connector;
  @InjectSpy ProductStatusUMBMessageConsumer consumer;

  private InMemorySource<Object> productUmbChannel;

  @BeforeAll
  static void configureLogging() {
    LoggerCaptor.registerHandler(ProductStatusUMBMessageConsumer.class);
  }

  @BeforeEach
  void setUp() {
    productUmbChannel = connector.source(OFFERING_SYNC_TASK_SERVICE_UMB);
    LoggerCaptor.clearRecords();
    when(featureFlags.isProductServiceUmbConsumerEnabled()).thenReturn(true);
  }

  @Test
  void shouldDeserializeLogAndSyncProduct() {
    whenSendMessage(VALID_JSON_MESSAGE);
    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              verify(consumer).consumeProduct(VALID_JSON_MESSAGE);
              LoggerCaptor.thenInfoLogWithMessage("IT Product message consumed: source=umb");
              verify(offeringSyncService).syncProductFromEvent(any(OperationalProductEvent.class));
            });
  }

  @Test
  void shouldIgnoreMessagesWhenFeatureFlagIsDisabled() {
    when(featureFlags.isProductServiceUmbConsumerEnabled()).thenReturn(false);
    consumer.consumeMessage(VALID_JSON_MESSAGE);
    verify(consumer, never()).consumeProduct(VALID_JSON_MESSAGE);
    verify(offeringSyncService, never()).syncProductFromEvent(any(OperationalProductEvent.class));
  }

  private void whenSendMessage(String message) {
    productUmbChannel.send(message);
  }
}
