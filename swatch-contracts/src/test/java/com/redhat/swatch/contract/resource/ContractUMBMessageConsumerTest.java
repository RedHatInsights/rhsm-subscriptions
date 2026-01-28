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

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.redhat.swatch.contract.model.PartnerEntitlementsRequest;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import com.redhat.swatch.contract.service.ContractService;
import com.redhat.swatch.contract.test.resources.EnableUmbResource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
@TestProfile(EnableUmbResource.class)
class ContractUMBMessageConsumerTest {

  private static final String CONTRACTS_CHANNEL = "contracts";
  private static final String VALID_JSON_MESSAGE =
      """
        {
          "redHatSubscriptionNumber": "12345678",
          "cloudIdentifiers": {
            "awsCustomerId": "aws-customer-123",
            "productCode": "test-product"
          },
          "currentDimensions": [{
            "dimensionName": "test-dimension",
            "dimensionValue": "10"
          }]
        }
        """;

  @InjectMock ContractService contractService;

  @Inject @Any InMemoryConnector connector;

  private InMemorySource<Object> contractsChannel;

  @BeforeEach
  void setUp() {
    contractsChannel = connector.source(CONTRACTS_CHANNEL);
    Mockito.reset(contractService);

    // Configure mock to return a success response
    StatusResponse mockResponse = new StatusResponse();
    mockResponse.setMessage("Contract created");
    Mockito.when(contractService.createPartnerContract(any(PartnerEntitlementsRequest.class)))
        .thenReturn(mockResponse);
  }

  @Test
  void shouldProcessStringMessage() {
    whenSendMessage(VALID_JSON_MESSAGE);
    assertMessageIsProcessed();
  }

  @Test
  void shouldProcessUtf8ByteArrayMessage() {
    byte[] messageBytes = VALID_JSON_MESSAGE.getBytes(StandardCharsets.UTF_8);

    whenSendMessage(messageBytes);

    assertMessageIsProcessed();
  }

  @Test
  void shouldProcessSerializedObjectMessage() throws Exception {
    byte[] serializedBytes = serializeString(VALID_JSON_MESSAGE);

    whenSendMessage(serializedBytes);

    assertMessageIsProcessed();
  }

  private void whenSendMessage(Object message) {
    contractsChannel.send(message);
  }

  private void assertMessageIsProcessed() {
    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(
            () ->
                verify(contractService)
                    .createPartnerContract(any(PartnerEntitlementsRequest.class)));
  }

  private byte[] serializeString(String str) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(str);
    oos.flush();
    return baos.toByteArray();
  }
}
