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
package org.candlepin.subscriptions.inventory;

import org.candlepin.subscriptions.exception.inventory.InventoryServiceException;
import org.candlepin.subscriptions.inventory.client.InventoryServiceProperties;
import org.candlepin.subscriptions.inventory.client.model.CreateHostIn;
import org.candlepin.subscriptions.inventory.client.resources.HostsApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;


/**
 * The default wrapper for the insights inventory client.
 */
public class DefaultInventoryService extends InventoryService {
    private static final Logger log = LoggerFactory.getLogger(DefaultInventoryService.class);

    private final HostsApi hostsInventoryApi;

    public DefaultInventoryService(HostsApi hostsInventoryApi, InventoryServiceProperties serviceProperties) {
        super(serviceProperties, serviceProperties.getApiHostUpdateBatchSize());
        this.hostsInventoryApi = hostsInventoryApi;
    }

    @Override
    protected void sendHostUpdate(List<ConduitFacts> facts) {
        // The same timestamp for the whole batch
        OffsetDateTime now = OffsetDateTime.now();
        List<CreateHostIn> hostsToSend = facts.stream()
            .map(conduitFacts -> createHost(conduitFacts, now))
            .collect(Collectors.toList());

        try {
            log.debug("Sending host updates to inventory via API.");
            hostsInventoryApi.apiHostAddHostList(hostsToSend);
        }
        catch (Exception e) {
            throw new InventoryServiceException(
                "An error occurred while sending a host update to the inventory service.", e);
        }
    }

}
