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
package org.candlepin.subscriptions.inventory.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HostsApiFactoryTest {
  @Test
  void testStubClientConfiguration() throws Exception {
    InventoryServiceProperties props = new InventoryServiceProperties();
    props.setUseStub(true);
    HostsApiFactory factory = new HostsApiFactory(props);
    assertEquals(StubHostsApi.class, factory.getObject().getClass());
  }

  @Test
  void testClientGetsUrlFromConfiguration() throws Exception {
    InventoryServiceProperties props = new InventoryServiceProperties();
    props.setApiKey("mysecret");
    props.setUrl("http://example.com/foobar");
    HostsApiFactory factory = new HostsApiFactory(props);
    assertEquals("http://example.com/foobar", factory.getObject().getApiClient().getBasePath());
  }

  @Test
  void testNoErrorWhenApiKeyIsSet() throws Exception {
    InventoryServiceProperties props = new InventoryServiceProperties();
    props.setApiKey("mysecret");

    HostsApiFactory factory = new HostsApiFactory(props);
    assertNotNull(factory.getObject());
  }

  @Test
  void throwsIllegalStateWhenApiKeyIsNull() throws Exception {
    testApiToken(null);
  }

  @Test
  void throwsIllegalStateWhenApiKeyIsEmpty() throws Exception {
    testApiToken("");
  }

  private void testApiToken(String key) {
    InventoryServiceProperties props = new InventoryServiceProperties();
    props.setApiKey(key);
    HostsApiFactory factory = new HostsApiFactory(props);

    assertThrows(IllegalStateException.class, () -> factory.getObject());
  }
}
