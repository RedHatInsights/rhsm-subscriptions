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
package com.redhat.swatch.metrics.admin.api;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.metrics.configuration.ApplicationConfiguration;
import com.redhat.swatch.metrics.configuration.MetricProperties;
import com.redhat.swatch.metrics.service.PrometheusMeteringController;
import com.redhat.swatch.metrics.service.PrometheusMetricsTaskManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;

@Slf4j
@ApplicationScoped
public class InternalMeteringResource implements DefaultApi {

  @Context SecurityContext securityContext;
  private final PrometheusMetricsTaskManager tasks;
  private final PrometheusMeteringController controller;
  private final MetricProperties metricProperties;
  private final ApplicationConfiguration applicationConfiguration;
  private final ApplicationClock clock;

  public InternalMeteringResource(
      PrometheusMetricsTaskManager tasks,
      PrometheusMeteringController controller,
      MetricProperties metricProperties,
      ApplicationConfiguration applicationConfiguration,
      ApplicationClock clock) {
    this.tasks = tasks;
    this.controller = controller;
    this.metricProperties = metricProperties;
    this.clock = clock;
    this.applicationConfiguration = applicationConfiguration;
  }

  @Override
  public void syncMetricsForAllAccounts() {
    SubscriptionDefinition.getSubscriptionDefinitions().stream()
        .filter(SubscriptionDefinition::isPrometheusEnabled)
        .flatMap(subDef -> subDef.getVariants().stream())
        .map(Variant::getTag)
        .forEach(
            productTag -> {
              try {
                tasks.updateMetricsForAllAccounts(productTag);
              } catch (Exception e) {
                log.error(
                    "Error updating metrics of product tag {} for all accounts. ", productTag, e);
              }
            });
  }

  @Override
  public void meterProductForOrgIdAndRange(
      String productTag,
      @NotNull String orgId,
      OffsetDateTime endDate,
      @Min(0) Integer rangeInMinutes,
      Boolean xRhSwatchSynchronousRequest) {
    Object principal = securityContext.getUserPrincipal();

    if (Objects.isNull(rangeInMinutes)) {
      rangeInMinutes = metricProperties.rangeInMinutes();
    }
    OffsetDateTime end = getDate(Optional.ofNullable(endDate));
    OffsetDateTime start = getStartDate(end, rangeInMinutes);

    var subDef = SubscriptionDefinition.lookupSubscriptionByTag(productTag);
    if (subDef.isEmpty() || !subDef.get().isPrometheusEnabled()) {
      throw new BadRequestException(String.format("Invalid product tag specified: %s", productTag));
    }

    log.info(
        "{} metering for {} against range [{}, {}) triggered via API by {}",
        productTag,
        orgId,
        start,
        end,
        principal);

    if (xRhSwatchSynchronousRequest != null && xRhSwatchSynchronousRequest) {
      if (!applicationConfiguration.isEnableSynchronousOperations()) {
        throw new BadRequestException("Synchronous metering operations are not enabled.");
      }
      performMeteringForOrgId(orgId, productTag, start, end, subDef.get());
    } else {
      queueMeteringForOrgId(orgId, productTag, start, end);
    }
  }

  private void performMeteringForOrgId(
      String orgId,
      String productTag,
      OffsetDateTime start,
      OffsetDateTime end,
      SubscriptionDefinition subDef) {
    log.info("Performing {} metering for orgId={} via API.", productTag, orgId);

    subDef
        .getMetricIds()
        .forEach(
            metric -> {
              try {
                controller.collectMetrics(
                    productTag, MetricId.fromString(metric), orgId, start, end);
              } catch (Exception e) {
                log.error(
                    "Problem collecting metrics: {} {} {} [{} -> {}]",
                    orgId,
                    productTag,
                    metric,
                    start,
                    end,
                    e);
              }
            });
  }

  private void queueMeteringForOrgId(
      String orgId, String productTag, OffsetDateTime start, OffsetDateTime end) {
    try {
      log.info("Queuing {} metering for orgId={} via API.", productTag, orgId);
      tasks.updateMetricsForOrgId(orgId, productTag, start, end);
    } catch (Exception e) {
      log.error("Error queuing {} metering for orgId={} via API.", productTag, orgId, e);
    }
  }

  private OffsetDateTime getDate(Optional<OffsetDateTime> optionalDate) {
    if (optionalDate.isPresent()) {
      OffsetDateTime date = optionalDate.get();
      if (!date.isEqual(clock.startOfHour(date))) {
        throw new IllegalArgumentException(
            String.format("Date must start at top of the hour: %s", date));
      }
      return date;
    }
    // Default to the top of the current hour.
    return clock.startOfCurrentHour();
  }

  private OffsetDateTime getStartDate(OffsetDateTime endDate, Integer rangeInMinutes) {
    if (rangeInMinutes == null) {
      throw new IllegalArgumentException("Required argument: rangeInMinutes");
    }

    if (rangeInMinutes < 0) {
      throw new IllegalArgumentException("Invalid value specified (Must be >= 0): rangeInMinutes");
    }

    OffsetDateTime result = endDate.minusMinutes(rangeInMinutes);
    if (!result.isEqual(clock.startOfHour(result))) {
      throw new IllegalArgumentException(
          String.format(
              "endDate %s - range %s produces time not at top of the hour: %s",
              endDate, rangeInMinutes, result));
    }
    return result;
  }
}
