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
package org.candlepin.subscriptions.metering.task;

import com.redhat.swatch.configuration.registry.MetricId;
import java.time.OffsetDateTime;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMeteringController;
import org.candlepin.subscriptions.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pulls metrics from Telemeter and translates them into events and puts them into the event stream.
 */
public class MetricsTask implements Task {

  private static final Logger log = LoggerFactory.getLogger(MetricsTask.class);

  private final String orgId;
  private final String productTag;
  private final MetricId metric;
  private final OffsetDateTime start;
  private final OffsetDateTime end;

  private final PrometheusMeteringController controller;

  public MetricsTask(
      PrometheusMeteringController controller,
      String orgId,
      String productTag,
      MetricId metric,
      OffsetDateTime start,
      OffsetDateTime end) {
    this.controller = controller;
    this.orgId = orgId;
    this.productTag = productTag;
    this.metric = metric;
    this.start = start;
    this.end = end;
  }

  @Override
  public void execute() {
    log.info("Running {} {} metrics update task for orgId: {}", productTag, metric, orgId);
    try {
      controller.collectMetrics(productTag, metric, orgId, start, end);
      log.info("{} {} metrics task complete.", productTag, metric);
    } catch (Exception e) {
      log.error("Problem running task: {}", this.getClass().getSimpleName(), e);
    }
  }
}
