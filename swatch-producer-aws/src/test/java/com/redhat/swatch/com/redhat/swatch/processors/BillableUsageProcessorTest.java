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
package com.redhat.swatch.com.redhat.swatch.processors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.swatch.internal.subscription.api.model.AwsUsageContext;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.InternalSubscriptionsApi;
import com.redhat.swatch.exception.AwsUsageContextLookupException;
import com.redhat.swatch.exception.DefaultApiException;
import com.redhat.swatch.exception.SubscriptionRecentlyTerminatedException;
import com.redhat.swatch.openapi.model.BillableUsage;
import com.redhat.swatch.openapi.model.BillableUsage.BillingProviderEnum;
import com.redhat.swatch.openapi.model.Error;
import com.redhat.swatch.openapi.model.Errors;
import com.redhat.swatch.processors.AwsMarketplaceMeteringClientFactory;
import com.redhat.swatch.processors.BillableUsageProcessor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.marketplacemetering.MarketplaceMeteringClient;
import software.amazon.awssdk.services.marketplacemetering.model.BatchMeterUsageRequest;
import software.amazon.awssdk.services.marketplacemetering.model.BatchMeterUsageResponse;
import software.amazon.awssdk.services.marketplacemetering.model.MarketplaceMeteringException;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecord;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecordResult;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecordResultStatus;

@QuarkusTest
class BillableUsageProcessorTest {

  private static final String INSTANCE_HOURS = "Instance-hours";
  private static final String STORAGE_GIBIBYTE_MONTHS = "Storage-gibibyte-months";

  private static final BillableUsage RHOSAK_INSTANCE_HOURS_RECORD =
      new BillableUsage()
          .productId("rhosak")
          .snapshotDate(OffsetDateTime.MAX)
          .billingProvider(BillingProviderEnum.AWS)
          .uom(INSTANCE_HOURS)
          .value(new BigDecimal("42.0"));

  private static final BillableUsage RHOSAK_STORAGE_GIB_MONTHS_RECORD =
      new BillableUsage()
          .productId("rhosak")
          .snapshotDate(OffsetDateTime.MAX)
          .billingProvider(BillingProviderEnum.AWS)
          .uom(STORAGE_GIBIBYTE_MONTHS)
          .value(new BigDecimal("42.0"));

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

  @InjectMock @RestClient InternalSubscriptionsApi internalSubscriptionsApi;
  @InjectMock AwsMarketplaceMeteringClientFactory clientFactory;
  MarketplaceMeteringClient meteringClient;

  @Inject MeterRegistry meterRegistry;
  Counter acceptedCounter;
  Counter rejectedCounter;
  @Inject BillableUsageProcessor processor;

  @BeforeEach
  void setup() {
    acceptedCounter = meterRegistry.counter("swatch_aws_marketplace_batch_accepted_total");
    rejectedCounter = meterRegistry.counter("swatch_aws_marketplace_batch_rejected_total");
    meteringClient = mock(MarketplaceMeteringClient.class);
  }

  @Test
  void shouldSkipNonAwsSnapshots() {
    BillableUsage usage = new BillableUsage().billingProvider(BillingProviderEnum.RED_HAT);
    processor.process(usage);
    verifyNoInteractions(internalSubscriptionsApi, clientFactory);
  }

