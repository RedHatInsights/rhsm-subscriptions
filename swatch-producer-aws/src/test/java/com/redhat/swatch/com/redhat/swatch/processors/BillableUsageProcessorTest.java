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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.swatch.internal.subscription.api.model.AwsUsageContext;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.InternalSubscriptionsApi;
import com.redhat.swatch.exception.AwsUsageContextLookupException;
import com.redhat.swatch.files.TagProfile;
import com.redhat.swatch.openapi.model.BillableUsage;
import com.redhat.swatch.openapi.model.TallySnapshot;
import com.redhat.swatch.openapi.model.TallySnapshot.BillingProviderEnum;
import com.redhat.swatch.openapi.model.TallySnapshot.GranularityEnum;
import com.redhat.swatch.openapi.model.TallySnapshotTallyMeasurements;
import com.redhat.swatch.openapi.model.TallySnapshotTallyMeasurements.UomEnum;
import com.redhat.swatch.processors.AwsMarketplaceMeteringClientFactory;
import com.redhat.swatch.processors.BillableUsageProcessor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.marketplacemetering.MarketplaceMeteringClient;
import software.amazon.awssdk.services.marketplacemetering.model.BatchMeterUsageRequest;
import software.amazon.awssdk.services.marketplacemetering.model.BatchMeterUsageResponse;
import software.amazon.awssdk.services.marketplacemetering.model.MarketplaceMeteringException;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecord;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecordResult;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecordResultStatus;

@ExtendWith(MockitoExtension.class)
class BillableUsageProcessorTest {

  private static final BillableUsage RHOSAK_INSTANCE_HOURS_RECORD =
      new BillableUsage()
          .billableTallySnapshots(
              List.of(
                  new TallySnapshot()
                      .productId("rhosak")
                      .granularity(GranularityEnum.DAILY)
                      .snapshotDate(OffsetDateTime.MAX)
                      .billingProvider(BillingProviderEnum.AWS)
                      .tallyMeasurements(
                          List.of(
                              new TallySnapshotTallyMeasurements()
                                  .uom(UomEnum.INSTANCE_HOURS)
                                  .value(new BigDecimal("42.0"))))));

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

  @Mock InternalSubscriptionsApi internalSubscriptionsApi;
  @Mock AwsMarketplaceMeteringClientFactory clientFactory;
  @Mock MarketplaceMeteringClient meteringClient;

  MeterRegistry meterRegistry;
  Counter acceptedCounter;
  Counter rejectedCounter;
  BillableUsageProcessor processor;

  @BeforeEach
  void setup() {
    meterRegistry = new SimpleMeterRegistry();
    acceptedCounter = meterRegistry.counter("swatch_aws_marketplace_batch_accepted_total");
    rejectedCounter = meterRegistry.counter("swatch_aws_marketplace_batch_rejected_total");
    processor =
        new BillableUsageProcessor(
            meterRegistry, new TagProfile(), internalSubscriptionsApi, clientFactory);
  }

  @Test
  void shouldSkipNonDailySnapshots() {
    BillableUsage usage =
        new BillableUsage()
            .billableTallySnapshots(
                List.of(new TallySnapshot().granularity(GranularityEnum.YEARLY)));
    processor.process(usage);
    verifyNoInteractions(internalSubscriptionsApi, clientFactory);
  }

  @Test
  void shouldSkipNonAwsSnapshots() {
    BillableUsage usage =
        new BillableUsage()
            .billableTallySnapshots(
                List.of(new TallySnapshot().billingProvider(BillingProviderEnum.RED_HAT)));
    processor.process(usage);
    verifyNoInteractions(internalSubscriptionsApi, clientFactory);
  }

  @Test
  void shouldLookupAwsContextOnApplicableSnapshot() throws ApiException {
    when(internalSubscriptionsApi.getAwsUsageContext(any(), any(), any(), any(), any()))
        .thenReturn(new AwsUsageContext());
    processor.process(RHOSAK_INSTANCE_HOURS_RECORD);
    verify(internalSubscriptionsApi).getAwsUsageContext(any(), any(), any(), any(), any());
  }

  @Test
  void shouldSendUsageForApplicableSnapshot() throws ApiException {
    when(internalSubscriptionsApi.getAwsUsageContext(any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    when(clientFactory.buildMarketplaceMeteringClient(any())).thenReturn(meteringClient);
    processor.process(RHOSAK_INSTANCE_HOURS_RECORD);
    verify(meteringClient).batchMeterUsage(any(BatchMeterUsageRequest.class));
  }

  @Test
  void shouldSkipMessageIfAwsContextCannotBeLookedUp() throws ApiException {
    BillableUsage usage =
        new BillableUsage()
            .billableTallySnapshots(
                List.of(
                    new TallySnapshot()
                        .granularity(GranularityEnum.DAILY)
                        .billingProvider(BillingProviderEnum.AWS)));
    when(internalSubscriptionsApi.getAwsUsageContext(any(), any(), any(), any(), any()))
        .thenThrow(AwsUsageContextLookupException.class);
    processor.process(usage);
    verifyNoInteractions(meteringClient);
  }

  @Test
  void shouldIncrementAcceptedCounterIfSuccessful() throws ApiException {
    when(internalSubscriptionsApi.getAwsUsageContext(any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    when(clientFactory.buildMarketplaceMeteringClient(any())).thenReturn(meteringClient);
    when(meteringClient.batchMeterUsage(any(BatchMeterUsageRequest.class)))
        .thenReturn(BATCH_METER_USAGE_SUCCESS_RESPONSE);
    processor.process(RHOSAK_INSTANCE_HOURS_RECORD);
    assertEquals(1.0, acceptedCounter.count());
  }

  @Test
  void shouldIncrementFailureCounterIfUnprocessed() throws ApiException {
    when(internalSubscriptionsApi.getAwsUsageContext(any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    when(clientFactory.buildMarketplaceMeteringClient(any())).thenReturn(meteringClient);
    when(meteringClient.batchMeterUsage(any(BatchMeterUsageRequest.class)))
        .thenReturn(
            BatchMeterUsageResponse.builder()
                .unprocessedRecords(UsageRecord.builder().build())
                .build());
    processor.process(RHOSAK_INSTANCE_HOURS_RECORD);
    assertEquals(1.0, rejectedCounter.count());
  }

  @Test
  void shouldIncrementFailureCounterOnError() throws ApiException {
    when(internalSubscriptionsApi.getAwsUsageContext(any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AWS_USAGE_CONTEXT);
    when(clientFactory.buildMarketplaceMeteringClient(any())).thenReturn(meteringClient);
    when(meteringClient.batchMeterUsage(any(BatchMeterUsageRequest.class)))
        .thenThrow(MarketplaceMeteringException.class);
    processor.process(RHOSAK_INSTANCE_HOURS_RECORD);
    assertEquals(1.0, rejectedCounter.count());
  }
}
