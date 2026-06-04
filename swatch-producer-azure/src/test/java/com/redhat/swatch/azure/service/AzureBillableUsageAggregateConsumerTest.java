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
import static com.redhat.swatch.azure.configuration.Channels.BILLABLE_USAGE_STATUS;
import static com.redhat.swatch.azure.service.AzureBillableUsageAggregateConsumer.METERED_TOTAL_METRIC;
import static com.redhat.swatch.azure.test.resources.InMemoryMessageBrokerKafkaResource.IN_MEMORY_CONNECTOR;
import static com.redhat.swatch.configuration.registry.SubscriptionDefinition.getBillingFactor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.azure.test.resources.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.azure.test.resources.InjectWireMock;
import com.redhat.swatch.azure.test.resources.WireMockResource;
import com.redhat.swatch.clients.contracts.api.model.AzureUsageContext;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
  private static final BigDecimal EXPECTED_VALUE = BigDecimal.valueOf(500.0);
  private static final LoggerCaptor LOGGER_CAPTOR = new LoggerCaptor();

  @Inject
  @Connector(IN_MEMORY_CONNECTOR)
  InMemoryConnector connector;

  @InjectWireMock WireMockResource wireMockResource;
  @Inject MeterRegistry meterRegistry;

  InMemorySource<BillableUsageAggregate> source;
  InMemorySink<BillableUsageAggregate> status;
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
    status = connector.sink(BILLABLE_USAGE_STATUS);
    status.clear();
    meterRegistry.clear();
  }

  @Test
  void testUsageIsSentToAzureMarketplace() {
    givenValidUsage();
    givenAzureContextForUsage();
    givenAzureMarketplaceReturnsOk();
    whenSendUsage();
    thenInfoLogContainsClientId();
    thenUsageIsSentToAzure();
    thenMeteredTotalMetricIsPopulated();
  }

  @Test
  void testAzureMarketplaceReturnsForbiddenThenExceptionIsThrown() {
    givenValidUsage();
    givenAzureContextForUsage();
    givenAzureMarketplaceReturnsForbidden();
    whenSendUsage();
    thenErrorLogWithMessage("Error sending azure usage for aggregate");
  }

  @Test
  void testAzureMarketplaceReturnsInvalidRequestThenWarningLogIsPrinted() {
    givenValidUsage();
    givenAzureContextForUsage();
    givenAzureMarketplaceReturnsInvalidRequest();
    whenSendUsage();
    thenWarningLogWithMessage("status: Error");
  }

  @Test
  void testShouldSendErrorWhenMetricIsUnsupported() {
    givenUsageWithUnsupportedMetric();
    whenSendUsage();
    Awaitility.await()
        .untilAsserted(
            () -> {
              var received = status.received();
              assertEquals(1, received.size());
              var actual = received.get(0).getPayload();
              assertEquals(BillableUsage.Status.FAILED, actual.getStatus());
              assertEquals(BillableUsage.ErrorCode.UNSUPPORTED_METRIC, actual.getErrorCode());
            });
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
    contextForUsage = wireMockResource.stubContractsAzureMarketPlaceContextForUsage(usage);
  }

  private void givenUsageWithUnsupportedMetric() {
    givenValidUsage();
    // this metric is not configured for rosa
    usage
        .getAggregateKey()
        .setMetricId(MetricIdUtils.getStorageGibibyteMonths().toUpperCaseFormatted());
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
    usage.setTotalValue(EXPECTED_VALUE);
  }

  private void whenSendUsage() {
    source.send(usage);
  }

  private void thenUsageIsSentToAzure() {
    Awaitility.await()
        .untilAsserted(() -> wireMockResource.verifyUsageIsSentToAzureMarketplace(contextForUsage));
  }

  private void thenInfoLogContainsClientId() {
    thenLogWithMessage(Level.INFO, contextForUsage.getClientId());
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

  private void thenMeteredTotalMetricIsPopulated() {
    Awaitility.await()
        .untilAsserted(
            () -> {
              var metric = getMeteredTotalMetric(MetricIdUtils.getCores().toUpperCaseFormatted());
              assertTrue(metric.isPresent());
              assertEquals(
                  metric.get().measure().iterator().next().getValue(),
                  EXPECTED_VALUE.doubleValue()
                      / getBillingFactor(PRODUCT_ID, MetricIdUtils.getCores().getValue()));
            });
  }

  private Optional<Meter> getMeteredTotalMetric(String metricId) {
    return meterRegistry.getMeters().stream()
        .filter(
            m ->
                METERED_TOTAL_METRIC.equals(m.getId().getName())
                    && PRODUCT_ID.equals(m.getId().getTag("product"))
                    && MetricId.fromString(metricId)
                        .getValue()
                        .equals(m.getId().getTag("metric_id"))
                    && BillableUsage.BillingProvider.AZURE
                        .toString()
                        .equals(m.getId().getTag("billing_provider")))
        .findFirst();
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
