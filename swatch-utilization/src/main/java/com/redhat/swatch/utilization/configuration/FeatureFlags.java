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

import io.getunleash.Unleash;
import io.getunleash.variant.Variant;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class FeatureFlags {
  public static final String SEND_NOTIFICATIONS = "swatch.swatch-notifications.send-notifications";
  public static final String SEND_NOTIFICATIONS_ORGS_ALLOWLIST =
      "swatch.swatch-notifications.send-notifications-orgs-whitelist";
  public static final String ORGS_VARIANT = "orgs";

  private final Unleash unleash;

  public FeatureFlags(Unleash unleash) {
    this.unleash = unleash;
  }

  public boolean sendNotifications() {
    return unleash.isEnabled(FeatureFlags.SEND_NOTIFICATIONS);
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
}
