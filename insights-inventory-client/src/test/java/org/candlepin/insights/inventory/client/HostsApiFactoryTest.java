package org.candlepin.insights.inventory.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class HostsApiFactoryTest {
    @Test
    public void testStubClientConfiguration() throws Exception {
        InventoryServiceConfiguration config = new InventoryServiceConfiguration();
        config.setUseStub(true);
        HostsApiFactory factory = new HostsApiFactory(config);
        assertEquals(StubHostsApi.class, factory.getObject().getClass());
    }

    @Test
    public void testClientGetsUrlFromConfiguration() throws Exception {
        InventoryServiceConfiguration config = new InventoryServiceConfiguration();
        config.setUrl("http://example.com/foobar");
        HostsApiFactory factory = new HostsApiFactory(config);
        assertEquals("http://example.com/foobar", factory.getObject().getApiClient().getBasePath());
    }
}
