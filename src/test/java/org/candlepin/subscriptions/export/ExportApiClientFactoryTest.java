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
package org.candlepin.subscriptions.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.redhat.swatch.clients.export.SwatchExportApi;
import com.redhat.swatch.clients.export.api.resources.ExportApi;
import org.candlepin.subscriptions.clients.ClientConfig;
import org.candlepin.subscriptions.clients.ClientConnection;
import org.candlepin.subscriptions.clients.ClientInfo;
import org.junit.jupiter.api.Test;

class ExportApiClientFactoryTest {

  private static final ClientConfig CONFIG =
      new ClientConfig(
          "export-service",
          "psk",
          new ClientConnection("v2", "https://v2.example", null, null, null, true),
          new ClientConnection("v1", "http://v1.example", null, null, null, null),
          null);

  @Test
  void getObjectReturnsStickyExportApi() {
    ExportApiClientFactory factory = new ExportApiClientFactory(CONFIG, () -> "token");

    ExportApi api = factory.getObject();
    assertInstanceOf(SwatchExportApi.class, api);
    assertInstanceOf(ClientInfo.class, api);
    assertEquals(SwatchExportApi.class, factory.getObjectType());
  }
}
