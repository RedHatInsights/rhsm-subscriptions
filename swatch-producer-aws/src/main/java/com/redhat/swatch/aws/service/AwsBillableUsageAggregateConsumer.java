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

import com.redhat.swatch.aws.configuration.UsageInfoPrefixedLogger;
import com.redhat.swatch.aws.exception.AwsMissingCredentialsException;
import com.redhat.swatch.aws.exception.AwsUnprocessedRecordsException;
import com.redhat.swatch.aws.exception.AwsUsageContextLookupException;
import com.redhat.swatch.aws.exception.DefaultApiException;
import com.redhat.swatch.aws.exception.SubscriptionRecentlyTerminatedException;
import com.redhat.swatch.aws.exception.UsageTimestampOutOfBoundsException;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;
import org.candlepin.subscriptions.billable.usage.UsageInfo;
import org.candlepin.subscriptions.billable.usage.UsageInfoMapper;
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

@ApplicationScoped
public class AwsBillableUsageAggregateConsumer {

  private static final UsageInfoPrefixedLogger log =
      new UsageInfoPrefixedLogger(AwsBillableUsageAggregateConsumer.class);

  private final Counter acceptedCounter;
  private final Counter rejectedCounter;
  private final Counter ignoreCounter;
  private final InternalSubscriptionsApi internalSubscriptionsApi;
  private final AwsMarketplaceMeteringClientFactory awsMarketplaceMeteringClientFactory;
  private final Optional<Boolean> isDryRun;
  private final Duration awsUsageWindow;

