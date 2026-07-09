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
package org.candlepin.subscriptions.actuator;

import java.util.LinkedHashMap;
import java.util.Map;
import org.candlepin.subscriptions.configuration.FeatureFlags;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.info.ConditionalOnEnabledInfoContributor;
import org.springframework.boot.actuate.autoconfigure.info.InfoContributorFallback;
import org.springframework.boot.actuate.info.Info.Builder;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/** InfoContributor to report the status of Unleash feature flags. */
@Component
@ConditionalOnEnabledInfoContributor(
    value = "feature-flags",
    fallback = InfoContributorFallback.DISABLE)
public class FeatureFlagsInfoContributor implements InfoContributor {

  @Autowired(required = false)
  private FeatureFlags featureFlags;

  public FeatureFlagsInfoContributor() {}

  public FeatureFlagsInfoContributor(FeatureFlags featureFlags) {
    this.featureFlags = featureFlags;
  }

  @Override
  public void contribute(Builder builder) {
    if (featureFlags == null) {
      builder.withDetail("feature-flags", Map.of());
      return;
    }

    Map<String, Boolean> flagStatus = new LinkedHashMap<>();
    flagStatus.put(
        FeatureFlags.ENABLE_PRIMARY_ROW_SEARCHES,
        featureFlags.isEnabled(FeatureFlags.ENABLE_PRIMARY_ROW_SEARCHES));
    flagStatus.put(
        FeatureFlags.ENABLE_HTB_PRIMARY_ROW_SEARCHES,
        featureFlags.isEnabled(FeatureFlags.ENABLE_HTB_PRIMARY_ROW_SEARCHES));

    builder.withDetail("feature-flags", flagStatus);
  }
}
