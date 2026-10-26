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
package com.redhat.swatch.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ClientInfoTest {

  private static final ClientConnection FALLBACK =
      new ClientConnection("v1", "http://v1.example", null, null, null, null);

  @Test
  void describeIncludesAuthenticatedAndRedactsUserInfo() {
    ClientConnection primary =
        new ClientConnection(
            "v2",
            "https://user:secret@host.example:443/path",
            "/tmp/ts",
            "pw".toCharArray(),
            "JKS",
            true);
    ClientConfig config = new ClientConfig("export-service", "psk", primary, FALLBACK, null);
    ClientInfo info = () -> config;

    Map<String, Object> description = info.describe();

    assertEquals("export-service", info.name());
    assertEquals("v2", description.get("current"));
    @SuppressWarnings("unchecked")
    Map<String, Object> primaryInfo = (Map<String, Object>) description.get("primary");
    assertEquals("v2", primaryInfo.get("id"));
    assertEquals("https://host.example:443/path", primaryInfo.get("uri"));
    assertEquals(true, primaryInfo.get("authenticated"));
    assertFalse(primaryInfo.containsKey("trustStorePassword"));
    @SuppressWarnings("unchecked")
    Map<String, Object> fallbackInfo = (Map<String, Object>) description.get("fallback");
    assertEquals("v1", fallbackInfo.get("id"));
    assertFalse(fallbackInfo.containsKey("authenticated"));
  }
}
