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

import com.redhat.swatch.azure.openapi.model.BillableUsage;
import com.redhat.swatch.azure.openapi.model.BillableUsage.BillingProviderEnum;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.InternalSubscriptionsApi;
import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.Variant;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.MDC;

@Slf4j
@ApplicationScoped
public class BillableUsageConsumer {
  private final Counter
      acceptedCounter; // TODO https://issues.redhat.com/browse/SWATCH-1726 //NOSONAR
  private final Counter
      rejectedCounter; // TODO https://issues.redhat.com/browse/SWATCH-1726 //NOSONAR
  private final InternalSubscriptionsApi
      internalSubscriptionsApi; // TODO https://issues.redhat.com/browse/SWATCH-1726 //NOSONAR
  private final Optional<Boolean>
      isDryRun; // TODO https://issues.redhat.com/browse/SWATCH-1726 //NOSONAR

  public BillableUsageConsumer(
      MeterRegistry meterRegistry,
      @RestClient InternalSubscriptionsApi internalSubscriptionsApi,
      @ConfigProperty(name = "ENABLE_AZURE_DRY_RUN") Optional<Boolean> isDryRun) {
    acceptedCounter = meterRegistry.counter("swatch_azure_marketplace_batch_accepted_total");
    rejectedCounter = meterRegistry.counter("swatch_azure_marketplace_batch_rejected_total");
    this.internalSubscriptionsApi = internalSubscriptionsApi;
    this.isDryRun = isDryRun;
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
    if (billableUsage.getAccountNumber() != null) {
      MDC.put("account_id", billableUsage.getAccountNumber());
    }

    Optional<Metric> metric = validateUsageAndLookupMetric(billableUsage);
    if (metric.isEmpty()) {
      log.debug("Skipping billable usage because it is not applicable: {}", billableUsage);
      return; // please remove me after below TODO implemented! NOSONAR
    }
    /* TODO https://issues.redhat.com/browse/SWATCH-1726 //NOSONAR
    (reference swatch-producer-aws BillableUsageProcessor)
    - lookup azure usage context
    - transform and send
     */
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
