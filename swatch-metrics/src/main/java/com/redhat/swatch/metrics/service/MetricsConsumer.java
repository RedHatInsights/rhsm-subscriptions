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
package com.redhat.swatch.metrics.service;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.metrics.model.MetricsTaskDescriptor;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
public class MetricsConsumer {

  private static final String ORG_ID = "orgId";
  private static final String METRIC = "metric";
  private static final String PRODUCT_TAG = "productTag";
  private static final String START = "start";
  private static final String END = "end";

  private final PrometheusMeteringController service;

  public MetricsConsumer(PrometheusMeteringController service) {
    this.service = service;
  }

  @Incoming("tasks-in")
  @Blocking
  public void process(MetricsTaskDescriptor task) {
    log.info(
        "Running {} {} metrics update task for orgId: {}",
        task.getProductTag(),
        task.getMetric(),
        task.getOrgId());
    try {
      validate(task);
      service.collectMetrics(
          task.getProductTag(),
          MetricId.fromString(task.getMetric()),
          task.getOrgId(),
          task.getStart(),
          task.getEnd());
      log.info("{} {} metrics task complete.", task.getProductTag(), task.getMetric());
    } catch (Exception e) {
      log.error("Problem running task: {}", this.getClass().getSimpleName(), e);
    }
  }

  private void validate(MetricsTaskDescriptor task) {
    validate(task.getOrgId(), ORG_ID);
    validate(task.getProductTag(), PRODUCT_TAG);
    validate(task.getMetric(), METRIC);
    validate(task.getStart(), START);
    validate(task.getEnd(), END);
  }

  private void validate(String str, String name) {
    if (str == null || str.isEmpty()) {
      throw new IllegalArgumentException(
          String.format("Could not build task. '%s' was empty.", name));
    }
  }

  private void validate(OffsetDateTime date, String name) {
    if (date == null) {
      throw new IllegalArgumentException(
          String.format("Could not build task. '%s' was empty.", name));
    }
  }
}
