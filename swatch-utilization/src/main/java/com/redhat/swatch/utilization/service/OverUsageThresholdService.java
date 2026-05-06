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
package com.redhat.swatch.utilization.service;

import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.swatch.utilization.model.Measurement;
import com.redhat.swatch.utilization.model.Severity;
import com.redhat.swatch.utilization.model.UtilizationSummary;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class OverUsageThresholdService extends BaseThresholdService {

  public static final String OVER_USAGE_METRIC = "swatch_utilization_over_usage";

  static final String EVENT_TYPE = "exceeded-utilization-threshold";

  @Override
  protected Optional<Event> evaluateThreshold(
      double utilizationPercent,
      double overUsageThreshold,
      UtilizationSummary payload,
      Measurement measurement) {
    if (overUsageThreshold < 0.0) {
      log.debug(
          "Skipping over-usage check for orgId={} productId={} due to negative threshold={}",
          payload.getOrgId(),
          payload.getProductId(),
          overUsageThreshold);
      return Optional.empty();
    }

    double overagePercent = utilizationPercent - FULL_CAPACITY_PERCENT;

    if (overagePercent > overUsageThreshold) {
      log.info(
          "Over-usage detected: orgId={} productId={} metricId={} sla={} usage={} utilizationPercent={}% overagePercent={}% threshold={}%",
          payload.getOrgId(),
          payload.getProductId(),
          measurement.getMetricId(),
          payload.getSla(),
          payload.getUsage(),
          String.format(PERCENT_FORMAT, utilizationPercent),
          String.format(PERCENT_FORMAT, overagePercent),
          String.format(PERCENT_FORMAT, overUsageThreshold));
      return Optional.of(buildEvent(utilizationPercent));
    }

    log.debug(
        "Usage within threshold: orgId={} productId={} metricId={} sla={} usage={} utilizationPercent={}% overagePercent={}% threshold={}%",
        payload.getOrgId(),
        payload.getProductId(),
        measurement.getMetricId(),
        payload.getSla(),
        payload.getUsage(),
        String.format(PERCENT_FORMAT, utilizationPercent),
        String.format(PERCENT_FORMAT, overagePercent),
        String.format(PERCENT_FORMAT, overUsageThreshold));
    return Optional.empty();
  }

  @Override
  protected String eventType() {
    return EVENT_TYPE;
  }

  @Override
  protected Severity severity() {
    return Severity.IMPORTANT;
  }

  @Override
  protected String metricName() {
    return OVER_USAGE_METRIC;
  }
}
