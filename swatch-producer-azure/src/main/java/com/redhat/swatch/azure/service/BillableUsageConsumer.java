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

import com.redhat.swatch.azure.exception.AzureDimensionNotConfiguredException;
import com.redhat.swatch.azure.exception.AzureMarketplaceRequestFailedException;
import com.redhat.swatch.azure.exception.AzureUnprocessedRecordsException;
import com.redhat.swatch.azure.exception.AzureUsageContextLookupException;
import com.redhat.swatch.azure.exception.DefaultApiException;
import com.redhat.swatch.azure.exception.SubscriptionCanNotBeDeterminedException;
import com.redhat.swatch.azure.exception.SubscriptionRecentlyTerminatedException;
import com.redhat.swatch.azure.exception.UsageTimestampOutOfBoundsException;
import com.redhat.swatch.azure.openapi.model.BillableUsage;
import com.redhat.swatch.azure.openapi.model.BillableUsage.BillingProviderEnum;
import com.redhat.swatch.azure.openapi.model.BillableUsage.SlaEnum;
import com.redhat.swatch.azure.openapi.model.BillableUsage.UsageEnum;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEvent;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEventStatusEnum;
import com.redhat.swatch.clients.swatch.internal.subscription.api.model.AzureUsageContext;
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

@Slf4j
@ApplicationScoped
public class BillableUsageConsumer {

  private final AzureMarketplaceService azureMarketplaceService;
  private final BillableUsageDeadLetterTopicProducer billableUsageDeadLetterTopicProducer;

  private final Counter acceptedCounter;
  private final Counter rejectedCounter;
  private final InternalSubscriptionsApi internalSubscriptionsApi;
  private final Optional<Boolean> isDryRun;
  private final Duration azureUsageWindow;

  public BillableUsageConsumer(
      MeterRegistry meterRegistry,
      @RestClient InternalSubscriptionsApi internalSubscriptionsApi,
      AzureMarketplaceService azureMarketplaceService,
      BillableUsageDeadLetterTopicProducer billableUsageDeadLetterTopicProducer,
      @ConfigProperty(name = "ENABLE_AZURE_DRY_RUN") Optional<Boolean> isDryRun,
      @ConfigProperty(name = "AZURE_MARKETPLACE_USAGE_WINDOW") Duration azureUsageWindow) {
    acceptedCounter = meterRegistry.counter("swatch_azure_marketplace_batch_accepted_total");
    rejectedCounter = meterRegistry.counter("swatch_azure_marketplace_batch_rejected_total");
    this.internalSubscriptionsApi = internalSubscriptionsApi;
    this.azureMarketplaceService = azureMarketplaceService;
    this.billableUsageDeadLetterTopicProducer = billableUsageDeadLetterTopicProducer;
    this.isDryRun = isDryRun;
    this.azureUsageWindow = azureUsageWindow;
  }

  @Incoming("tally-in")
  @Blocking
  public void process(BillableUsage billableUsage) {
    log.debug("Picked up billable usage message {} to process", billableUsage);
    if (billableUsage == null) {
      log.warn("Skipping null billable usage: deserialization failure?");
      return;
    }
    if (billableUsage.getOrgId() != null) {
      MDC.put("org_id", billableUsage.getOrgId());
    }

    Optional<Metric> metric = validateUsageAndLookupMetric(billableUsage);

    AzureUsageContext context;
    if (metric.isEmpty()) {
      log.debug("Skipping billable usage because it is not applicable: {}", billableUsage);
      return;
    }
    try {
      context = lookupAzureUsageContext(billableUsage);
    } catch (SubscriptionRecentlyTerminatedException e) {
      log.info(
          "Subscription recently terminated for tallySnapshotId={} orgId={}",
          billableUsage.getId(),
          billableUsage.getOrgId());
      return;
    } catch (SubscriptionCanNotBeDeterminedException e) {
      if (!isUsageDateValid(billableUsage)) {
        log.warn(
            "Skipping billable usage with id={} orgId={} because the subscription was not found and the snapshot '{}' is past the azure time window of '{}'",
            billableUsage.getId(),
            billableUsage.getOrgId(),
            billableUsage.getSnapshotDate(),
            azureUsageWindow);
      } else {
        billableUsageDeadLetterTopicProducer.send(billableUsage);
        log.warn(
            "Skipping billable usage with id={} orgId={} because the subscription was not found. Will retry again after one hour.",
            billableUsage.getId(),
            billableUsage.getOrgId());
      }

      return;
    } catch (AzureUsageContextLookupException e) {
      log.error(
          "Error looking up usage context for tallySnapshotId={} orgId={}",
          billableUsage.getId(),
          billableUsage.getOrgId(),
          e);
      return;
    }
    try {
      transformAndSend(context, billableUsage, metric.get());
    } catch (Exception e) {
      log.error(
          "Error sending usage for tallySnapshotId={} azureResourceId={} orgId={}",
          billableUsage.getId(),
          context.getAzureResourceId(),
          billableUsage.getOrgId(),
          e);
    }
  }

