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
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.utilization.model.UtilizationSummary;
import com.redhat.swatch.utilization.model.UtilizationSummary.Granularity;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class UtilizationSummaryValidator {

  private static final double MIN_VALID_CAPACITY = 0.0;
  private static final List<Granularity> SUPPORTED_GRANULARITY =
      List.of(Granularity.DAILY, Granularity.HOURLY);

  public boolean isValid(UtilizationSummary payload) {
    return hasValidOrgId(payload)
        && hasValidProduct(payload)
        && hasValidGranularity(payload)
        && hasValidMeasurements(payload)
        && hasValidBillingForPayg(payload);
  }

  private boolean hasValidOrgId(UtilizationSummary payload) {
    if (payload.getOrgId() == null) {
      log.warn("Received utilization summary without orgId. Payload: {}", payload);
      return false;
    }

    return true;
  }

  private boolean hasValidProduct(UtilizationSummary payload) {
    try {
      ProductId.fromString(payload.getProductId());
    } catch (IllegalArgumentException e) {
      log.warn(
          "Received utilization summary with invalid productId '{}'. Payload: {}",
          payload.getProductId(),
          payload);
      return false;
    }

    return true;
  }

  private boolean hasValidGranularity(UtilizationSummary payload) {
    if (payload.getGranularity() == null) {
      logValidationFailure(payload, "granularity is null");
      return false;
    }
    UtilizationSummary.Granularity granularity = payload.getGranularity();
    if (granularity == null || !SUPPORTED_GRANULARITY.contains(granularity)) {
      logValidationFailure(
          payload,
          "unsupported granularity '" + granularity + "'. Supported: " + SUPPORTED_GRANULARITY);
      return false;
    }
    return true;
  }

  private boolean hasValidMeasurements(UtilizationSummary payload) {
    if (payload.getMeasurements() == null || payload.getMeasurements().isEmpty()) {
      log.warn("Received utilization summary without measurements. Payload: {}", payload);
      return false;
    }

    List<MetricId> supportedMetrics =
        MetricIdUtils.getMetricIdsFromConfigForTag(payload.getProductId()).toList();
    for (var measurement : payload.getMeasurements()) {
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
                .formatted(
                    measurement.getMetricId(), measurement.getCapacity(), MIN_VALID_CAPACITY));
        return false;
      }

      if (measurement.getCurrentTotal() == null) {
        logValidationFailure(
            payload,
            "The metric '%s' currentTotal is null. Capacity: %s"
                .formatted(measurement.getMetricId(), measurement.getCapacity()));
        return false;
      }
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

  private boolean hasValidBillingForPayg(UtilizationSummary payload) {
    if (!isPaygProduct(payload)) {
      return true;
    }

    if (payload.getBillingAccountId() == null) {
      log.warn("Received utilization summary without payg. Payload: {}", payload);
      return false;
    }
    return true;
  }

  private boolean isPaygProduct(UtilizationSummary payload) {
    try {
      var product = ProductId.fromString(payload.getProductId());
      return product.isPayg();
    } catch (IllegalArgumentException e) {
      return false;
    }
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
