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
import static com.redhat.swatch.configuration.registry.SubscriptionDefinition.getBillingFactor;

import com.redhat.swatch.azure.exception.AzureMarketplaceRequestFailedException;
import com.redhat.swatch.azure.exception.AzureUnprocessedRecordsException;
import com.redhat.swatch.azure.exception.AzureUsageContextLookupException;
import com.redhat.swatch.azure.exception.DefaultApiException;
import com.redhat.swatch.azure.exception.SubscriptionCanNotBeDeterminedException;
import com.redhat.swatch.azure.exception.SubscriptionRecentlyTerminatedException;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEvent;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEventStatusEnum;
import com.redhat.swatch.clients.contracts.api.model.AzureUsageContext;
import com.redhat.swatch.clients.contracts.api.resources.ApiException;
import com.redhat.swatch.clients.contracts.api.resources.DefaultApi;
import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.faulttolerance.api.RetryWithExponentialBackoff;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response.Status;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.MDC;

@Slf4j
@ApplicationScoped
public class AzureBillableUsageAggregateConsumer {
  protected static final String METERED_TOTAL_METRIC = "swatch_producer_metered_total";

  private final BillableUsageStatusProducer billableUsageStatusProducer;
  private final MeterRegistry meterRegistry;
  private final Counter acceptedCounter;
  private final Counter rejectedCounter;
  private final DefaultApi internalSubscriptionsApi;
  private final Optional<Boolean> isDryRun;
  private final Duration azureUsageWindow;
  private final AzureMarketplaceService azureMarketplaceService;

  @Inject
  public AzureBillableUsageAggregateConsumer(
      MeterRegistry meterRegistry,
      @RestClient DefaultApi contractsApi,
      AzureMarketplaceService azureMarketplaceService,
      BillableUsageStatusProducer billableUsageStatusProducer,
      @ConfigProperty(name = "ENABLE_AZURE_DRY_RUN") Optional<Boolean> isDryRun,
      @ConfigProperty(name = "AZURE_MARKETPLACE_USAGE_WINDOW") Duration azureUsageWindow) {
    this.meterRegistry = meterRegistry;
    this.acceptedCounter = meterRegistry.counter("swatch_azure_marketplace_batch_accepted_total");
    this.rejectedCounter = meterRegistry.counter("swatch_azure_marketplace_batch_rejected_total");
    this.internalSubscriptionsApi = contractsApi;
    this.azureMarketplaceService = azureMarketplaceService;
    this.billableUsageStatusProducer = billableUsageStatusProducer;
    this.isDryRun = isDryRun;
    this.azureUsageWindow = azureUsageWindow;
  }

  @SuppressWarnings("java:S3776")
  @Incoming(BILLABLE_USAGE_HOURLY_AGGREGATE)
  @Blocking
  public void process(BillableUsageAggregate billableUsageAggregate) {
    log.info("Received billable usage message {}", billableUsageAggregate);
    if (billableUsageAggregate == null || billableUsageAggregate.getAggregateKey() == null) {
      log.warn("Skipping null billable usage: deserialization failure?");
      return;
    }

    if (!isForAzure(billableUsageAggregate.getAggregateKey())) {
      log.debug("Snapshot not applicable because billingProvider is not Azure");
      return;
    }

    if (billableUsageAggregate.getAggregateKey().getOrgId() != null) {
      MDC.put("org_id", billableUsageAggregate.getAggregateKey().getOrgId());
    }

    Optional<Metric> metric = lookupMetric(billableUsageAggregate.getAggregateKey());
    if (metric.isEmpty() || metric.get().getAzureDimension() == null) {
      log.warn(
          "Skipping billable usage because the metric is not supported: {}",
          billableUsageAggregate);
      emitErrorStatusOnUsage(billableUsageAggregate, BillableUsage.ErrorCode.UNSUPPORTED_METRIC);
      return;
    }

    log.info("Processing billable usage message: {}", billableUsageAggregate);

    AzureUsageContext context;
    try {
      context = lookupAzureUsageContext(billableUsageAggregate);
    } catch (SubscriptionRecentlyTerminatedException e) {
      emitErrorStatusOnUsage(
          billableUsageAggregate, BillableUsage.ErrorCode.SUBSCRIPTION_TERMINATED);
      log.info(
          "Subscription recently terminated for billableUsageAggregate={}",
          billableUsageAggregate,
          e);
      return;
    } catch (SubscriptionCanNotBeDeterminedException e) {
      if (!isUsageDateValid(billableUsageAggregate)) {
        log.warn(
            "Skipping billable usage aggregate={} because the subscription was not found and the snapshot '{}' is past the azure time window of '{}'",
            billableUsageAggregate,
            billableUsageAggregate.getWindowTimestamp(),
            azureUsageWindow,
            e);
        emitErrorStatusOnUsage(billableUsageAggregate, BillableUsage.ErrorCode.INACTIVE);
      } else {
        log.warn("Subscription not found for for aggregate={}", billableUsageAggregate, e);
        emitErrorStatusOnUsage(
            billableUsageAggregate, BillableUsage.ErrorCode.SUBSCRIPTION_NOT_FOUND);
      }
      return;
    } catch (AzureUsageContextLookupException e) {
      emitErrorStatusOnUsage(billableUsageAggregate, BillableUsage.ErrorCode.USAGE_CONTEXT_LOOKUP);
      log.error("Error looking up usage context for aggregate={}", billableUsageAggregate, e);
      return;
    }
    try {
      transformAndSend(context, billableUsageAggregate, metric.get());
      emitSuccessfulStatusOnUsage(billableUsageAggregate);
    } catch (Exception e) {
      emitErrorStatusOnUsage(billableUsageAggregate, BillableUsage.ErrorCode.UNKNOWN);
      log.error(
          "Error sending azure usage for aggregate={}, azureResourceId={}",
          billableUsageAggregate,
          context.getAzureResourceId(),
          e);
    }
  }

