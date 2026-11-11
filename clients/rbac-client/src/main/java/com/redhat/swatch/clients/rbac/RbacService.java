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
import java.util.Objects;
import java.util.function.Supplier;

public class RbacService {

  private final RbacApi api;
  private final Supplier<String> identityHeaderProvider;

  public RbacService(RbacApi api) {
    this(api, () -> null);
  }

  public RbacService(RbacApi api, Supplier<String> identityHeaderProvider) {
    this.api = Objects.requireNonNull(api, "api");
    this.identityHeaderProvider =
        Objects.requireNonNull(identityHeaderProvider, "identityHeaderProvider");
  }

  public List<String> getPermissions(String applicationName) throws RbacApiException {
    String identity = identityHeaderProvider.get();
    List<Access> accesses =
        identity == null
            ? api.getCurrentUserAccess(applicationName)
            : api.getCurrentIdentityAccess(applicationName, identity);
    return permissions(accesses);
  }

  public List<String> getPermissions(String applicationName, String identity)
      throws RbacApiException {
    return permissions(api.getCurrentIdentityAccess(applicationName, identity));
  }

  private static List<String> permissions(List<Access> accesses) {
    if (accesses == null) {
      return List.of();
    }
    return accesses.stream()
        .filter(Objects::nonNull)
        .map(Access::getPermission)
        .filter(RbacService::hasText)
        .toList();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
