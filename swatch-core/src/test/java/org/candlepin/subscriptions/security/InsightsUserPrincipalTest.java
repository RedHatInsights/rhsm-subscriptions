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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class InsightsUserPrincipalTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void resolvesKesselPrincipalIdFromTopLevelUserId() throws Exception {
    var principal =
        mapper.readValue(
            """
            {
              "account_number": "10001",
              "user_id": "53908736",
              "internal": {"org_id": "11009103"},
              "type": "User"
            }
            """,
            InsightsUserPrincipal.class);

    assertEquals("53908736", principal.getKesselPrincipalId().orElseThrow());
  }

  @Test
  void resolvesKesselPrincipalIdFromNestedUserObject() throws Exception {
    var principal =
        mapper.readValue(
            """
            {
              "internal": {"org_id": "11009103"},
              "type": "User",
              "user": {"user_id": "nested-user"}
            }
            """,
            InsightsUserPrincipal.class);

    assertEquals("nested-user", principal.getKesselPrincipalId().orElseThrow());
  }

  @Test
  void ignoresOrgIdWhenResolvingKesselPrincipalId() throws Exception {
    var principal =
        mapper.readValue(
            """
            {
              "internal": {"org_id": "11009103"},
              "type": "User"
            }
            """,
            InsightsUserPrincipal.class);

    assertTrue(principal.getKesselPrincipalId().isEmpty());
  }

  @Test
  void resolvesServiceAccountFromServiceAccountUserId() throws Exception {
    // Per identity schema, ServiceAccount has user_id in service_account object
    var principal =
        mapper.readValue(
            """
            {
              "internal": {"org_id": "11009103"},
              "type": "ServiceAccount",
              "service_account": {
                "client_id": "b69eaf9e-e6a6-4f9e-805e-02987daddfbd",
                "username": "service-account-b69eaf9e-e6a6-4f9e-805e-02987daddfbd",
                "user_id": "60ce65dc-4b5a-4812-8b65-b48178d92b12"
              }
            }
            """,
            InsightsUserPrincipal.class);

    assertEquals(
        "60ce65dc-4b5a-4812-8b65-b48178d92b12", principal.getKesselPrincipalId().orElseThrow());
  }

  @Test
  void serviceAccountWithoutUserIdReturnsEmpty() throws Exception {
    // Kessel requires user_id - if missing, return empty (don't fall back to client_id)
    var principal =
        mapper.readValue(
            """
            {
              "internal": {"org_id": "11009103"},
              "type": "ServiceAccount",
              "service_account": {
                "client_id": "client-id",
                "username": "service-account-client-id"
              }
            }
            """,
            InsightsUserPrincipal.class);

    assertTrue(principal.getKesselPrincipalId().isEmpty());
  }

  @Test
  void serviceAccountWithTopLevelUserIdFallsBack() throws Exception {
    var principal =
        mapper.readValue(
            """
            {
              "internal": {"org_id": "11009103"},
              "type": "ServiceAccount",
              "user_id": "top-level-fallback-userid",
              "service_account": {
                "client_id": "client-id",
                "username": "service-account-client-id"
              }
            }
            """,
            InsightsUserPrincipal.class);

    assertEquals(
        "top-level-fallback-userid",
        principal.getKesselPrincipalId().orElseThrow(),
        "ServiceAccount should fall back to top-level user_id with warning");
  }
}
