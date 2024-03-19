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
package com.redhat.swatch.aws.processors;

import com.redhat.swatch.aws.exception.AwsDimensionNotConfiguredException;
import com.redhat.swatch.aws.exception.AwsMissingCredentialsException;
import com.redhat.swatch.aws.exception.AwsUnprocessedRecordsException;
import com.redhat.swatch.aws.exception.AwsUsageContextLookupException;
import com.redhat.swatch.aws.exception.DefaultApiException;
import com.redhat.swatch.aws.exception.SubscriptionRecentlyTerminatedException;
import com.redhat.swatch.aws.exception.UsageTimestampOutOfBoundsException;
import com.redhat.swatch.aws.kafka.BillableUsageAggregate;
import com.redhat.swatch.aws.kafka.BillableUsageAggregateKey;
import com.redhat.swatch.aws.openapi.model.BillableUsage.BillingProviderEnum;
import com.redhat.swatch.clients.swatch.internal.subscription.api.model.AwsUsageContext;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.InternalSubscriptionsApi;
import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.Variant;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.MDC;
import software.amazon.awssdk.services.marketplacemetering.MarketplaceMeteringClient;
import software.amazon.awssdk.services.marketplacemetering.model.BatchMeterUsageRequest;
import software.amazon.awssdk.services.marketplacemetering.model.BatchMeterUsageResponse;
import software.amazon.awssdk.services.marketplacemetering.model.MarketplaceMeteringException;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecord;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecordResultStatus;

@Slf4j
@ApplicationScoped
public class BillableUsageAggregateProcessor {
  private final Counter acceptedCounter;
  private final Counter rejectedCounter;
  private final Counter ignoreCounter;
  private final InternalSubscriptionsApi internalSubscriptionsApi;
  private final AwsMarketplaceMeteringClientFactory awsMarketplaceMeteringClientFactory;
  private final Optional<Boolean> isDryRun;
  private final Duration awsUsageWindow;

  public BillableUsageAggregateProcessor(
      MeterRegistry meterRegistry,
      @RestClient InternalSubscriptionsApi internalSubscriptionsApi,
      AwsMarketplaceMeteringClientFactory awsMarketplaceMeteringClientFactory,
      @ConfigProperty(name = "ENABLE_AWS_DRY_RUN") Optional<Boolean> isDryRun,
      @ConfigProperty(name = "AWS_MARKETPLACE_USAGE_WINDOW") Duration awsUsageWindow) {
    acceptedCounter = meterRegistry.counter("swatch_aws_marketplace_batch_accepted_total");
    rejectedCounter = meterRegistry.counter("swatch_aws_marketplace_batch_rejected_total");
    ignoreCounter = meterRegistry.counter("swatch_aws_marketplace_batch_ignored_total");
    this.internalSubscriptionsApi = internalSubscriptionsApi;
    this.awsMarketplaceMeteringClientFactory = awsMarketplaceMeteringClientFactory;
    this.isDryRun = isDryRun;
    this.awsUsageWindow = awsUsageWindow;
  }