  private boolean isUsageDateValid(BillableUsage usage) {
    OffsetDateTime startOfCurrentHour =
        OffsetDateTime.now(Clock.systemUTC()).truncatedTo(ChronoUnit.HOURS);
    OffsetDateTime cutoff = startOfCurrentHour.minus(azureUsageWindow);
    return !usage.getSnapshotDate().isBefore(cutoff);
  }

  private void transformAndSend(
      AzureUsageContext context, BillableUsage billableUsage, Metric metric)
      throws AzureUnprocessedRecordsException,
          AzureDimensionNotConfiguredException,
          UsageTimestampOutOfBoundsException {
    var usageEvent = transformToAzureUsage(context, billableUsage, metric);
    if (isDryRun.isPresent() && Boolean.TRUE.equals(isDryRun.get())) {
      log.info(
          "[DRY RUN] Sending usage request to Azure: {}, organization={}, product_id={}",
          usageEvent,
          billableUsage.getOrgId(),
          billableUsage.getProductId());
      return;
    } else {
      log.info(
          "Sending usage request to Azure: {}, organization={}, product_id={}",
          usageEvent,
          billableUsage.getOrgId(),
          billableUsage.getProductId());
    }

    try {
      var response = azureMarketplaceService.sendUsageEventToAzureMarketplace(usageEvent);
      log.debug("{}", response);
      if (response.getStatus() != UsageEventStatusEnum.ACCEPTED) {
        log.warn("{}, organization={}", response, billableUsage.getOrgId());
      } else {
        log.info("{}, organization={},", response, billableUsage.getOrgId());
        acceptedCounter.increment();
      }
    } catch (AzureMarketplaceRequestFailedException e) {
      rejectedCounter.increment();
      throw new AzureUnprocessedRecordsException(e);
    }
  }

  private UsageEvent transformToAzureUsage(
      AzureUsageContext context, BillableUsage billableUsage, Metric metric)
      throws AzureDimensionNotConfiguredException {

    var usage = new UsageEvent();
    usage.setDimension(metric.getAzureDimension());
    usage.setPlanId(context.getPlanId());
    usage.setResourceId(context.getAzureResourceId());
    usage.setQuantity(billableUsage.getValue().doubleValue());
    usage.setEffectiveStartTime(billableUsage.getSnapshotDate());
    return usage;
  }

  @Retry(retryOn = AzureUsageContextLookupException.class)
  public AzureUsageContext lookupAzureUsageContext(BillableUsage billableUsage)
      throws AzureUsageContextLookupException {
    try {
      return internalSubscriptionsApi.getAzureMarketplaceContext(
          billableUsage.getSnapshotDate(),
          billableUsage.getProductId(),
          billableUsage.getOrgId(),
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
        var isSubscriptionCannotBeDeterminedError =
            optionalErrors.get().getErrors().stream()
                .anyMatch(error -> ("SUBSCRIPTIONS1006").equals(error.getCode()));
        if (isSubscriptionCannotBeDeterminedError) {
          throw new SubscriptionCanNotBeDeterminedException(e);
        }
      }

      throw new AzureUsageContextLookupException(e);
    } catch (ProcessingException | ApiException e) {
      throw new AzureUsageContextLookupException(e);
    }
  }

  private Optional<Metric> validateUsageAndLookupMetric(BillableUsage billableUsage) {
    if (billableUsage.getBillingProvider() != BillingProviderEnum.AZURE) {
      log.debug("Snapshot not applicable because billingProvider is not Azure");
      return Optional.empty();
    }

    if (billableUsage.getUom() == null) {
      log.debug("Snapshot not applicable because billable uom is empty");
      return Optional.empty();
    }

    Optional<Metric> metric =
        Variant.findByTag(billableUsage.getProductId()).stream()
            .map(
                v ->
                    v.getSubscription()
                        .getMetric(MetricId.fromString(billableUsage.getUom()).getValue())
                        .orElse(null))
            .filter(Objects::nonNull)
            .findFirst();

    if (metric.isEmpty()) {
      log.debug("Snapshot not applicable because productId and/or uom is not configured for Azure");
    }

    return metric;
  }
}
