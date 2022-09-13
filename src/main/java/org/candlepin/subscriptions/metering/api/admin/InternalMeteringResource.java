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

import java.time.OffsetDateTime;
import java.util.Optional;
import javax.ws.rs.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.metering.BaseMeteringResource;
import org.candlepin.subscriptions.metering.admin.api.InternalApi;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMeteringController;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InternalMeteringResource extends BaseMeteringResource implements InternalApi {

  private final ApplicationProperties applicationProperties;
  private final PrometheusMetricsTaskManager tasks;
  private final PrometheusMeteringController controller;
  private final TagProfile tagProfile;

  public InternalMeteringResource(
      ApplicationProperties applicationProperties,
      ApplicationClock clock,
      TagProfile tagProfile,
      PrometheusMetricsTaskManager tasks,
      PrometheusMeteringController controller) {
    super(clock);
    this.applicationProperties = applicationProperties;
    this.tagProfile = tagProfile;
    this.tasks = tasks;
    this.controller = controller;
  }

  @Override
  public void meterProductForAccount(
      String accountNumber,
      String productTag,
      Integer rangeInMinutes,
      OffsetDateTime endDate,
      Boolean xRhSwatchSynchronousRequest) {
    Object principal = ResourceUtils.getPrincipal();

    if (!tagProfile.tagIsPrometheusEnabled(productTag)) {
      throw new BadRequestException(String.format("Invalid product tag specified: %s", productTag));
    }

    OffsetDateTime end = getDate(Optional.ofNullable(endDate));
    OffsetDateTime start = getStartDate(end, rangeInMinutes);
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
      performMeteringForAccount(accountNumber, productTag, start, end);
    } else {
      queueMeteringForAccount(accountNumber, productTag, start, end);
    }
  }

  private void performMeteringForAccount(
      String accountNumber, String productTag, OffsetDateTime start, OffsetDateTime end) {
    log.info("Performing {} metering for account {} via API.", productTag, accountNumber);
    tagProfile
        .getSupportedMetricsForProduct(productTag)
        .forEach(
            metric -> {
              try {
                controller.collectMetrics(productTag, metric, accountNumber, start, end);
              } catch (Exception e) {
                log.error(
                    "Problem collecting metrics: {} {} {} [{} -> {}]",
                    accountNumber,
                    productTag,
                    metric,
                    start,
                    end,
                    e);
              }
            });
  }

  private void queueMeteringForAccount(
      String accountNumber, String productTag, OffsetDateTime start, OffsetDateTime end) {
    try {
      log.info("Queuing {} metering for account {} via API.", productTag, accountNumber);
      tasks.updateMetricsForAccount(accountNumber, productTag, start, end);
    } catch (Exception e) {
      log.error("Error queuing {} metering for account {} via API.", productTag, accountNumber, e);
    }
  }
}
