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
import com.redhat.swatch.utilization.data.OrgUtilizationPreferenceRepository;
import com.redhat.swatch.utilization.model.Measurement;
import com.redhat.swatch.utilization.model.Severity;
import com.redhat.swatch.utilization.model.UtilizationSummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

@Slf4j
@ApplicationScoped
public class CustomUsageThresholdService extends BaseThresholdService {

  public static final String CUSTOM_THRESHOLD_METRIC = "swatch_utilization_custom_threshold";

  static final String EVENT_TYPE = "exceeded-custom-utilization-threshold";

  @Inject OrgUtilizationPreferenceRepository preferenceRepository;

  @Transactional
  @Override
  public boolean check(UtilizationSummary payload, Measurement measurement) {
    return super.check(payload, measurement);
  }

  @Override
  protected Optional<Event> evaluateThreshold(
      double utilizationPercent,
      double overUsageThreshold,
      UtilizationSummary payload,
      Measurement measurement) {
    if (utilizationPercent > FULL_CAPACITY_PERCENT + overUsageThreshold) {
      log.debug(
          "Skipping custom threshold check: over-usage alert will be triggered for orgId={} productId={}",
          payload.getOrgId(),
          payload.getProductId());
      return Optional.empty();
    }

    var preferenceOpt = preferenceRepository.findByIdOptional(payload.getOrgId());
    if (preferenceOpt.isEmpty()) {
      log.debug(
          "No org preference found for orgId={}, skipping custom threshold check",
          payload.getOrgId());
      return Optional.empty();
    }

    var preference = preferenceOpt.get();
    int threshold = preference.getCustomThreshold();

    if (utilizationPercent >= threshold) {
      log.info(
          "Custom threshold exceeded: orgId={} productId={} metricId={} sla={} usage={} utilizationPercent={}% threshold={}%",
          payload.getOrgId(),
          payload.getProductId(),
          measurement.getMetricId(),
          payload.getSla(),
          payload.getUsage(),
          String.format(PERCENT_FORMAT, utilizationPercent),
          threshold);
      var event = buildEvent(utilizationPercent);
      String lastUpdatedHash = hashLastUpdated(preference.getLastUpdated());
      event.getPayload().getAdditionalProperties().put("last_updated_hash", lastUpdatedHash);
      return Optional.of(event);
    }

    return Optional.empty();
  }

  @Override
  protected String eventType() {
    return EVENT_TYPE;
  }

  @Override
  protected Severity severity() {
    return Severity.MODERATE;
  }

  @Override
  protected String metricName() {
    return CUSTOM_THRESHOLD_METRIC;
  }

  static String hashLastUpdated(OffsetDateTime lastUpdated) {
    return DigestUtils.sha256Hex(lastUpdated.toString());
  }
}
