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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.project_kessel.api.inventory.v1beta2.Allowed;

class MockKesselServerTest {

  MockKesselServer kesselServer;
  KesselAuthorizationService service;
  RhIdentityPrincipalFactory identityFactory;

  @BeforeEach
  void setup() throws IOException {
    kesselServer = new MockKesselServer();
    kesselServer.start();

    service = new KesselAuthorizationService();
    service.setStub(kesselServer.blockingStub());
    service.properties =
        new KesselProperties() {
          @Override
          public String endpoint() {
            return "localhost:9000";
          }

          @Override
          public boolean insecure() {
            return true;
          }

          @Override
          public long timeoutMs() {
            return 5000L;
          }
        };

    identityFactory = new RhIdentityPrincipalFactory();
    identityFactory.mapper = new ObjectMapper();
  }

  @AfterEach
  void teardown() {
    kesselServer.stop();
  }

  private RhIdentityPrincipal principal() {
    try {
      return identityFactory.fromJson(RhIdentityUtils.CUSTOMER_IDENTITY_JSON);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void allowAllGrantsBothPermissions() {
    kesselServer.allowAll();
    List<String> permissions = service.getPermissions(principal());
    assertEquals(List.of("subscriptions:*:*", "subscriptions:reports:read"), permissions);
  }

  @Test
  void denyAllGrantsNoPermissions() {
    kesselServer.denyAll();
    List<String> permissions = service.getPermissions(principal());
    assertTrue(permissions.isEmpty());
  }

  @Test
  void selectivePermissions() {
    kesselServer.setResponse("subscriptions_report_view", Allowed.ALLOWED_TRUE);

    assertTrue(service.checkAccess(principal(), "subscriptions:reports:read"));
    assertTrue(service.checkAccess(principal(), "subscriptions:*:*"));

    List<String> permissions = service.getPermissions(principal());
    assertEquals(List.of("subscriptions:*:*", "subscriptions:reports:read"), permissions);
  }

  @Test
  void resetClearsResponses() {
    kesselServer.allowAll();
    assertTrue(service.checkAccess(principal(), "subscriptions:*:*"));

    kesselServer.reset();
    assertFalse(service.checkAccess(principal(), "subscriptions:*:*"));
  }
}