  @Incoming("billable-usage-hourly-aggregate-in")
  @Blocking
  public void process(BillableUsageAggregate billableUsageAggregate) {
    log.debug("Picked up billable usage message {} to process", billableUsageAggregate);
    if (billableUsageAggregate == null || billableUsageAggregate.getAggregateKey() == null) {
      log.warn("Skipping null billable usage: deserialization failure?");
      return;
    }
    if (billableUsageAggregate.getAggregateKey().getOrgId() != null) {
      MDC.put("org_id", billableUsageAggregate.getAggregateKey().getOrgId());
    }

    Optional<Metric> metric =
        validateUsageAndLookupMetric(billableUsageAggregate.getAggregateKey());
    if (metric.isEmpty()) {
      log.debug("Skipping billable usage because it is not applicable: {}", billableUsageAggregate);
      return;
    }

    AwsUsageContext context;
    try {
      context = lookupAwsUsageContext(billableUsageAggregate);
    } catch (SubscriptionRecentlyTerminatedException e) {
      log.info(
          "Subscription recently terminated for billableUsageAggregateId={} orgId={}",
          billableUsageAggregate.getAggregateId(),
          billableUsageAggregate.getAggregateKey().getOrgId());
      return;
    } catch (AwsUsageContextLookupException e) {
      log.error(
          "Error looking up usage context for tallySnapshotId={} orgId={}",
          billableUsageAggregate.getAggregateId(),
          billableUsageAggregate.getAggregateKey().getOrgId(),
          e);
      return;
    }
    try {
      transformAndSend(context, billableUsageAggregate, metric.get());
    } catch (UsageTimestampOutOfBoundsException e) {
      log.warn(
          "{} orgId={} aggregateId={} productId={} windowTimestamp={} rhSubscriptionId={} awsCustomerId={} awsProductCode={} subscriptionStartDate={} value={}",
          e.getMessage(),
          billableUsageAggregate.getAggregateKey().getOrgId(),
          billableUsageAggregate.getAggregateId(),
          billableUsageAggregate.getAggregateKey().getProductId(),
          billableUsageAggregate.getWindowTimestamp(),
          context.getRhSubscriptionId(),
          context.getCustomerId(),
          context.getProductCode(),
          context.getSubscriptionStartDate(),
          billableUsageAggregate.getTotalValue());
      ignoreCounter.increment();
    } catch (Exception e) {
      log.error(
          "Error sending usage for rhSubscriptionId={} aggregateId={} awsCustomerId={} awsProductCode={} orgId={}",
          context.getRhSubscriptionId(),
          billableUsageAggregate.getAggregateId(),
          context.getCustomerId(),
          context.getProductCode(),
          billableUsageAggregate.getAggregateKey().getOrgId(),
          e);
    }
  }

  @Retry(retryOn = AwsUsageContextLookupException.class)
  public AwsUsageContext lookupAwsUsageContext(BillableUsageAggregate billableUsageAggregate)
      throws AwsUsageContextLookupException {
    try {
      return internalSubscriptionsApi.getAwsUsageContext(
          billableUsageAggregate.getWindowTimestamp(),
          billableUsageAggregate.getAggregateKey().getProductId(),
          billableUsageAggregate.getAggregateKey().getOrgId(),
          billableUsageAggregate.getAggregateKey().getSla(),
          billableUsageAggregate.getAggregateKey().getUsage(),
          billableUsageAggregate.getAggregateKey().getBillingAccountId());
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
    } catch (ProcessingException | ApiException e) {
      throw new AwsUsageContextLookupException(e);
    }
  }

