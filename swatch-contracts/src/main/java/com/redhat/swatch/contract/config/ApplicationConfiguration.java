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
package com.redhat.swatch.contract.config;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Getter
public class ApplicationConfiguration {
  @ConfigProperty(name = "rhsm-subscriptions.subscription-sync-enabled")
  boolean subscriptionSyncEnabled;

  /** Do not sync any subs that have expired longer than this much in the past from now. */
  @ConfigProperty(name = "SUBSCRIPTION_IGNORE_EXPIRED_OLDER_THAN", defaultValue = "60d")
  Duration subscriptionIgnoreExpiredOlderThan;

  /** Do not sync any subs starting later than this much in the future from now. */
  @ConfigProperty(name = "SUBSCRIPTION_IGNORE_STARTING_LATER_THAN", defaultValue = "60d")
  Duration subscriptionIgnoreStartingLaterThan;

  @ConfigProperty(name = "SUBSCRIPTION_PAGE_SIZE", defaultValue = "1000")
  int subscriptionPageSize;

  @ConfigProperty(name = "DEVTEST_SUBSCRIPTION_EDITING_ENABLED", defaultValue = "true")
  boolean manualSubscriptionEditingEnabled;

  @ConfigProperty(name = "ENABLE_PAYG_SUBSCRIPTION_FORCE_SYNC", defaultValue = "false")
  boolean enablePaygSubscriptionForceSync;
}
