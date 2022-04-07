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
package com.redhat.swatch.processors;

import com.redhat.swatch.exception.AwsDimensionNotConfiguredException;
import com.redhat.swatch.exception.AwsUnprocessedRecordsException;
import com.redhat.swatch.exception.AwsUsageContextLookupException;
import com.redhat.swatch.files.TagProfile;
import com.redhat.swatch.openapi.model.TallySummary;
import com.redhat.swatch.openapi.model.TallySummaryTallyMeasurements;
import com.redhat.swatch.openapi.model.TallySummaryTallySnapshots;
import com.redhat.swatch.openapi.model.TallySummaryTallySnapshots.BillingProviderEnum;
import com.redhat.swatch.openapi.model.TallySummaryTallySnapshots.GranularityEnum;
import com.redhat.swatch.openapi.model.TallySummaryTallySnapshots.SlaEnum;
import com.redhat.swatch.openapi.model.TallySummaryTallySnapshots.UsageEnum;
import com.swatch.internal.subscription.api.model.AwsUsageContext;
import com.swatch.internal.subscription.api.resources.ApiException;
import com.swatch.internal.subscription.api.resources.InternalSubscriptionsApi;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.reactive.messaging.annotations.Blocking;
import java.time.OffsetDateTime;
import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import software.amazon.awssdk.services.marketplacemetering.MarketplaceMeteringClient;
import software.amazon.awssdk.services.marketplacemetering.model.BatchMeterUsageRequest;
import software.amazon.awssdk.services.marketplacemetering.model.BatchMeterUsageResponse;
import software.amazon.awssdk.services.marketplacemetering.model.MarketplaceMeteringException;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecord;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecordResultStatus;

@Slf4j
@ApplicationScoped
public class TallyTopicProcessor {
  private final Counter acceptedCounter;
  private final Counter rejectedCounter;
  private final TagProfile tagProfile;
  private final InternalSubscriptionsApi internalSubscriptionsApi;
  private final AwsMarketplaceMeteringClientFactory awsMarketplaceMeteringClientFactory;

  public TallyTopicProcessor(
      MeterRegistry meterRegistry,
      TagProfile tagProfile,
      @RestClient InternalSubscriptionsApi internalSubscriptionsApi,
      AwsMarketplaceMeteringClientFactory awsMarketplaceMeteringClientFactory) {
    acceptedCounter = meterRegistry.counter("swatch_aws_marketplace_batch_accepted_total");
    rejectedCounter = meterRegistry.counter("swatch_aws_marketplace_batch_rejected_total");
    this.tagProfile = tagProfile;
    this.internalSubscriptionsApi = internalSubscriptionsApi;
    this.awsMarketplaceMeteringClientFactory = awsMarketplaceMeteringClientFactory;
  }

  @Incoming("tally-in")
  @Blocking
  public void process(TallySummary tallySummary) {
    if (log.isDebugEnabled()) {
      log.debug("Picked up tally message {} to process", tallySummary);
    }
    for (TallySummaryTallySnapshots tallySnapshot : tallySummary.getTallySnapshots()) {
      if (!isSnapshotApplicable(tallySnapshot)) {
        continue;
      }

      AwsUsageContext context;
      try {
        context = lookupAwsUsageContext(tallySummary, tallySnapshot);
      } catch (AwsUsageContextLookupException e) {
        log.error(
            "Error looking up usage context for account={} tallySnapshotId={}",
            tallySummary.getAccountNumber(),
            tallySnapshot.getId(),
            e);
        return;
      }
      try {
        for (var measurement : tallySnapshot.getTallyMeasurements()) {
          transformAndSend(context, tallySnapshot, measurement);
        }
      } catch (Exception e) {
        log.error(
            "Error sending usage for account={} rhSubscriptionId={} tallySnapshotId={} awsCustomerId={} awsProductCode={}",
            tallySummary.getAccountNumber(),
            context.getRhSubscriptionId(),
            tallySnapshot.getId(),
            context.getCustomerId(),
            context.getProductCode(),
            e);
      }
    }
  }

