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
package com.redhat.swatch.billable.usage.configuration;

import static com.redhat.swatch.info.UnleashInfoFeatureFlags.snapshot;

import com.redhat.swatch.info.InfoFeatureFlagContributor;
import com.redhat.swatch.info.model.InfoFeatureFlags;
import com.redhat.swatch.kafka.config.ReadOnlyProvider;
import io.getunleash.Unleash;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class FeatureFlags implements InfoFeatureFlagContributor, ReadOnlyProvider {

  public static final String READ_ONLY_FLAG = "swatch.swatch-billable-usage.enable-read-only";
  protected static final boolean DEFAULT_READ_ONLY_FLAG_VALUE = false;
  public static final String USE_SUBSCRIPTIONS_FLAG =
      "swatch.swatch-contracts.use-subscriptions";
  protected static final boolean DEFAULT_USE_SUBSCRIPTIONS_FLAG_VALUE = false;

  private final Unleash unleash;

  /** Whether the service is in read-only mode. */
  @Override
  public boolean isReadOnly() {
    return unleash.isEnabled(READ_ONLY_FLAG, DEFAULT_READ_ONLY_FLAG_VALUE);
  }

  /** Whether the service should use subscription data. */
  public boolean useSubscriptions() {
    return unleash.isEnabled(USE_SUBSCRIPTIONS_FLAG, DEFAULT_USE_SUBSCRIPTIONS_FLAG_VALUE);
  }

  @Override
  public InfoFeatureFlags getFeatureFlags() {
    return snapshot(
        unleash,
        DEFAULT_READ_ONLY_FLAG_VALUE,
        READ_ONLY_FLAG,
        USE_SUBSCRIPTIONS_FLAG);
  }
}
