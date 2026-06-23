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
package com.redhat.swatch.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KesselPrincipalIdsTest {

  @Test
  void resolvesUserIdFromTopLevelIdentityField() {
    var identity = Identity.builder().type("User").orgId("org123").userId("user456").build();

    assertEquals("user456", KesselPrincipalIds.fromIdentity(identity).orElseThrow());
  }

  @Test
  void resolvesUserIdFromNestedUserObject() {
    var identity =
        Identity.builder()
            .type("User")
            .orgId("org123")
            .user(User.builder().userId("nested789").build())
            .build();

    assertEquals("nested789", KesselPrincipalIds.fromIdentity(identity).orElseThrow());
  }

  @Test
  void resolvesServiceAccountFromUserIdWhenPresent() {
    var identity =
        Identity.builder()
            .type("ServiceAccount")
            .orgId("org123")
            .userId("sa-user-id")
            .serviceAccount(ServiceAccount.builder().clientId("client-id").build())
            .build();

    assertEquals("sa-user-id", KesselPrincipalIds.fromIdentity(identity).orElseThrow());
  }

  @Test
  void resolvesServiceAccountFromClientIdWhenUserIdMissing() {
    var identity =
        Identity.builder()
            .type("ServiceAccount")
            .orgId("org123")
            .serviceAccount(ServiceAccount.builder().clientId("client-id").build())
            .build();

    assertEquals("client-id", KesselPrincipalIds.fromIdentity(identity).orElseThrow());
  }

  @Test
  void ignoresOrgIdForUserPrincipalResolution() {
    var identity = Identity.builder().type("User").orgId("org123").build();

    assertTrue(KesselPrincipalIds.fromIdentity(identity).isEmpty());
  }
}
