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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClientsInfoContributorTest {

  @Test
  void toInfoMapsClientDescribePayloads() {
    ClientConnection connection =
        new ClientConnection("v1", "http://localhost:10000", null, null, null, null);
    ClientConfig config = new ClientConfig("export-service", "psk", connection, null, null);
    ClientInfo client =
        new ClientInfo() {
          @Override
          public ClientConfig getConfig() {
            return config;
          }
        };

    ClientsInfoContributor contributor =
        new ClientsInfoContributor() {
          @Override
          protected List<ClientInfo> getClients() {
            return List.of(client);
          }
        };

    Map<String, Object> info = contributor.toInfo();
    assertTrue(info.containsKey("export-service"));
    assertEquals(ClientsInfoContributor.INFO_SECTION, "dependencies");
    @SuppressWarnings("unchecked")
    Map<String, Object> export = (Map<String, Object>) info.get("export-service");
    assertEquals("v1", export.get("current"));
  }
}
