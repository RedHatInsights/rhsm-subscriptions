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
package org.candlepin.subscriptions.metering.api.admin;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.Variant;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.metering.ResourceUtil;
import org.candlepin.subscriptions.metering.admin.api.InternalProductMeteringApi;
import org.candlepin.subscriptions.metering.service.prometheus.MetricProperties;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMeteringController;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InternalMeteringResource implements InternalProductMeteringApi {
  private final ResourceUtil util;
  private final ApplicationProperties applicationProperties;
  private final PrometheusMetricsTaskManager tasks;
  private final PrometheusMeteringController controller;
  private final MetricProperties metricProperties;
  private final RetryTemplate retryTemplate;

  public InternalMeteringResource(
      ResourceUtil util,
      ApplicationProperties applicationProperties,
      PrometheusMetricsTaskManager tasks,
      PrometheusMeteringController controller,
      MetricProperties metricProperties,
      @Qualifier("meteringJobRetryTemplate") RetryTemplate retryTemplate) {
    this.util = util;
    this.applicationProperties = applicationProperties;
    this.tasks = tasks;
    this.controller = controller;
    this.metricProperties = metricProperties;
    this.retryTemplate = retryTemplate;
  }

  @Override
  public void syncMetricsForAllAccounts() {
    int range = metricProperties.getRangeInMinutes();

    SubscriptionDefinition.getSubscriptionDefinitions().stream()
        .filter(SubscriptionDefinition::isPrometheusEnabled)
        .flatMap(subDef -> subDef.getVariants().stream())
        .map(Variant::getTag)
        .forEach(
            productTag -> {
              try {
                tasks.updateMetricsForAllAccounts(productTag, range, retryTemplate);
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
    Object principal = ResourceUtils.getPrincipal();

    if (Objects.isNull(rangeInMinutes)) {
      rangeInMinutes = metricProperties.getRangeInMinutes();
    }
    OffsetDateTime end = util.getDate(Optional.ofNullable(endDate));
    OffsetDateTime start = util.getStartDate(end, rangeInMinutes);

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

    if (ResourceUtils.sanitizeBoolean(xRhSwatchSynchronousRequest, false)) {
      if (!applicationProperties.isEnableSynchronousOperations()) {
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
}