  @Test
  void shouldLookupAwsContextOnApplicableSnapshot() throws ApiException {
    when(internalSubscriptionsApi.getAwsUsageContext(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new AwsUsageContext());
    processor.process(RHOSAK_INSTANCE_HOURS_RECORD);
    verify(internalSubscriptionsApi)
        .getAwsUsageContext(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void shouldSendUsageForApplicableSnapshot() throws ApiException {
    when(internalSubscriptionsApi.getAwsUsageContext(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    when(clientFactory.buildMarketplaceMeteringClient(any())).thenReturn(meteringClient);
    processor.process(RHOSAK_INSTANCE_HOURS_RECORD);
    verify(meteringClient).batchMeterUsage(any(BatchMeterUsageRequest.class));
  }

  @Test
  void shouldSkipMessageIfAwsContextCannotBeLookedUp() throws ApiException {
    BillableUsage usage =
        new BillableUsage()
            .productId("rhosak")
            .billingProvider(BillingProviderEnum.AWS)
            .uom(INSTANCE_HOURS)
            .value(new BigDecimal("42.0"));
    when(internalSubscriptionsApi.getAwsUsageContext(
            any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(AwsUsageContextLookupException.class);
    processor.process(usage);
    verifyNoInteractions(meteringClient);
  }

  @Test
  void shouldSkipMessageIfUnknownAwsDimensionCannotBeLookedUp() {
    BillableUsage usage =
        new BillableUsage()
            .productId("foobar")
            .billingProvider(BillingProviderEnum.AWS)
            .uom(INSTANCE_HOURS)
            .value(new BigDecimal("42.0"));
    processor.process(usage);
    verifyNoInteractions(internalSubscriptionsApi, meteringClient);
  }

  @Test
  void shouldFindStorageAwsDimension() throws ApiException {
    when(internalSubscriptionsApi.getAwsUsageContext(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new AwsUsageContext());
    processor.process(RHOSAK_STORAGE_GIB_MONTHS_RECORD);
    verify(internalSubscriptionsApi)
        .getAwsUsageContext(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void shouldIncrementAcceptedCounterIfSuccessful() throws ApiException {
    when(internalSubscriptionsApi.getAwsUsageContext(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    when(clientFactory.buildMarketplaceMeteringClient(any())).thenReturn(meteringClient);
    when(meteringClient.batchMeterUsage(any(BatchMeterUsageRequest.class)))
        .thenReturn(BATCH_METER_USAGE_SUCCESS_RESPONSE);
    processor.process(RHOSAK_INSTANCE_HOURS_RECORD);
    assertEquals(1.0, acceptedCounter.count());
  }

  @Test
  void shouldIncrementFailureCounterIfUnprocessed() throws ApiException {
    double current = rejectedCounter.count();
    when(internalSubscriptionsApi.getAwsUsageContext(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    when(clientFactory.buildMarketplaceMeteringClient(any())).thenReturn(meteringClient);
    when(meteringClient.batchMeterUsage(any(BatchMeterUsageRequest.class)))
        .thenReturn(
            BatchMeterUsageResponse.builder()
                .unprocessedRecords(UsageRecord.builder().build())
                .build());
    processor.process(RHOSAK_INSTANCE_HOURS_RECORD);
    assertEquals(current + 1, rejectedCounter.count());
  }

  @Test
  void shouldIncrementFailureCounterOnError() throws ApiException {
    double current = rejectedCounter.count();
    when(internalSubscriptionsApi.getAwsUsageContext(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    when(clientFactory.buildMarketplaceMeteringClient(any())).thenReturn(meteringClient);
    when(meteringClient.batchMeterUsage(any(BatchMeterUsageRequest.class)))
        .thenThrow(MarketplaceMeteringException.class);
    processor.process(RHOSAK_INSTANCE_HOURS_RECORD);
    assertEquals(current + 1, rejectedCounter.count());
  }

  @Test
  void shouldNotMakeAwsUsageRequestWhenDryRunEnabled() throws ApiException {
    BillableUsageProcessor processor =
        new BillableUsageProcessor(
            meterRegistry, internalSubscriptionsApi, clientFactory, Optional.of(true));
    when(internalSubscriptionsApi.getAwsUsageContext(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    processor.process(RHOSAK_INSTANCE_HOURS_RECORD);
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
    when(internalSubscriptionsApi.getAwsUsageContext(
            any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(exception);

    assertThrows(
        SubscriptionRecentlyTerminatedException.class,
        () -> {
          processor.lookupAwsUsageContext(RHOSAK_INSTANCE_HOURS_RECORD);
        });
  }

  @Test
  void shouldSkipMessageIfSubscriptionRecentlyTerminated() throws ApiException {
    Errors errors = new Errors();
    Error error = new Error();
    error.setCode("SUBSCRIPTIONS1005");
    errors.setErrors(Arrays.asList(error));
    var response = Response.serverError().entity(errors).build();
    var exception = new DefaultApiException(response, errors);
    when(internalSubscriptionsApi.getAwsUsageContext(
            any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(exception);

    processor.process(RHOSAK_INSTANCE_HOURS_RECORD);
    verifyNoInteractions(meteringClient);
  }
}
