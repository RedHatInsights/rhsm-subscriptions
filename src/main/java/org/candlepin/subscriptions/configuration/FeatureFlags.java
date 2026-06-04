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
package org.candlepin.subscriptions.configuration;

import io.getunleash.Unleash;
import lombok.extern.slf4j.Slf4j;

/**
 * Feature flags service for managing Unleash feature toggles in the Spring Boot application.
 *
 * <p>This class provides methods to check feature flag status using Unleash. Feature flags allow
 * runtime control of application features without redeployment.
 */
@Slf4j
public class FeatureFlags {

  // Feature flag names
  public static final String ENABLE_PRIMARY_ROW_SEARCHES =
      "swatch.swatch-tally.enable-primary-row-searches";

  private final Unleash unleash;

  public FeatureFlags(Unleash unleash) {
    this.unleash = unleash;
  }

  /**
   * Check if primary row searches are enabled.
   *
   * @return true if primary row searches feature flag is enabled
   */
  public boolean isPrimaryRowSearchesEnabled() {
    if (unleash == null) {
      return false; // Default to disabled when Unleash is not available
    }
    return unleash.isEnabled(ENABLE_PRIMARY_ROW_SEARCHES);
  }

  /**
   * Check if a feature flag is enabled by name.
   *
   * @param featureName the feature flag name
   * @return true if the feature flag is enabled
   */
  public boolean isEnabled(String featureName) {
    return unleash.isEnabled(featureName);
  }
}
