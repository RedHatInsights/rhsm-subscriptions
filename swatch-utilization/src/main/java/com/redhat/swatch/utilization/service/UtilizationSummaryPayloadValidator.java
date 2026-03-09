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

import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.utilization.model.UtilizationSummary;
import com.redhat.swatch.utilization.model.UtilizationSummary.Granularity;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class UtilizationSummaryPayloadValidator {

  private static final List<Granularity> SUPPORTED_GRANULARITY =
      List.of(Granularity.DAILY, Granularity.HOURLY);

  public boolean isUtilizationSummaryValid(UtilizationSummary payload) {
    return hasValidOrgId(payload)
        && hasValidProduct(payload)
        && hasValidGranularity(payload)
        && hasValidBillingForPayg(payload);
  }

  private boolean hasValidOrgId(UtilizationSummary payload) {
    if (payload.getOrgId() == null) {
      log.warn("Received utilization summary without orgId. ProductId: {}", payload.getProductId());
      return false;
    }

    return true;
  }

  private boolean hasValidProduct(UtilizationSummary payload) {
    try {
      ProductId.fromString(payload.getProductId());
    } catch (IllegalArgumentException e) {
      log.warn(
          "Received utilization summary with invalid productId '{}'. OrgId: {}",
          payload.getProductId(),
          payload.getOrgId());
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

  private boolean hasValidBillingForPayg(UtilizationSummary payload) {
    if (!isPaygProduct(payload)) {
      return true;
    }

    if (payload.getBillingAccountId() == null) {
      log.warn(
          "Received utilization summary without billingAccountId for PAYG product '{}'. OrgId: {}",
          payload.getProductId(),
          payload.getOrgId());
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

  private void logValidationFailure(UtilizationSummary payload, String reason) {
    log.debug(
        "Validation failed: {}. OrgId: {}, ProductId: {}, BillingAccountId: {}",
        reason,
        payload.getOrgId(),
        payload.getProductId(),
        payload.getBillingAccountId());
  }
}
