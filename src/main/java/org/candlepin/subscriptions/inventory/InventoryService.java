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

import org.candlepin.insights.inventory.client.ApiClient;
import org.candlepin.insights.inventory.client.ApiException;
import org.candlepin.insights.inventory.client.model.HostOut;
import org.candlepin.insights.inventory.client.model.HostQueryOutput;
import org.candlepin.insights.inventory.client.resources.HostsApi;
import org.candlepin.subscriptions.exception.inventory.InventoryServiceRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

/**
 * A wrapper for the insights inventory client.
 */
@Component
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final HostsApi hostsInventoryApi;

    @Autowired
    public InventoryService(HostsApi hostsInventoryApi) {
        this.hostsInventoryApi = hostsInventoryApi;
    }

    // NOTE: Not being used due because for now we are fetching host data from the inventory DB.
    //       Leaving this here until we know for sure that we won't be using it.
    public List<HostOut> getHosts(String accountNumber) {
        // TODO Need to forge the identity header. Using token based auth would be better
        //      (req inventory change)
        //      but we would need to be able to supply the account number.
        ApiClient client = hostsInventoryApi.getApiClient();
        client.setApiKey(generateApiKey(accountNumber));

        try {
            HostQueryOutput result = hostsInventoryApi.apiHostGetHostList(null, null, null, null, null, null,
                1, null, null);
            List<HostOut> hosts = result.getResults();
            log.info("Found {} hosts.", hosts.size());
            return hosts;
        }
        catch (ApiException e) {
            throw new InventoryServiceRequestException("Could not get list of hosts for the account.", e);
        }
        finally {
            // reset the key so that the client can be reused -- yuck -- see TODO above.
            client.setApiKey(null);
        }
    }

    private String generateApiKey(String accountNumber) {
        String idString = String.format("{\"identity\":{\"account_number\":\"%s\"}}", accountNumber);
        return Base64.getEncoder().encodeToString(idString.getBytes());
    }

}
