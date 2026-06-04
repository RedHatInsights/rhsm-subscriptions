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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.contract.config.FeatureFlags;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ContractsPartnerEntitlementMessageConsumerTest {

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

  /**
   * Partner Gateway contract-updated payload shape with extra top-level licenseArn (SWATCH-4946).
   */
  private static final String JSON_WITH_EXTRA_LICENSE_ARN_FIELD =
      """
        {
          "action": "contract-updated",
          "licenseArn": "arn:aws:license-manager:us-east-1:000000000000:license:swatch-test-license",
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

  private static final LoggerCaptor LOGGER_CAPTOR = new LoggerCaptor();

  @Inject @Any InMemoryConnector connector;

  @InjectMock FeatureFlags featureFlags;
  @InjectSpy ContractsPartnerEntitlementMessageConsumer consumer;

  private InMemorySource<Object> contractsKafkaChannel;

  @BeforeAll
  static void configureLogging() {
    LogContext.getLogContext()
        .getLogger(ContractsPartnerEntitlementMessageConsumer.class.getName())
        .addHandler(LOGGER_CAPTOR);
  }

  @BeforeEach
  void setUp() {
    contractsKafkaChannel = connector.source(CONTRACTS_FROM_GATEWAY);
    LOGGER_CAPTOR.clearRecords();
    when(featureFlags.isPartnerGatewayContractsKafkaConsumerEnabled()).thenReturn(true);
  }

  @Test
  void shouldProcessStringMessage() {
    whenSendMessage(VALID_JSON_MESSAGE);
    assertMessageIsProcessed();
  }

  @Test
  void shouldProcessKafkaMessageWithUnknownLicenseArnField() {
    whenSendMessage(JSON_WITH_EXTRA_LICENSE_ARN_FIELD);
    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              verify(consumer).consumeContract(JSON_WITH_EXTRA_LICENSE_ARN_FIELD);
              thenKafkaContractDeserializedSuccessfully();
            });
  }

  @Test
  void shouldIgnoreMessagesWhenFeatureFlagIsDisabled() {
    when(featureFlags.isPartnerGatewayContractsKafkaConsumerEnabled()).thenReturn(false);
    whenSendMessage(VALID_JSON_MESSAGE);
    assertMessageIsNotProcessed();
  }

  private void whenSendMessage(String message) {
    contractsKafkaChannel.send(message);
  }

  private void assertMessageIsProcessed() {
    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(() -> verify(consumer).consumeContract(anyString()));
  }

  private void assertMessageIsNotProcessed() {
    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(() -> verify(consumer, never()).consumeContract(anyString()));
  }

  private static void thenKafkaContractDeserializedSuccessfully() {
    assertTrue(
        LOGGER_CAPTOR.records.stream()
            .anyMatch(
                r ->
                    Level.INFO.equals(r.getLevel())
                        && r.getMessage().contains("IT Partner message consumed: source=kafka")));
    assertTrue(
        LOGGER_CAPTOR.records.stream()
            .noneMatch(r -> r.getMessage().contains("Unable to read IT Partner Kafka message")));
  }

  static class LoggerCaptor extends Handler {

    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord trace) {
      records.add(trace);
    }

    @Override
    public void flush() {
      // no sink
    }

    @Override
    public void close() throws SecurityException {
      clearRecords();
    }

    void clearRecords() {
      records.clear();
    }
  }
}
