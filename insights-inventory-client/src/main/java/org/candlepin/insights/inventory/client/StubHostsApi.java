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

import org.candlepin.insights.inventory.client.model.HostOut;
import org.candlepin.insights.inventory.client.model.HostQueryOutput;
import org.candlepin.insights.inventory.client.resources.HostsApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Stub of the HostsApi that doesn't make requests, for the methods used by subscriptions.
 */
public class StubHostsApi extends HostsApi {

    private static Logger log = LoggerFactory.getLogger(StubHostsApi.class);

    @Override
    public HostQueryOutput apiHostGetHostList(String displayName, String fqdn, String hostnameOrId,
        UUID insightsId, List<String> tags, String branchId, Integer perPage, Integer page, String orderBy,
        String orderHow) throws ApiException {
        log.info("Getting stub host list");
        HostQueryOutput hostQueryOutput = new HostQueryOutput();
        HostOut hostOut = new HostOut();
        // TODO fill in some dummy data for the hostOut
        hostQueryOutput.addResultsItem(hostOut);
        return hostQueryOutput;
    }
}
