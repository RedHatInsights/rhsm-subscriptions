/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.insights.inventory.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class HostsApiFactoryTest {
    @Test
    public void testStubClientConfiguration() throws Exception {
        InventoryServiceProperties config = new InventoryServiceProperties();
        config.setUseStub(true);
        HostsApiFactory factory = new HostsApiFactory(config);
        assertEquals(StubHostsApi.class, factory.getObject().getClass());
    }

    @Test
    public void testClientGetsUrlFromConfiguration() throws Exception {
        InventoryServiceProperties config = new InventoryServiceProperties();
        config.setUrl("http://example.com/foobar");
        HostsApiFactory factory = new HostsApiFactory(config);
        assertEquals("http://example.com/foobar", factory.getObject().getApiClient().getBasePath());
    }
}