  private boolean isUsageDateValid(BillableUsageAggregate aggregate) {
    OffsetDateTime startOfCurrentHour =
        OffsetDateTime.now(Clock.systemUTC()).truncatedTo(ChronoUnit.HOURS);
    OffsetDateTime cutoff = startOfCurrentHour.minus(azureUsageWindow);
    var earliestSnapshotDate = aggregate.getSnapshotDates().stream().sorted().findFirst();
    return !earliestSnapshotDate.map(date -> date.isBefore(cutoff)).orElse(false);
  }

  private void transformAndSend(
      AzureUsageContext context, BillableUsageAggregate billableUsageAggregate, Metric metric)
      throws AzureUnprocessedRecordsException {
    var usageEvent = transformToAzureUsage(context, billableUsageAggregate, metric);
    if (isDryRun.isPresent() && Boolean.TRUE.equals(isDryRun.get())) {
      log.info(
          "[DRY RUN] Sending usage request to Azure: {}, for aggregate={}",
          usageEvent,
          billableUsageAggregate);
      return;
    } else {
      log.info(
          "Sending usage request to Azure: {}, for aggregate={}",
          usageEvent,
          billableUsageAggregate);
    }

    try {
      var response = azureMarketplaceService.sendUsageEventToAzureMarketplace(usageEvent);
      log.debug("{}", response);
      if (response.getStatus() != UsageEventStatusEnum.ACCEPTED) {
        log.warn("{}, aggregate={}", response, billableUsageAggregate);
      } else {
        log.info("{}, aggregate={}", response, billableUsageAggregate);
        acceptedCounter.increment();
      }
    } catch (AzureMarketplaceRequestFailedException e) {
      rejectedCounter.increment();
      throw new AzureUnprocessedRecordsException(e);
    }
  }

  private UsageEvent transformToAzureUsage(
      AzureUsageContext context, BillableUsageAggregate billableUsageAggregate, Metric metric) {
    var usage = new UsageEvent();
    usage.setDimension(metric.getAzureDimension());
    usage.setPlanId(context.getPlanId());
    usage.setResourceId(context.getAzureResourceId());
    usage.setClientId(context.getClientId());
    usage.setQuantity(billableUsageAggregate.getTotalValue().doubleValue());
    usage.setEffectiveStartTime(billableUsageAggregate.getWindowTimestamp());
    return usage;
  }

  @RetryWithExponentialBackoff(
      maxRetries = "${AZURE_USAGE_CONTEXT_LOOKUP_RETRIES}",
      retryOn = AzureUsageContextLookupException.class)
  public AzureUsageContext lookupAzureUsageContext(BillableUsageAggregate billableUsageAggregate)
      throws AzureUsageContextLookupException {
    try {
      return internalSubscriptionsApi.getAzureMarketplaceContext(
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
      if (Status.NOT_FOUND.getStatusCode() == e.getResponse().getStatus()) {
        throw new SubscriptionCanNotBeDeterminedException(e);
      }
      throw new AzureUsageContextLookupException(e);
    } catch (ProcessingException | ApiException e) {
      throw new AzureUsageContextLookupException(e);
    }
  }

  private boolean isForAzure(BillableUsageAggregateKey aggregationKey) {
    return Objects.equals(
        aggregationKey.getBillingProvider(), BillableUsage.BillingProvider.AZURE.value());
  }

  private Optional<Metric> lookupMetric(BillableUsageAggregateKey aggregationKey) {
    if (aggregationKey.getMetricId() == null) {
      log.debug("Snapshot not applicable because billable metric id is empty");
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
          "Snapshot not applicable because productId and/or metric id is not configured for Azure");
    }

    return metric;
  }

  private void emitSuccessfulStatusOnUsage(BillableUsageAggregate usage) {
    usage.setStatus(BillableUsage.Status.SUCCEEDED);
    usage.setErrorCode(null);
    usage.setBilledOn(OffsetDateTime.now());
    incrementMeteredTotal(usage);
    billableUsageStatusProducer.emitStatus(usage);
  }

  private void emitErrorStatusOnUsage(
      BillableUsageAggregate usage, BillableUsage.ErrorCode errorCode) {
    usage.setStatus(BillableUsage.Status.FAILED);
    usage.setErrorCode(errorCode);
    incrementMeteredTotal(usage);
    billableUsageStatusProducer.emitStatus(usage);
  }

  private void incrementMeteredTotal(BillableUsageAggregate usage) {
    // add metrics for aggregation
    List<String> tags =
        new ArrayList<>(
            List.of(
                "product",
                usage.getAggregateKey().getProductId(),
                "metric_id",
                MetricId.tryGetValueFromString(usage.getAggregateKey().getMetricId())));
    if (usage.getAggregateKey().getBillingProvider() != null) {
      tags.addAll(List.of("billing_provider", usage.getAggregateKey().getBillingProvider()));
    }

    if (usage.getStatus() != null) {
      tags.addAll(List.of("status", usage.getStatus().toString()));
    }

    if (usage.getErrorCode() != null) {
      tags.addAll(List.of("error_code", usage.getErrorCode().toString()));
    }

    double amount =
        usage.getTotalValue().doubleValue()
            / getBillingFactor(
                usage.getAggregateKey().getProductId(), usage.getAggregateKey().getMetricId());
    meterRegistry.counter(METERED_TOTAL_METRIC, tags.toArray(new String[0])).increment(amount);
  }
}
