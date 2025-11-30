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

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.utilization.model.Measurement;
import com.redhat.swatch.utilization.model.UtilizationSummary;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class UtilizationSummaryMeasurementValidator {

  private static final double MIN_VALID_CAPACITY = 0.0;

  public boolean isMeasurementValid(UtilizationSummary payload, Measurement measurement) {
    List<MetricId> supportedMetrics =
        MetricIdUtils.getMetricIdsFromConfigForTag(payload.getProductId()).toList();
    if (!isValidMetric(measurement.getMetricId(), supportedMetrics, payload)) {
      return false;
    }

    if (Boolean.TRUE.equals(measurement.getUnlimited())) {
      logValidationFailure(
          payload, "The metric '%s' has unlimited=true".formatted(measurement.getMetricId()));
      return false;
    }

    if (measurement.getCapacity() == null) {
      logValidationFailure(
          payload, "The metric '%s' capacity is null".formatted(measurement.getMetricId()));
      return false;
    }

    if (measurement.getCapacity() <= MIN_VALID_CAPACITY) {
      logValidationFailure(
          payload,
          "The metric '%s' capacity (%s) <= MIN_VALID_CAPACITY (%s)"
              .formatted(measurement.getMetricId(), measurement.getCapacity(), MIN_VALID_CAPACITY));
      return false;
    }

    if (measurement.getCurrentTotal() == null) {
      logValidationFailure(
          payload,
          "The metric '%s' currentTotal is null. Capacity: %s"
              .formatted(measurement.getMetricId(), measurement.getCapacity()));
      return false;
    }

    return true;
  }

  private boolean isValidMetric(
      String metricIdString, List<MetricId> supportedMetrics, UtilizationSummary payload) {
    try {
      var metric = MetricId.fromString(metricIdString);
      if (!supportedMetrics.contains(metric)) {
        log.warn(
            "Received utilization summary with unsupported metricId '{}' in product '{}'. Payload: {}",
            metricIdString,
            payload.getProductId(),
            payload);
        return false;
      }
    } catch (IllegalArgumentException e) {
      log.warn(
          "Received utilization summary with invalid metricId '{}'. Payload: {}",
          metricIdString,
          payload);
      return false;
    }

    return true;
  }

  /**
   * Helper method to log validation failures with common context.
   *
   * @param payload the utilization summary being validated
   * @param reason the reason for the validation failure
   */
  private void logValidationFailure(UtilizationSummary payload, String reason) {
    log.debug(
        "Validation failed: {}. OrgId: {}, ProductId: {}, BillingAccountId: {}",
        reason,
        payload.getOrgId(),
        payload.getProductId(),
        payload.getBillingAccountId());
  }
}