  private boolean isSnapshotApplicable(TallySummaryTallySnapshots tallySnapshot) {
    boolean applicable = true;
    if (tallySnapshot.getGranularity() != GranularityEnum.DAILY) {
      log.debug("Snapshot not applicable because granularity is not Daily");
      applicable = false;
    }
    if (tallySnapshot.getBillingProvider() != BillingProviderEnum.AWS) {
      log.debug("Snapshot not applicable because billingProvider is not AWS");
      applicable = false;
    }
    return applicable;
  }

  @Retry
  public AwsUsageContext lookupAwsUsageContext(
      TallySummary tallySummary, TallySummaryTallySnapshots tallySnapshot)
      throws AwsUsageContextLookupException {
    try {
      return internalSubscriptionsApi.getAwsUsageContext(
          tallySummary.getAccountNumber(),
          tallySnapshot.getSnapshotDate(),
          tallySnapshot.getProductId(),
          Optional.ofNullable(tallySnapshot.getSla()).map(SlaEnum::value).orElse(null),
          Optional.ofNullable(tallySnapshot.getUsage()).map(UsageEnum::value).orElse(null));
    } catch (ApiException e) {
      throw new AwsUsageContextLookupException(e);
    }
  }

  private void transformAndSend(
      AwsUsageContext context,
      TallySummaryTallySnapshots tallySnapshot,
      TallySummaryTallyMeasurements m)
      throws AwsUnprocessedRecordsException, AwsDimensionNotConfiguredException {
    BatchMeterUsageRequest request =
        BatchMeterUsageRequest.builder()
            .productCode(context.getProductCode())
            .usageRecords(transformToAwsUsage(context, tallySnapshot, m))
            .build();
    try {
      MarketplaceMeteringClient marketplaceMeteringClient =
          awsMarketplaceMeteringClientFactory.buildMarketplaceMeteringClient(context);
      BatchMeterUsageResponse response = send(marketplaceMeteringClient, request);
      log.debug("{}", response);
      response
          .results()
          .forEach(
              result -> {
                log.info(
                    "awsMeteringRecordId={} for dimension={} and customerId={}",
                    result.meteringRecordId(),
                    result.usageRecord().dimension(),
                    result.usageRecord().customerIdentifier());
                if (result.status() != UsageRecordResultStatus.SUCCESS) {
                  log.error("{}", result);
                } else {
                  acceptedCounter.increment(response.results().size());
                }
              });
      if (!response.unprocessedRecords().isEmpty()) {
        rejectedCounter.increment(response.unprocessedRecords().size());
        throw new AwsUnprocessedRecordsException(response.unprocessedRecords().size());
      }
    } catch (MarketplaceMeteringException e) {
      rejectedCounter.increment(request.usageRecords().size());
      throw new AwsUnprocessedRecordsException(request.usageRecords().size(), e);
    }
  }

  @Retry
  public BatchMeterUsageResponse send(
      MarketplaceMeteringClient client, BatchMeterUsageRequest request) {
    return client.batchMeterUsage(request);
  }

  private UsageRecord transformToAwsUsage(
      AwsUsageContext context,
      TallySummaryTallySnapshots tallySnapshot,
      TallySummaryTallyMeasurements measurement)
      throws AwsDimensionNotConfiguredException {
    OffsetDateTime effectiveTimestamp = tallySnapshot.getSnapshotDate();
    if (effectiveTimestamp.isBefore(context.getSubscriptionStartDate())) {
      // NOTE: AWS requires that the timestamp "is not before the start of the software usage."
      // https://docs.aws.amazon.com/marketplacemetering/latest/APIReference/API_UsageRecord.html
      // Because swatch doesn't store a precise timestamp for beginning of usage, we'll fall back to
      // the subscription start timestamp.
      effectiveTimestamp = context.getSubscriptionStartDate();
    }
    return UsageRecord.builder()
        .customerIdentifier(context.getCustomerId())
        .dimension(
            tagProfile.getAwsDimension(tallySnapshot.getProductId(), measurement.getUom().name()))
        .quantity(measurement.getValue().intValue())
        .timestamp(effectiveTimestamp.toInstant())
        .build();
  }
}
