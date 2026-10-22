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
package org.candlepin.subscriptions.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ClientConfigTest {

  private static final ClientConnection PRIMARY =
      new ClientConnection("v2", "https://v2.example", null, null, null, true);
  private static final ClientConnection FALLBACK =
      new ClientConnection("v1", "http://v1.example", null, null, null, null);

  @Test
  void startsOnPrimary() {
    ClientConfig config = new ClientConfig("export-service", "psk", PRIMARY, FALLBACK, null);

    assertEquals("export-service", config.getName());
    assertEquals("psk", config.getPsk());
    assertEquals(PRIMARY, config.current());
    assertEquals(PRIMARY, config.primary());
    assertEquals(FALLBACK, config.fallback());
  }
}
