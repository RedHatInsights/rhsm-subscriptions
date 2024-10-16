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
package com.redhat.swatch.clients.rbac;

import com.redhat.swatch.clients.rbac.api.model.Access;
import java.util.List;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;

/** Provides RBAC functionality. */
@AllArgsConstructor
public class RbacService {

  private final RbacApi api;

  public List<String> getPermissions(String rbacAppName) throws RbacApiException {
    // Get all permissions for the configured application name.
    try (Stream<Access> accessStream = api.getCurrentUserAccess(rbacAppName).stream()) {
      return accessStream
          .filter(access -> access != null && hasText(access.getPermission()))
          .map(Access::getPermission)
          .toList();
    }
  }

  public List<String> getPermissions(String rbacAppName, String identity) throws RbacApiException {
    // Get all permissions for the configured application name.
    try (Stream<Access> accessStream =
        api.getCurrentIdentityAccess(rbacAppName, identity).stream()) {
      return accessStream
          .filter(access -> access != null && hasText(access.getPermission()))
          .map(Access::getPermission)
          .toList();
    }
  }

  private boolean hasText(String str) {
    if (str == null) {
      return false;
    }

    for (int i = 0; i < str.length(); ++i) {
      if (!Character.isWhitespace(str.charAt(i))) {
        return true;
      }
    }

    return false;
  }
}
