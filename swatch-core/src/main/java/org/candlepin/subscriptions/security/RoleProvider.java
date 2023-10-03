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
package org.candlepin.subscriptions.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds roles based on the provided RBAC permission list. The intent of this class is to process
 * the permissions received from RBAC and define which spring security roles the user should have.
 */
public class RoleProvider {

  public static final String SWATCH_ADMIN_ROLE = "SUBSCRIPTION_WATCH_ADMIN";
  public static final String SWATCH_REPORT_READER = "SUBSCRIPTION_WATCH_REPORT_READER";
  public static final String ROLE_INTERNAL = "ROLE_INTERNAL";

  private String rulePrefix;
  private boolean devModeEnabled;

  public RoleProvider(String rulePrefix, boolean devModeEnabled) {
    this.rulePrefix = rulePrefix;
    this.devModeEnabled = devModeEnabled;
  }

  public Collection<String> getRoles(Collection<String> permissions) {
    if (permissions == null) {
      return List.of();
    }

    Set<String> roles = new HashSet<>();
    // By default, we look for the subscriptions:*:* permission (unless
    // configured otherwise).
    if (permissions.contains(rulePrefix + ":*:*")) {
      roles.add(SWATCH_ADMIN_ROLE);
    }
    if (permissions.contains(rulePrefix + ":reports:read")) {
      roles.add(SWATCH_REPORT_READER);
    }
    if (devModeEnabled) {
      roles.add(SWATCH_ADMIN_ROLE);
      roles.add(ROLE_INTERNAL);
    }
    return roles;
  }
}
