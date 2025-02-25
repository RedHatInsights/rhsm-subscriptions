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
package com.redhat.swatch.aws.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.aws.exception.AwsUsageContextLookupException;
import com.redhat.swatch.aws.exception.DefaultApiException;
import com.redhat.swatch.aws.exception.SubscriptionCanNotBeDeterminedException;
import com.redhat.swatch.aws.exception.SubscriptionRecentlyTerminatedException;
import com.redhat.swatch.aws.openapi.model.Error;
import com.redhat.swatch.aws.openapi.model.Errors;
import com.redhat.swatch.clients.contracts.api.model.AwsUsageContext;
import com.redhat.swatch.clients.contracts.api.resources.ApiException;
import com.redhat.swatch.clients.contracts.api.resources.DefaultApi;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.Usage;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.services.marketplacemetering.MarketplaceMeteringClient;
import software.amazon.awssdk.services.marketplacemetering.model.BatchMeterUsageRequest;
import software.amazon.awssdk.services.marketplacemetering.model.BatchMeterUsageResponse;
import software.amazon.awssdk.services.marketplacemetering.model.MarketplaceMeteringException;
import software.amazon.awssdk.services.marketplacemetering.model.ThrottlingException;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecord;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecordResult;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecordResultStatus;

@QuarkusTest
class AwsBillableUsageAggregateConsumerTest {
  private static final String PRODUCT_ID = "rosa";

  private static final Clock clock =
      Clock.fixed(Instant.parse("2023-10-02T12:30:00Z"), ZoneId.of("UTC"));

  private static final BillableUsageAggregate ROSA_INSTANCE_HOURS_RECORD =
      createAggregate(
          "rosa",
          MetricIdUtils.getInstanceHours().toUpperCaseFormatted(),
          OffsetDateTime.now(Clock.systemUTC()),
          42);

  private static final BillableUsageAggregate ROSA_STORAGE_CORES_RECORD =
      createAggregate(
          "rosa",
          MetricIdUtils.getCores().toUpperCaseFormatted(),
          OffsetDateTime.now(Clock.systemUTC()),
          42);

  public static final AwsUsageContext MOCK_AWS_USAGE_CONTEXT =
      new AwsUsageContext()
          .rhSubscriptionId("id")
          .customerId("customer")
          .productCode("product")
          .subscriptionStartDate(OffsetDateTime.MIN);
  public static final BatchMeterUsageResponse BATCH_METER_USAGE_SUCCESS_RESPONSE =
      BatchMeterUsageResponse.builder()
          .results(
              UsageRecordResult.builder()
                  .usageRecord(
                      UsageRecord.builder()
                          .customerIdentifier("customer")
                          .dimension("dimension")
                          .build())
                  .status(UsageRecordResultStatus.SUCCESS)
                  .build())
          .build();

  @InjectMock @RestClient DefaultApi contractsApi;
  @InjectMock AwsMarketplaceMeteringClientFactory clientFactory;
  MarketplaceMeteringClient meteringClient;
  @InjectMock BillableUsageStatusProducer billableUsageStatusProducer;

  @Inject MeterRegistry meterRegistry;
  Counter acceptedCounter;
  Counter rejectedCounter;
  Counter ignoredCounter;
  Counter redundantCounter;
  Counter successCounter;
  Counter failedCounter;

  @Inject AwsBillableUsageAggregateConsumer consumer;

  @ConfigProperty(name = "AWS_MARKETPLACE_USAGE_WINDOW")
  Duration maxAgeDuration;

  @BeforeEach
  void setup() {
    acceptedCounter = meterRegistry.counter("swatch_aws_marketplace_batch_accepted_total");
    rejectedCounter = meterRegistry.counter("swatch_aws_marketplace_batch_rejected_total");
    ignoredCounter = meterRegistry.counter("swatch_aws_marketplace_batch_ignored_total");

    var meteredTotalBuilder =
        Counter.builder(AwsBillableUsageAggregateConsumer.METERED_TOTAL_METRIC)
            .tags(
                "product",
                "rosa",
                "metric_id",
                MetricIdUtils.getInstanceHours().getValue(),
                "billing_provider",
                "aws")
            .withRegistry(meterRegistry);

    failedCounter =
        meteredTotalBuilder.withTags(
            "status",
            BillableUsage.Status.FAILED.toString(),
            "error_code",
            BillableUsage.ErrorCode.UNKNOWN.toString());

    redundantCounter =
        meteredTotalBuilder.withTags(
            "status",
            BillableUsage.Status.FAILED.toString(),
            "error_code",
            BillableUsage.ErrorCode.REDUNDANT.toString());

    successCounter =
        meteredTotalBuilder.withTag("status", BillableUsage.Status.SUCCEEDED.toString());

    meteringClient = mock(MarketplaceMeteringClient.class);
  }

