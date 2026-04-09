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
package com.redhat.swatch.contract.resource;

import static com.redhat.swatch.contract.config.Channels.CONTRACTS_FROM_GATEWAY;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PartnerEntitlementKafkaMessageConsumerTest {

  private static final String VALID_JSON_MESSAGE =
      """
        {
          "action": "contract-updated",
          "redHatSubscriptionNumber": "12345678",
          "cloudIdentifiers": {
            "awsCustomerAccountId": "795061427196",
            "productCode": "test-product-code",
            "azureResourceId": "azure-resource-123"
          },
          "currentDimensions": [{
            "dimensionName": "test-dimension",
            "dimensionValue": "10"
          }]
        }
        """;

  @Inject @Any InMemoryConnector connector;

  @InjectSpy PartnerEntitlementKafkaMessageConsumer consumer;

  private InMemorySource<Object> contractsKafkaChannel;

  @BeforeEach
  void setUp() {
    contractsKafkaChannel = connector.source(CONTRACTS_FROM_GATEWAY);
  }

  @Test
  void shouldProcessStringMessage() {
    whenSendMessage(VALID_JSON_MESSAGE);
    assertMessageIsProcessed();
  }

  private void whenSendMessage(String message) {
    contractsKafkaChannel.send(message);
  }

  private void assertMessageIsProcessed() {
    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(() -> verify(consumer).consumeContract(anyString()));
  }

  private byte[] serializeString(String str) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(str);
    oos.flush();
    return baos.toByteArray();
  }
}