  private void transformAndSend(
      AwsUsageContext context, BillableUsageAggregate billableUsageAggregate, Metric metric)
      throws AwsUnprocessedRecordsException,
          AwsDimensionNotConfiguredException,
          UsageTimestampOutOfBoundsException {
    BatchMeterUsageRequest request =
        BatchMeterUsageRequest.builder()
            .productCode(context.getProductCode())
            .usageRecords(transformToAwsUsage(context, billableUsageAggregate, metric))
            .build();

    if (isDryRun.isPresent() && Boolean.TRUE.equals(isDryRun.get())) {
      log.info(
          "[DRY RUN] Sending usage request to AWS: {}, organization={}, product_id={}",
          request,
          billableUsageAggregate.getAggregateKey().getOrgId(),
          billableUsageAggregate.getAggregateKey().getProductId());
      return;
    } else {
      log.info(
          "Sending usage request to AWS: {}, organization={}, product_id={}",
          request,
          billableUsageAggregate.getAggregateKey().getOrgId(),
          billableUsageAggregate.getAggregateKey().getProductId());
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
                if (result.status() == UsageRecordResultStatus.CUSTOMER_NOT_SUBSCRIBED) {
                  log.warn(
                      "No subscription found for organization={}, product_id={}, result={}",
                      billableUsageAggregate.getAggregateKey().getOrgId(),
                      billableUsageAggregate.getAggregateKey().getProductId(),
                      result);
                } else if (result.status() != UsageRecordResultStatus.SUCCESS) {
                  log.warn(
                      "{}, organization={}",
                      result,
                      billableUsageAggregate.getAggregateKey().getOrgId());
                } else {
                  log.info(
                      "{}, organization={},",
                      result,
                      billableUsageAggregate.getAggregateKey().getOrgId());
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
    } catch (AwsMissingCredentialsException e) {
      log.warn(
          "{} for organization={}, awsCustomerId={}",
          e.getMessage(),
          billableUsageAggregate.getAggregateKey().getOrgId(),
          context.getCustomerId());
    }
  }

  @Retry
  public BatchMeterUsageResponse send(
      MarketplaceMeteringClient client, BatchMeterUsageRequest request) {
    return client.batchMeterUsage(request);
  }

  private UsageRecord transformToAwsUsage(
      AwsUsageContext context, BillableUsageAggregate billableUsageAggregate, Metric metric)
      throws AwsDimensionNotConfiguredException, UsageTimestampOutOfBoundsException {
    OffsetDateTime effectiveTimestamp = billableUsageAggregate.getWindowTimestamp();
    if (effectiveTimestamp.isBefore(context.getSubscriptionStartDate())) {
      // Because swatch doesn't store a precise timestamp for beginning of usage, we'll fall back to
      // the subscription start timestamp.
      effectiveTimestamp = context.getSubscriptionStartDate();
    }

    // NOTE: AWS requires that the timestamp "is not before the start of the software usage."
    // https://docs.aws.amazon.com/marketplacemetering/latest/APIReference/API_UsageRecord.html
    if (!isUsageDateValid(Clock.systemUTC(), billableUsageAggregate)) {
      throw new UsageTimestampOutOfBoundsException(
          "Unable to send usage since it is outside of the AWS processing window");
    }

    return UsageRecord.builder()
        .customerIdentifier(context.getCustomerId())
        .dimension(metric.getAwsDimension())
        .quantity(billableUsageAggregate.getTotalValue().intValueExact())
        .timestamp(effectiveTimestamp.toInstant())
        .build();
  }

  private Optional<Metric> validateUsageAndLookupMetric(BillableUsageAggregateKey aggregationKey) {
    if (!Objects.equals(aggregationKey.getBillingProvider(), BillingProviderEnum.AWS.value())) {
      log.debug("Snapshot not applicable because billingProvider is not AWS");
      return Optional.empty();
    }

    if (aggregationKey.getMetricId() == null) {
      log.debug("Snapshot not applicable because billable uom is empty");
      return Optional.empty();
    }

    Optional<Metric> metric =
        Variant.findByTag(aggregationKey.getProductId()).stream()
            .map(
                v ->
                    v.getSubscription()
                        .getMetric(MetricId.fromString(aggregationKey.getMetricId()).getValue())
                        .orElse(null))
            .filter(Objects::nonNull)
            .findFirst();

    if (metric.isEmpty()) {
      log.debug("Snapshot not applicable because productId and/or uom is not configured for AWS");
    }

    return metric;
  }

  /**
   * Determines if the usage timestamp is valid according to the AWS usage age policy. In order for
   * usage to be accepted by AWS, the timestamp must be on or after the START_OF_CURRENT_HOUR - 6h.
   *
   * <p>The implementation of this validation method follows the AWS policy closely, however, the
   * AWS_MARKETPLACE_USAGE_WINDOW duration env var is available to allow fine-tuning of our own
   * window.
   *
   * <p>START_OF_CURRENT_HOUR - AWS_MARKETPLACE_USAGE_WINDOW
   *
   * @param clock the clock used to determine the time NOW.
   * @param billableUsageAggregate the usage aggregate to check.
   * @return true if the usage timestamp is valid, false otherwise.
   */
  // NOTE: Pass the clock as a parameter to make things a little easier to test.
  protected boolean isUsageDateValid(Clock clock, BillableUsageAggregate billableUsageAggregate) {
    OffsetDateTime startOfCurrentHour = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.HOURS);
    OffsetDateTime cutoff = startOfCurrentHour.minus(awsUsageWindow);
    return !billableUsageAggregate.getWindowTimestamp().isBefore(cutoff);
  }
}
