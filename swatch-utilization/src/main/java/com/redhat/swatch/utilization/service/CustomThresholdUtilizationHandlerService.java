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

import com.redhat.swatch.utilization.model.Measurement;
import com.redhat.swatch.utilization.model.Severity;
import com.redhat.swatch.utilization.model.UtilizationSummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

@Slf4j
@ApplicationScoped
public class CustomThresholdUtilizationHandlerService
    extends BaseThresholdUtilizationHandlerService {

  public static final String CUSTOM_THRESHOLD_METRIC = "swatch_utilization_custom_threshold";
  public static final String PREFERENCES_HASH = "preferences_hash";

  static final String EVENT_TYPE = "exceeded-custom-utilization-threshold";

  @Inject OrgPreferencesService orgPreferencesService;
  @Inject CustomThresholdValidator customThresholdValidator;

  @Override
  protected Optional<HandlerEvent> evaluateThreshold(
      double utilizationPercent, UtilizationSummary payload, Measurement measurement) {
    var preference = orgPreferencesService.getOrgPreferences(payload.getOrgId());

    int threshold = preference.getCustomThreshold();
    if (!customThresholdValidator.isValid(threshold)) {
      int defaultThreshold = orgPreferencesService.getDefaultThreshold();
      log.warn(
          "Retrieved invalid custom threshold '{}'. OrgId: {}. Using default threshold '{}'.",
          threshold,
          payload.getOrgId(),
          defaultThreshold);
      threshold = defaultThreshold;
    } else if (preference.getLastModified() == null) {
      log.debug(
          "No persisted preference for orgId={}, threshold value {}% returned by preferences service matches default",
          payload.getOrgId(), threshold);
    }

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
      if (preference.getLastModified() != null) {
        event.addContextProperty(
            PREFERENCES_HASH, hashLastModified(preference.getLastModified().toInstant()));
      }
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

  static String hashLastModified(Instant lastModified) {
    String canonicalTimestamp = lastModified.getEpochSecond() + ":" + lastModified.getNano();
    return DigestUtils.sha256Hex(canonicalTimestamp);
  }
}
