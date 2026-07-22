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

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.candlepin.subscriptions.configuration.FeatureFlags;
import org.candlepin.subscriptions.json.InfoFeatureFlag;
import org.springframework.boot.actuate.info.Info.Builder;
import org.springframework.boot.actuate.info.InfoContributor;

/** InfoContributor to report the status of Unleash feature flags. */
public class FeatureFlagsInfoContributor implements InfoContributor {

  private final FeatureFlags featureFlags;

  public FeatureFlagsInfoContributor(@Nullable FeatureFlags featureFlags) {
    this.featureFlags = featureFlags;
  }

  @Override
  public void contribute(Builder builder) {
    if (featureFlags == null) {
      builder.withDetail("feature-flags", List.of());
      return;
    }

    List<InfoFeatureFlag> flags = new ArrayList<>();
    for (String flag : FeatureFlags.FLAG_LIST) {
      flags.add(new InfoFeatureFlag().withName(flag).withEnabled(featureFlags.isEnabled(flag)));
    }
    builder.withDetail("feature-flags", flags);
  }
}