  @Test
  void shouldSkipNonAwsSnapshots() {
    var aggregate =
        createAggregate(
            "BASILISK",
            MetricIdUtils.getInstanceHours().toUpperCaseFormatted(),
            OffsetDateTime.now(),
            10);
    var key =
        new BillableUsageAggregateKey(
            "testOrg",
            "BASILSK",
            MetricIdUtils.getInstanceHours().toUpperCaseFormatted(),
            BillableUsage.Sla.PREMIUM.value(),
            Usage.PRODUCTION.getValue(),
            BillableUsage.BillingProvider.RED_HAT.value(),
            "testBillingAccountId");
    aggregate.setAggregateKey(key);
    consumer.process(aggregate);
    verifyNoInteractions(contractsApi, clientFactory);
  }

  @Test
  void shouldLookupAwsContextOnApplicableSnapshot() throws ApiException {
    when(contractsApi.getAwsUsageContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(new AwsUsageContext());
    consumer.process(ROSA_INSTANCE_HOURS_RECORD);
    verify(contractsApi).getAwsUsageContext(any(), any(), any(), any(), any(), any());
  }

  @Test
  void shouldSendUsageForApplicableSnapshot() throws ApiException {
    when(contractsApi.getAwsUsageContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    when(clientFactory.buildMarketplaceMeteringClient(any())).thenReturn(meteringClient);
    consumer.process(ROSA_INSTANCE_HOURS_RECORD);
    verify(meteringClient).batchMeterUsage(any(BatchMeterUsageRequest.class));
  }

  @Test
  void whenThrottlingExceptionThenSetRateLimitErrorCode() throws ApiException {
    when(contractsApi.getAwsUsageContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    when(clientFactory.buildMarketplaceMeteringClient(any())).thenReturn(meteringClient);
    ThrottlingException throttlingException =
        ThrottlingException.builder().message("Rate limit exceeded").build();
    when(meteringClient.batchMeterUsage(any(BatchMeterUsageRequest.class)))
        .thenThrow(throttlingException);
    consumer.process(ROSA_INSTANCE_HOURS_RECORD);
    verify(billableUsageStatusProducer)
        .emitStatus(
            argThat(
                usage ->
                    BillableUsage.Status.FAILED.equals(usage.getStatus())
                        && BillableUsage.ErrorCode.MARKETPLACE_RATE_LIMIT.equals(
                            usage.getErrorCode())));
  }

  @Test
  void shouldSkipMessageIfAwsContextCannotBeLookedUp() throws ApiException {
    BillableUsageAggregate aggregate =
        createAggregate(
            "rosa",
            MetricIdUtils.getInstanceHours().toUpperCaseFormatted(),
            OffsetDateTime.now(),
            42.0);
    when(contractsApi.getAwsUsageContext(any(), any(), any(), any(), any(), any()))
        .thenThrow(AwsUsageContextLookupException.class);
    consumer.process(aggregate);
    verifyNoInteractions(meteringClient);
  }

  @Test
  void shouldSkipMessageIfUnknownAwsDimensionCannotBeLookedUp() {
    BillableUsageAggregate aggregate =
        createAggregate(
            "foobar",
            MetricIdUtils.getInstanceHours().toUpperCaseFormatted(),
            OffsetDateTime.now(),
            42.0);
    consumer.process(aggregate);
    verifyNoInteractions(contractsApi, meteringClient);
  }

  @Test
  void shouldFindStorageAwsDimension() throws ApiException {
    when(contractsApi.getAwsUsageContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(new AwsUsageContext());
    consumer.process(ROSA_STORAGE_CORES_RECORD);
    verify(contractsApi).getAwsUsageContext(any(), any(), any(), any(), any(), any());
  }

  @Test
  void shouldIncrementAcceptedAndSuccessCounters() throws ApiException {
    var priorSuccess = successCounter.count();
    when(contractsApi.getAwsUsageContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    when(clientFactory.buildMarketplaceMeteringClient(any())).thenReturn(meteringClient);
    when(meteringClient.batchMeterUsage(any(BatchMeterUsageRequest.class)))
        .thenReturn(BATCH_METER_USAGE_SUCCESS_RESPONSE);
    consumer.process(ROSA_INSTANCE_HOURS_RECORD);
    assertEquals(1.0, acceptedCounter.count());

    var metric = getMeteredTotalMetricForSucceeded(MetricIdUtils.getInstanceHours().getValue());
    assertTrue(metric.isPresent());
    assertEquals(priorSuccess + 42.0, metric.get().measure().iterator().next().getValue());
  }

  @Test
  void shouldIncrementFailureCounterIfUnprocessed() throws ApiException {
    double priorFailed = failedCounter.count();
    double current = rejectedCounter.count();
    when(contractsApi.getAwsUsageContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    when(clientFactory.buildMarketplaceMeteringClient(any())).thenReturn(meteringClient);
    when(meteringClient.batchMeterUsage(any(BatchMeterUsageRequest.class)))
        .thenReturn(
            BatchMeterUsageResponse.builder()
                .unprocessedRecords(UsageRecord.builder().build())
                .build());
    consumer.process(ROSA_INSTANCE_HOURS_RECORD);
    assertEquals(current + 1, rejectedCounter.count());
    var metric =
        getMeteredTotalMetricForFailed(
            MetricIdUtils.getInstanceHours().getValue(),
            BillableUsage.ErrorCode.UNKNOWN.toString());
    assertTrue(metric.isPresent());
    assertEquals(priorFailed + 42.0, metric.get().measure().iterator().next().getValue());
  }

  @Test
  void shouldIncrementFailureCounterOnError() throws ApiException {
    double current = rejectedCounter.count();
    when(contractsApi.getAwsUsageContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    when(clientFactory.buildMarketplaceMeteringClient(any())).thenReturn(meteringClient);
    when(meteringClient.batchMeterUsage(any(BatchMeterUsageRequest.class)))
        .thenThrow(MarketplaceMeteringException.class);
    consumer.process(ROSA_INSTANCE_HOURS_RECORD);
    assertEquals(current + 1, rejectedCounter.count());
  }

  @Test
  void shouldNotMakeAwsUsageRequestWhenDryRunEnabled() throws ApiException {
    AwsBillableUsageAggregateConsumer processor =
        new AwsBillableUsageAggregateConsumer(
            meterRegistry,
            contractsApi,
            clientFactory,
            Optional.of(true),
            Duration.of(1, ChronoUnit.HOURS),
            billableUsageStatusProducer);
    when(contractsApi.getAwsUsageContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    processor.process(ROSA_INSTANCE_HOURS_RECORD);
    verifyNoInteractions(clientFactory, meteringClient);
  }

  @Test
  void shouldThrowSubscriptionTerminatedException() throws ApiException {
    Errors errors = new Errors();
    Error error = new Error();
    error.setCode("SUBSCRIPTIONS1005");
    errors.setErrors(Arrays.asList(error));
    var response = Response.serverError().entity(errors).build();
    var exception = new DefaultApiException(response, errors);
    when(contractsApi.getAwsUsageContext(any(), any(), any(), any(), any(), any()))
        .thenThrow(exception);

    assertThrows(
        SubscriptionRecentlyTerminatedException.class,
        () -> {
          consumer.lookupAwsUsageContext(ROSA_INSTANCE_HOURS_RECORD);
        });
  }

  @Test
  void shouldSkipMessageIfSubscriptionRecentlyTerminated() throws ApiException {
    Errors errors = new Errors();
    Error error = new Error();
    error.setCode("SUBSCRIPTIONS1005");
    errors.setErrors(List.of(error));
    var response = Response.serverError().entity(errors).build();
    var exception = new DefaultApiException(response, errors);
    when(contractsApi.getAwsUsageContext(any(), any(), any(), any(), any(), any()))
        .thenThrow(exception);

    consumer.process(ROSA_INSTANCE_HOURS_RECORD);
    verifyNoInteractions(meteringClient);
  }

  @Test
  void shouldSkipMessageProcessingIfUsageIsOutsideTheUsageWindow() throws Exception {
    double currentIgnored = ignoredCounter.count();
    when(contractsApi.getAwsUsageContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    BillableUsageAggregate aggregate =
        createAggregate(
            "rosa",
            MetricIdUtils.getInstanceHours().toUpperCaseFormatted(),
            OffsetDateTime.now(Clock.systemUTC()).minusHours(8),
            42.0);
    consumer.process(aggregate);
    verifyNoInteractions(meteringClient);
    assertEquals(currentIgnored + 1, ignoredCounter.count());
    var metric =
        getMeteredTotalMetricForFailed(
            MetricIdUtils.getInstanceHours().getValue(),
            BillableUsage.ErrorCode.REDUNDANT.toString());
    assertTrue(metric.isPresent());
    assertEquals(currentIgnored + 42.0, metric.get().measure().iterator().next().getValue());
  }

  @Test
  void testEmitStatus() throws ApiException {
    BillableUsageAggregate aggregate =
        createAggregate(
            "rosa",
            MetricIdUtils.getInstanceHours().toUpperCaseFormatted(),
            OffsetDateTime.now(Clock.systemUTC()).minusHours(4),
            42.0);

    // verify failure
    when(contractsApi.getAwsUsageContext(any(), any(), any(), any(), any(), any()))
        .thenThrow(new SubscriptionCanNotBeDeterminedException(null));
    consumer.process(aggregate);
    verify(billableUsageStatusProducer)
        .emitStatus(
            argThat(
                usage ->
                    BillableUsage.Status.FAILED.equals(usage.getStatus())
                        && BillableUsage.ErrorCode.SUBSCRIPTION_NOT_FOUND.equals(
                            usage.getErrorCode())));

    // verify success
    reset(contractsApi, billableUsageStatusProducer);
    when(contractsApi.getAwsUsageContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    when(clientFactory.buildMarketplaceMeteringClient(any())).thenReturn(meteringClient);
    when(meteringClient.batchMeterUsage(any(BatchMeterUsageRequest.class)))
        .thenReturn(BatchMeterUsageResponse.builder().build());
    consumer.process(ROSA_INSTANCE_HOURS_RECORD);
    verify(billableUsageStatusProducer)
        .emitStatus(argThat(usage -> BillableUsage.Status.SUCCEEDED.equals(usage.getStatus())));
  }

  @Test
  void testShouldSendErrorWhenMetricIsUnsupported() {
    var aggregate = createAggregate("BASILISK", "Cores", OffsetDateTime.now(), 10);
    consumer.process(aggregate);
    verify(billableUsageStatusProducer)
        .emitStatus(
            argThat(
                usage ->
                    BillableUsage.Status.FAILED.equals(usage.getStatus())
                        && BillableUsage.ErrorCode.UNSUPPORTED_METRIC.equals(
                            usage.getErrorCode())));
  }

  static Stream<Arguments> usageWindowTestArgs() {
    OffsetDateTime now = OffsetDateTime.now(clock);
    OffsetDateTime startOfCurrentHour = now.minusMinutes(30);
    OffsetDateTime cutoff = startOfCurrentHour.minusHours(6);
    return Stream.of(
        Arguments.of(now, true),
        Arguments.of(startOfCurrentHour, true),
        Arguments.of(cutoff, true),
        Arguments.of(cutoff.minusNanos(1), false),
        Arguments.of(now.minusHours(7), false));
  }

  @ParameterizedTest
  @MethodSource("usageWindowTestArgs")
  void testUsageWindow(OffsetDateTime date, boolean isValid) {
    // 6h
    BillableUsageAggregate aggregate =
        createAggregate(
            "rosa", MetricIdUtils.getInstanceHours().toUpperCaseFormatted(), date, 42.0);
    assertEquals(21600, maxAgeDuration.getSeconds());
    assertEquals(isValid, consumer.isUsageDateValid(clock, aggregate));
  }

  private static BillableUsageAggregate createAggregate(
      String productId, String metricId, OffsetDateTime timestamp, double totalValue) {
    var aggregate = new BillableUsageAggregate();
    aggregate.setAggregateId(UUID.randomUUID());
    aggregate.setWindowTimestamp(timestamp);
    aggregate.setTotalValue(new BigDecimal(totalValue));
    var key =
        new BillableUsageAggregateKey(
            "testOrg",
            productId,
            metricId,
            BillableUsage.Sla.PREMIUM.value(),
            Usage.PRODUCTION.getValue(),
            BillableUsage.BillingProvider.AWS.value(),
            "testBillingAccountId");
    aggregate.setAggregateKey(key);
    return aggregate;
  }

  private Optional<Meter> getMeteredTotalMetricForSucceeded(String metricId) {
    return meterRegistry.getMeters().stream()
        .filter(
            m ->
                AwsBillableUsageAggregateConsumer.METERED_TOTAL_METRIC.equals(m.getId().getName())
                    && PRODUCT_ID.equals(m.getId().getTag("product"))
                    && MetricId.fromString(metricId)
                        .getValue()
                        .equals(m.getId().getTag("metric_id"))
                    && BillableUsage.BillingProvider.AWS
                        .toString()
                        .equals(m.getId().getTag("billing_provider"))
                    && "succeeded".equals(m.getId().getTag("status")))
        .findFirst();
  }

  private Optional<Meter> getMeteredTotalMetricForFailed(String metricId, String errorCode) {
    return meterRegistry.getMeters().stream()
        .filter(
            m ->
                AwsBillableUsageAggregateConsumer.METERED_TOTAL_METRIC.equals(m.getId().getName())
                    && PRODUCT_ID.equals(m.getId().getTag("product"))
                    && MetricId.fromString(metricId)
                        .getValue()
                        .equals(m.getId().getTag("metric_id"))
                    && BillableUsage.BillingProvider.AWS
                        .toString()
                        .equals(m.getId().getTag("billing_provider"))
                    && "failed".equals(m.getId().getTag("status"))
                    && errorCode.equals(m.getId().getTag("error_code")))
        .findFirst();
  }
}
