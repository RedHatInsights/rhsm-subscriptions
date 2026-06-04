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
package com.redhat.swatch.utilization.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.utilization.model.SendNotificationVariantPayload;
import io.getunleash.Unleash;
import io.getunleash.variant.Variant;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class FeatureFlags {
  public static final String SEND_NOTIFICATIONS = "swatch.swatch-notifications.send-notifications";
  public static final String SEND_NOTIFICATIONS_CONFIG_VARIANT = "send-notifications-config";
  public static final String SEND_NOTIFICATIONS_ORGS_ALLOWLIST =
      "swatch.swatch-notifications.send-notifications-orgs-allowlist";
  public static final String ORGS_VARIANT = "orgs";

  private final Unleash unleash;
  private final ObjectMapper mapper;

  /**
   * Whether the {@value #SEND_NOTIFICATIONS} toggle allows sending for the given notification event
   * type.
   *
   * <p>When the toggle is disabled, returns {@code false}. When enabled, an optional variant may
   * supply a JSON object with an {@code event_types_denylist} array; if the event type is in that
   * list, returns {@code false}. If the variant is absent, invalid, or the event type is not
   * listed, returns {@code true}.
   */
  public boolean sendNotifications(String eventType) {
    if (!unleash.isEnabled(SEND_NOTIFICATIONS)) {
      return false;
    }

    Variant variant = unleash.getVariant(SEND_NOTIFICATIONS);
    if (!SEND_NOTIFICATIONS_CONFIG_VARIANT.equals(variant.getName())) {
      log.debug("Feature flag '{}' with no valid variant '{}'", SEND_NOTIFICATIONS, variant);
      return true;
    }

    if (!variant.isEnabled()) {
      return true;
    }

    return mapToSendNotificationPayload(variant)
        .map(
            p -> {
              List<String> denylist = p.getEventTypesDenylist();
              return denylist == null || !denylist.contains(eventType);
            })
        .orElse(true);
  }

  public boolean isOrgAllowlistedForNotifications(String orgId) {
    if (!unleash.isEnabled(SEND_NOTIFICATIONS_ORGS_ALLOWLIST)) {
      log.debug("Feature flag '{}' is disabled", SEND_NOTIFICATIONS_ORGS_ALLOWLIST);
      return false;
    }

    Variant variant = unleash.getVariant(SEND_NOTIFICATIONS_ORGS_ALLOWLIST);
    if (!ORGS_VARIANT.equals(variant.getName()) || variant.getPayload().isEmpty()) {
      log.debug("Feature flag '{}' with no variants", SEND_NOTIFICATIONS_ORGS_ALLOWLIST);
      return false;
    }

    String value = variant.getPayload().get().getValue();
    if (value == null || value.isBlank()) {
      log.debug(
          "Feature flag '{}' with no organizations in the allowlist",
          SEND_NOTIFICATIONS_ORGS_ALLOWLIST);
      return false;
    }

    log.debug(
        "Feature flag '{}' with the following organizations '{}' in the allowlist",
        SEND_NOTIFICATIONS_ORGS_ALLOWLIST,
        value);

    return Arrays.stream(value.split(",")).map(String::trim).anyMatch(orgId::equals);
  }

  private Optional<SendNotificationVariantPayload> mapToSendNotificationPayload(Variant variant) {
    var payload = variant.getPayload();
    if (payload.isEmpty()) {
      return Optional.empty();
    }

    String payloadValue = payload.get().getValue();
    try {
      return Optional.ofNullable(
          mapper.readValue(payloadValue, SendNotificationVariantPayload.class));
    } catch (JsonProcessingException e) {
      log.warn(
          "Failed to parse the payload '{}' for feature flag '{}'",
          payloadValue,
          SEND_NOTIFICATIONS,
          e);
      return Optional.empty();
    }
  }
}
