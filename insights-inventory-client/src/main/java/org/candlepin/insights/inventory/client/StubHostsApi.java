/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.insights.inventory.client;

import org.candlepin.insights.inventory.client.model.BulkHostOut;
import org.candlepin.insights.inventory.client.model.BulkHostOutDetails;
import org.candlepin.insights.inventory.client.model.CreateHostIn;
import org.candlepin.insights.inventory.client.resources.HostsApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Stub of the HostsApi that doesn't make requests, for the methods used by rhsm-conduit.
 */
public class StubHostsApi extends HostsApi {

    private static Logger log = LoggerFactory.getLogger(StubHostsApi.class);

    @Override
    public BulkHostOut apiHostAddHostList(List<CreateHostIn> hosts) throws ApiException {
        log.info("Adding specified hosts to inventory: {}", hosts);
        BulkHostOut out = new BulkHostOut().total(hosts.size());
        hosts.forEach(h -> {
            BulkHostOutDetails hostOutDetails = new BulkHostOutDetails()
                .title(String.format("Host updated: %s", h.getSubscriptionManagerId()))
                .status(200)
                .detail("Update complete!");
            out.addDataItem(hostOutDetails);
        });
        return out;
    }
}
