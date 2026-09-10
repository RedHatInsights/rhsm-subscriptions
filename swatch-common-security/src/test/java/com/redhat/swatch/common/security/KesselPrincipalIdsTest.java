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
  void resolvesServiceAccountFromServiceAccountUserId() {
    // Per identity schema, user_id is required in service_account object
    var identity =
        Identity.builder()
            .type("ServiceAccount")
            .orgId("org123")
            .serviceAccount(
                ServiceAccount.builder()
                    .clientId("b69eaf9e-e6a6-4f9e-805e-02987daddfbd")
                    .username("service-account-b69eaf9e-e6a6-4f9e-805e-02987daddfbd")
                    .userId("60ce65dc-4b5a-4812-8b65-b48178d92b12")
                    .build())
            .build();

    assertEquals(
        "60ce65dc-4b5a-4812-8b65-b48178d92b12",
        KesselPrincipalIds.fromIdentity(identity).orElseThrow());
  }

  @Test
  void serviceAccountWithoutUserIdReturnsEmpty() {
    var identity =
        Identity.builder()
            .type("ServiceAccount")
            .orgId("org123")
            .serviceAccount(
                ServiceAccount.builder()
                    .clientId("client-id")
                    .username("service-account-client-id")
                    .build())
            .build();

    assertTrue(KesselPrincipalIds.fromIdentity(identity).isEmpty());
  }

  @Test
  void ignoresOrgIdForUserPrincipalResolution() {
    var identity = Identity.builder().type("User").orgId("org123").build();

    assertTrue(KesselPrincipalIds.fromIdentity(identity).isEmpty());
  }
}
