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

import io.micrometer.core.annotation.Timed;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.BadRequestException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.metering.ResourceUtil;
import org.candlepin.subscriptions.metering.admin.api.InternalApi;
import org.candlepin.subscriptions.metering.retention.EventRecordsRetentionProperties;
import org.candlepin.subscriptions.metering.service.prometheus.MetricProperties;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMeteringController;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InternalMeteringResource implements InternalApi {
  private final ResourceUtil util;
  private final ApplicationProperties applicationProperties;
  private final PrometheusMetricsTaskManager tasks;
  private final PrometheusMeteringController controller;
  private final AccountConfigRepository accountConfigRepository;
  private final TagProfile tagProfile;
  private final EventRecordsRetentionProperties eventRecordsRetentionProperties;
  private final EventRecordRepository eventRecordRepository;
  private final MetricProperties metricProperties;

  public InternalMeteringResource(
      ResourceUtil util,
      ApplicationProperties applicationProperties,
      EventRecordsRetentionProperties eventRecordsRetentionProperties,
      TagProfile tagProfile,
      PrometheusMetricsTaskManager tasks,
      PrometheusMeteringController controller,
      AccountConfigRepository accountConfigRepository,
      EventRecordRepository eventRecordRepository,
      MetricProperties metricProperties) {
    this.util = util;
    this.applicationProperties = applicationProperties;
    this.eventRecordsRetentionProperties = eventRecordsRetentionProperties;
    this.tagProfile = tagProfile;
    this.tasks = tasks;
    this.controller = controller;
    this.accountConfigRepository = accountConfigRepository;
    this.eventRecordRepository = eventRecordRepository;
    this.metricProperties = metricProperties;
  }

  @Override
  @Transactional
  @Timed("rhsm-subscriptions.events.purge")
  public void purgeEventRecords() {
    var eventRetentionDuration = eventRecordsRetentionProperties.getEventRetentionDuration();

    OffsetDateTime cutoffDate =
        OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minus(eventRetentionDuration);

    log.info("Purging event records older than {}", cutoffDate);
    eventRecordRepository.deleteInBulkEventRecordsByTimestampBefore(cutoffDate);
    log.info("Event record purge completed successfully");
  }

  protected void meterProductForAllAccounts(
      String productTag, OffsetDateTime endDate, Integer rangeInMinutes) {
    if (Objects.isNull(rangeInMinutes)) {
      rangeInMinutes = metricProperties.getRangeInMinutes();
    }
    OffsetDateTime end = util.getDate(Optional.ofNullable(endDate));
    OffsetDateTime start = util.getStartDate(end, rangeInMinutes);

    log.info("Metering {} for all accounts in the past {} minutes", productTag, rangeInMinutes);

    try {
      tasks.updateMetricsForAllAccounts(productTag, start, end);
    } catch (Exception e) {
      log.error("Error triggering {} metering for all accounts.", productTag, e);
    }
  }

  @Override
  public void meterProductForAccount(
      String productTag,
      String accountNumber,
      String orgId,
      OffsetDateTime endDate,
      @Min(0) Integer rangeInMinutes,
      Boolean xRhSwatchSynchronousRequest) {
    Object principal = ResourceUtils.getPrincipal();

    if (orgId == null && accountNumber == null) {
      meterProductForAllAccounts(productTag, endDate, rangeInMinutes);
    } else if (orgId == null) {
      orgId = accountConfigRepository.findOrgByAccountNumber(accountNumber);
      if (orgId == null) {
        throw new BadRequestException(
            String.format("Unable to look up orgId for accountNumber: %s", accountNumber));
      }
    }

    if (Objects.isNull(rangeInMinutes)) {
      rangeInMinutes = metricProperties.getRangeInMinutes();
    }
    OffsetDateTime end = util.getDate(Optional.ofNullable(endDate));
    OffsetDateTime start = util.getStartDate(end, rangeInMinutes);

    if (!tagProfile.tagIsPrometheusEnabled(productTag)) {
      throw new BadRequestException(String.format("Invalid product tag specified: %s", productTag));
    }

    log.info(
        "{} metering for {} against range [{}, {}) triggered via API by {}",
        productTag,
        accountNumber,
        start,
        end,
        principal);

    if (ResourceUtils.sanitizeBoolean(xRhSwatchSynchronousRequest, false)) {
      if (!applicationProperties.isEnableSynchronousOperations()) {
        throw new BadRequestException("Synchronous metering operations are not enabled.");
      }
      performMeteringForOrgId(orgId, productTag, start, end);
    } else {
      queueMeteringForOrgId(orgId, productTag, start, end);
    }
  }

  private void performMeteringForOrgId(
      String orgId, String productTag, OffsetDateTime start, OffsetDateTime end) {
    log.info("Performing {} metering for orgId={} via API.", productTag, orgId);
    tagProfile
        .getSupportedMetricsForProduct(productTag)
        .forEach(
            metric -> {
              try {
                controller.collectMetrics(productTag, metric, orgId, start, end);
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