  public AwsBillableUsageAggregateConsumer(
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

    var tracebackInfoPrefix = new UsageInfo();

    log.info(
        tracebackInfoPrefix,
        "Picked up billable usage message {} to process",
        billableUsageAggregate);
    if (billableUsageAggregate == null || billableUsageAggregate.getAggregateKey() == null) {
      log.warn(tracebackInfoPrefix, "Skipping null billable usage: deserialization failure?");
      return;
    }

    String orgId = billableUsageAggregate.getAggregateKey().getOrgId();

    tracebackInfoPrefix =
        UsageInfoMapper.INSTANCE.toUsageInfo(billableUsageAggregate.getAggregateKey());
    if (orgId != null) {
      MDC.put("org_id", orgId);
    }

    Optional<Metric> metric =
        validateUsageAndLookupMetric(billableUsageAggregate.getAggregateKey());
    if (metric.isEmpty()) {
      log.debug(
          tracebackInfoPrefix,
          "Skipping billable usage because it is not applicable for this service: {}",
          billableUsageAggregate);
      return;
    } else {

      log.info(
          tracebackInfoPrefix, "Processing billable usage message: {}", billableUsageAggregate);
    }

    AwsUsageContext context;
    try {
      context = lookupAwsUsageContext(billableUsageAggregate);
    } catch (SubscriptionRecentlyTerminatedException e) {
      log.info(
          tracebackInfoPrefix,
          "Subscription recently terminated for billableUsageAggregateId={} orgId={} remittanceUUIDs={}",
          billableUsageAggregate.getAggregateId(),
          orgId,
          billableUsageAggregate.getRemittanceUuids());
      return;
    } catch (AwsUsageContextLookupException e) {
      log.error(
          tracebackInfoPrefix,
          "Error looking up aws usage context for aggregateId={} orgId={} remittanceUUIDs={}",
          billableUsageAggregate.getAggregateId(),
          orgId,
          billableUsageAggregate.getRemittanceUuids(),
          e);
      return;
    }
    try {
      transformAndSend(context, billableUsageAggregate, metric.get());
    } catch (UsageTimestampOutOfBoundsException e) {
      log.warn(
          tracebackInfoPrefix,
          "{} remittanceUUIDs={} aggregateId={} windowTimestamp={} rhSubscriptionId={} awsCustomerId={} awsProductCode={} subscriptionStartDate={} value={}",
          e.getMessage(),
          billableUsageAggregate.getRemittanceUuids(),
          billableUsageAggregate.getAggregateId(),
          billableUsageAggregate.getWindowTimestamp(),
          context.getRhSubscriptionId(),
          context.getCustomerId(),
          context.getProductCode(),
          context.getSubscriptionStartDate(),
          billableUsageAggregate.getTotalValue());
      ignoreCounter.increment();
    } catch (Exception e) {
      log.error(
          tracebackInfoPrefix,
          "Error sending aws usage for rhSubscriptionId={} aggregateId={} awsCustomerId={} awsProductCode={} orgId={} remittanceUUIDs={}",
          context.getRhSubscriptionId(),
          billableUsageAggregate.getAggregateId(),
          context.getCustomerId(),
          context.getProductCode(),
          orgId,
          billableUsageAggregate.getRemittanceUuids(),
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
      throws AwsUnprocessedRecordsException, UsageTimestampOutOfBoundsException {
    BatchMeterUsageRequest request =
        BatchMeterUsageRequest.builder()
            .productCode(context.getProductCode())
            .usageRecords(transformToAwsUsage(context, billableUsageAggregate, metric))
            .build();

    UsageInfo tracebackInfoPrefix =
        UsageInfoMapper.INSTANCE.toUsageInfo(billableUsageAggregate.getAggregateKey());

    List<String> remittanceUuids = billableUsageAggregate.getRemittanceUuids();
    if (isDryRun.isPresent() && Boolean.TRUE.equals(isDryRun.get())) {
      log.info(
          tracebackInfoPrefix,
          "[DRY RUN] Sending usage request to AWS: {}, remittanceUUIDs={}",
          request,
          remittanceUuids);
      return;
    } else {
      log.info(
          tracebackInfoPrefix,
          "Sending usage request to AWS: {}, remittanceUUIDs={}",
          request,
          remittanceUuids);
    }

    try {
      MarketplaceMeteringClient marketplaceMeteringClient =
          awsMarketplaceMeteringClientFactory.buildMarketplaceMeteringClient(context);
      BatchMeterUsageResponse response = send(marketplaceMeteringClient, request);
      log.debug(tracebackInfoPrefix, "{}", response);
      response
          .results()
          .forEach(
              result -> {
                if (result.status() == UsageRecordResultStatus.CUSTOMER_NOT_SUBSCRIBED) {
                  log.warn(
                      tracebackInfoPrefix,
                      "No subscription found for remittanceUUIDs={}, result={}",
                      remittanceUuids,
                      result);
                } else if (result.status() != UsageRecordResultStatus.SUCCESS) {
                  log.warn(tracebackInfoPrefix, "{}, remittanceUUIDs={}", result, remittanceUuids);
                } else {
                  log.info(tracebackInfoPrefix, "{}, remittanceUUIDs={}", result, remittanceUuids);
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
          tracebackInfoPrefix,
          "{} for remittanceUUIDs={}, awsCustomerId={}",
          e.getMessage(),
          remittanceUuids,
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
      throws UsageTimestampOutOfBoundsException {

    UsageInfo traceBackInfo =
        UsageInfoMapper.INSTANCE.toUsageInfo(billableUsageAggregate.getAggregateKey());

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
          traceBackInfo
              + ", Unable to send usage since it is outside of the AWS processing window");
    }

    return UsageRecord.builder()
        .customerIdentifier(context.getCustomerId())
        .dimension(metric.getAwsDimension())
        .quantity(billableUsageAggregate.getTotalValue().intValueExact())
        .timestamp(effectiveTimestamp.toInstant())
        .build();
  }

  private Optional<Metric> validateUsageAndLookupMetric(BillableUsageAggregateKey aggregationKey) {

    UsageInfo tracebackInfoPrefix = UsageInfoMapper.INSTANCE.toUsageInfo(aggregationKey);

    if (!Objects.equals(aggregationKey.getBillingProvider(), BillingProviderEnum.AWS.value())) {
      log.debug(tracebackInfoPrefix, "Snapshot not applicable because billingProvider is not AWS");
      return Optional.empty();
    }

    if (aggregationKey.getMetricId() == null) {
      log.debug(tracebackInfoPrefix, "Snapshot not applicable because billable metric is empty");
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
      log.debug(
          tracebackInfoPrefix,
          "Snapshot not applicable because metricId/productId combination is not configured for AWS.");
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
