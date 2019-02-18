package org.candlepin.insights.inventory.client;

import org.candlepin.insights.inventory.client.model.Host;
import org.candlepin.insights.inventory.client.model.HostOut;
import org.candlepin.insights.inventory.client.resources.HostsApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub of the HostsApi that doesn't make requests, for the methods used by rhsm-conduit.
 */
public class StubHostsApi extends HostsApi {

    private static Logger log = LoggerFactory.getLogger(StubHostsApi.class);

    @Override
    public HostOut apiHostAddHost(byte[] xRhIdentity, Host host) throws ApiException {
        log.info("Using identity: {}, appending host: {}", xRhIdentity, host);
        return new HostOut();
    }
}
