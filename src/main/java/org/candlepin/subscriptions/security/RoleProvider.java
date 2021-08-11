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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builds roles based on the provided RBAC permission list. The intent of this class is to process
 * the permissions received from RBAC and define which spring security roles the user should have.
 */
public class RoleProvider {

  public static final String SWATCH_ADMIN_ROLE = "SUBSCRIPTION_WATCH_ADMIN";
  public static final String SWATCH_REPORT_READER = "SUBSCRIPTION_WATCH_REPORT_READER";

  private String rulePrefix;
  private boolean devModeEnabled;

  public RoleProvider(String rulePrefix, boolean devModeEnabled) {
    this.rulePrefix = rulePrefix;
    this.devModeEnabled = devModeEnabled;
  }

  public List<String> getRoles(Collection<String> permissions) {
    List<String> roles = new ArrayList<>();
    if (permissions == null) {
      return roles;
    }

    // By default, we look for the subscriptions:*:* permission (unless
    // configured otherwise).
    if (devModeEnabled || permissions.contains(rulePrefix + ":*:*")) {
      roles.add(SWATCH_ADMIN_ROLE);
    }
    if (permissions.contains(rulePrefix + ":reports:read")) {
      roles.add(SWATCH_REPORT_READER);
    }
    return roles;
  }
}
