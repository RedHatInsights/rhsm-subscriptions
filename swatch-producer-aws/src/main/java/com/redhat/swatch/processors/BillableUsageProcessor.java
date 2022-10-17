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

import com.redhat.swatch.clients.swatch.internal.subscription.api.model.AwsUsageContext;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.InternalSubscriptionsApi;
import com.redhat.swatch.exception.AwsDimensionNotConfiguredException;
import com.redhat.swatch.exception.AwsUnprocessedRecordsException;
import com.redhat.swatch.exception.AwsUsageContextLookupException;
import com.redhat.swatch.exception.DefaultApiException;
import com.redhat.swatch.exception.SubscriptionRecentlyTerminatedException;
import com.redhat.swatch.files.TagProfile;
import com.redhat.swatch.openapi.model.BillableUsage;
import com.redhat.swatch.openapi.model.BillableUsage.BillingProviderEnum;
import com.redhat.swatch.openapi.model.BillableUsage.SlaEnum;
import com.redhat.swatch.openapi.model.BillableUsage.UsageEnum;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.reactive.messaging.annotations.Blocking;
import java.time.OffsetDateTime;
import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
public class BillableUsageProcessor {
  private final Counter acceptedCounter;
  private final Counter rejectedCounter;
  private final TagProfile tagProfile;
  private final InternalSubscriptionsApi internalSubscriptionsApi;
  private final AwsMarketplaceMeteringClientFactory awsMarketplaceMeteringClientFactory;
  private final Optional<Boolean> isDryRun;

  public BillableUsageProcessor(
      MeterRegistry meterRegistry,
      TagProfile tagProfile,
      @RestClient InternalSubscriptionsApi internalSubscriptionsApi,
      AwsMarketplaceMeteringClientFactory awsMarketplaceMeteringClientFactory,
      @ConfigProperty(name = "ENABLE_AWS_DRY_RUN") Optional<Boolean> isDryRun) {
    acceptedCounter = meterRegistry.counter("swatch_aws_marketplace_batch_accepted_total");
    rejectedCounter = meterRegistry.counter("swatch_aws_marketplace_batch_rejected_total");
    this.tagProfile = tagProfile;
    this.internalSubscriptionsApi = internalSubscriptionsApi;
    this.awsMarketplaceMeteringClientFactory = awsMarketplaceMeteringClientFactory;
    this.isDryRun = isDryRun;
  }

  @Incoming("tally-in")
  @Blocking
  public void process(BillableUsage billableUsage) {
    if (log.isDebugEnabled()) {
      log.debug("Picked up billable usage message {} to process", billableUsage);
    }
    if (billableUsage == null) {
      log.warn("Skipping null billable usage: deserialization failure?");
      return;
    }
    if (!isApplicable(billableUsage)) {
      log.debug("Skipping billable usage because it is not applicable: {}", billableUsage);
      return;
    }

    AwsUsageContext context;
    try {
      context = lookupAwsUsageContext(billableUsage);
    } catch (SubscriptionRecentlyTerminatedException e) {
      log.info(
          "Subscription recently terminated for account={} tallySnapshotId={} orgId={}",
          billableUsage.getAccountNumber(),
          billableUsage.getId(),
          billableUsage.getOrgId());
      return;
    } catch (AwsUsageContextLookupException e) {
      log.error(
          "Error looking up usage context for account={} tallySnapshotId={} orgId={}",
          billableUsage.getAccountNumber(),
          billableUsage.getId(),
          billableUsage.getOrgId(),
          e);
      return;
    }
    try {
      transformAndSend(context, billableUsage);
    } catch (Exception e) {
      log.error(
          "Error sending usage for account={} rhSubscriptionId={} tallySnapshotId={} awsCustomerId={} awsProductCode={} orgId={}",
          billableUsage.getAccountNumber(),
          context.getRhSubscriptionId(),
          billableUsage.getId(),
          context.getCustomerId(),
          context.getProductCode(),
          billableUsage.getOrgId(),
          e);
    }
  }

  private boolean isApplicable(BillableUsage billableUsage) {
    boolean applicable = true;
    if (billableUsage.getBillingProvider() != BillingProviderEnum.AWS) {
      log.debug("Snapshot not applicable because billingProvider is not AWS");
      applicable = false;
    }
    if (!tagProfile.isAwsConfigured(
        billableUsage.getProductId(),
        Optional.ofNullable(billableUsage.getUom()).map(Enum::name).orElse(null))) {
      log.debug("Snapshot not applicable because productId and/or uom is not configured for AWS");
      applicable = false;
    }
    return applicable;
  }

  @Retry(retryOn = AwsUsageContextLookupException.class)
  public AwsUsageContext lookupAwsUsageContext(BillableUsage billableUsage)
      throws AwsUsageContextLookupException {
    try {
      return internalSubscriptionsApi.getAwsUsageContext(
          billableUsage.getAccountNumber(),
          billableUsage.getOrgId(),
          billableUsage.getSnapshotDate(),
          billableUsage.getProductId(),
          Optional.ofNullable(billableUsage.getSla()).map(SlaEnum::value).orElse(null),
          Optional.ofNullable(billableUsage.getUsage()).map(UsageEnum::value).orElse(null),
          Optional.ofNullable(billableUsage.getBillingAccountId()).orElse("_ANY"));
    } catch (DefaultApiException e) {
      var optionalErrors = Optional.ofNullable(e.getErrors());
      if (optionalErrors.isPresent()) {
        var isRecentlyTerminatedError =
            optionalErrors.get().getErrors().stream()
                .anyMatch(error -> ("SUBSCRIPTIONS1005").equals(error.getCode()));
        if (isRecentlyTerminatedError) {
          throw new SubscriptionRecentlyTerminatedException(e);
        }
      }
      throw new AwsUsageContextLookupException(e);
    } catch (ApiException e) {
      throw new AwsUsageContextLookupException(e);
    }
  }

  private void transformAndSend(AwsUsageContext context, BillableUsage billableUsage)
      throws AwsUnprocessedRecordsException, AwsDimensionNotConfiguredException {
    BatchMeterUsageRequest request =
        BatchMeterUsageRequest.builder()
            .productCode(context.getProductCode())
            .usageRecords(transformToAwsUsage(context, billableUsage))
            .build();

    if (isDryRun.isPresent() && Boolean.TRUE.equals(isDryRun.get())) {
      log.info("[DRY RUN] Sending usage request to AWS: {}", request);
      return;
    } else {
      log.info("Sending usage request to AWS: {}", request);
    }

    try {
      MarketplaceMeteringClient marketplaceMeteringClient =
          awsMarketplaceMeteringClientFactory.buildMarketplaceMeteringClient(context);
      BatchMeterUsageResponse response = send(marketplaceMeteringClient, request);
      log.debug("{}", response);
      response
          .results()
          .forEach(
              result -> {
                if (result.status() != UsageRecordResultStatus.SUCCESS) {
                  log.error("{}", result);
                } else {
                  log.info("{}", result);
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

  private UsageRecord transformToAwsUsage(AwsUsageContext context, BillableUsage billableUsage)
      throws AwsDimensionNotConfiguredException {
    OffsetDateTime effectiveTimestamp = billableUsage.getSnapshotDate();
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
            tagProfile.getAwsDimension(billableUsage.getProductId(), billableUsage.getUom().name()))
        .quantity(billableUsage.getValue().intValueExact())
        .timestamp(effectiveTimestamp.toInstant())
        .build();
  }
}
