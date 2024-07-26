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

import static com.redhat.swatch.azure.configuration.Channels.BILLABLE_USAGE_HOURLY_AGGREGATE;
import static com.redhat.swatch.azure.test.resources.InMemoryMessageBrokerKafkaResource.IN_MEMORY_CONNECTOR;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.azure.test.resources.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.azure.test.resources.InjectWireMock;
import com.redhat.swatch.azure.test.resources.WireMockResource;
import com.redhat.swatch.clients.swatch.internal.subscription.api.model.AzureUsageContext;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.apache.http.HttpStatus;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
@QuarkusTestResource(value = WireMockResource.class, restrictToAnnotatedClass = true)
class AzureBillableUsageAggregateConsumerTest {

  private static final String ORG_ID = "org123";
  private static final String PRODUCT_ID = "rosa";
  private static final String BILLING_ACCOUNT_ID = "abc";
  private static final LoggerCaptor LOGGER_CAPTOR = new LoggerCaptor();

  @Inject
  @Connector(IN_MEMORY_CONNECTOR)
  InMemoryConnector connector;

  @InjectWireMock WireMockResource wireMockResource;

  InMemorySource<BillableUsageAggregate> source;
  BillableUsageAggregate usage;
  AzureUsageContext contextForUsage;

  @BeforeAll
  static void configureLogging() {
    LogContext.getLogContext()
        .getLogger(AzureBillableUsageAggregateConsumer.class.getName())
        .addHandler(LOGGER_CAPTOR);
  }

  @BeforeEach
  void setup() {
    LOGGER_CAPTOR.clearRecords();
    source = connector.source(BILLABLE_USAGE_HOURLY_AGGREGATE);
  }

  @Test
  void testUsageIsSentToAzureMarketplace() {
    givenValidUsage();
    givenAzureContextForUsage();
    givenAzureMarketplaceReturnsOk();
    whenSendUsage();
    thenUsageIsSentToAzure();
  }

  @Test
  void testAzureMarketplaceReturnsForbiddenThenExceptionIsThrown() {
    givenValidUsage();
    givenAzureContextForUsage();
    givenAzureMarketplaceReturnsForbidden();
    whenSendUsage();
    thenErrorLogWithMessage("Error sending azure usage for aggregateId");
  }

  @Test
  void testAzureMarketplaceReturnsInvalidRequestThenWarningLogIsPrinted() {
    givenValidUsage();
    givenAzureContextForUsage();
    givenAzureMarketplaceReturnsInvalidRequest();
    whenSendUsage();
    thenWarningLogWithMessage("status: Error");
  }

  private void givenAzureMarketplaceReturnsForbidden() {
    wireMockResource.stubAzureMarketplaceSubmitUsageEventForReturnsStatus(
        contextForUsage, HttpStatus.SC_FORBIDDEN);
  }

  private void givenAzureMarketplaceReturnsInvalidRequest() {
    wireMockResource.stubAzureMarketplaceSubmitUsageEventForReturnsStatus(
        contextForUsage, HttpStatus.SC_BAD_REQUEST);
  }

  private void givenAzureMarketplaceReturnsOk() {
    wireMockResource.stubAzureMarketplaceSubmitUsageEventForReturnsOk(contextForUsage);
  }

  private void givenAzureContextForUsage() {
    contextForUsage =
        wireMockResource.stubInternalSubscriptionAzureMarketPlaceContextForUsage(usage);
  }

  private void givenValidUsage() {
    var key = new BillableUsageAggregateKey();
    key.setUsage(BillableUsage.Usage.DEVELOPMENT_TEST.toString());
    key.setSla(BillableUsage.Sla.STANDARD.toString());
    key.setOrgId(ORG_ID);
    key.setMetricId(MetricIdUtils.getCores().toUpperCaseFormatted());
    key.setProductId(PRODUCT_ID);
    key.setBillingProvider(BillableUsage.BillingProvider.AZURE.value());
    key.setBillingAccountId(BILLING_ACCOUNT_ID);

    usage = new BillableUsageAggregate();
    usage.setAggregateId(UUID.randomUUID());
    usage.setAggregateKey(key);
    usage.setWindowTimestamp(OffsetDateTime.now());
  }

  private void whenSendUsage() {
    source.send(usage);
  }

  private void thenUsageIsSentToAzure() {
    Awaitility.await()
        .untilAsserted(() -> wireMockResource.verifyUsageIsSentToAzureMarketplace(contextForUsage));
  }

  private void thenWarningLogWithMessage(String str) {
    thenLogWithMessage(Level.WARNING, str);
  }

  private void thenErrorLogWithMessage(String str) {
    thenLogWithMessage(Level.SEVERE, str);
  }

  private void thenLogWithMessage(Level level, String str) {
    Awaitility.await()
        .untilAsserted(
            () ->
                assertTrue(
                    LOGGER_CAPTOR.records.stream()
                        .anyMatch(
                            r -> r.getLevel().equals(level) && r.getMessage().contains(str))));
  }

  public static class LoggerCaptor extends Handler {

    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord trace) {
      records.add(trace);
    }

    @Override
    public void flush() {
      // no need to flush any sink
    }

    @Override
    public void close() throws SecurityException {
      clearRecords();
    }

    public void clearRecords() {
      records.clear();
    }
  }
}
