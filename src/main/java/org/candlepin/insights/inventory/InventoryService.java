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

import org.candlepin.insights.exceptions.RhsmConduitException;
import org.candlepin.insights.inventory.client.model.FactSet;
import org.candlepin.insights.inventory.client.model.Host;
import org.candlepin.insights.inventory.client.model.HostOut;
import org.candlepin.insights.inventory.client.resources.HostsApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A wrapper for the insights inventory client.
 */
@Component
public class InventoryService {

    private static Logger log = LoggerFactory.getLogger(InventoryService.class);

    private HostsApi hostsInventoryApi;

    @Autowired
    public InventoryService(ApiClient inventoryApiClient) throws Exception {
        hostsInventoryApi = new HostsApi(inventoryApiClient);
    }

    // TODO Update the parameters once we know what will be coming from the RHSM service.
    public HostOut sendHostUpdate(String orgId, String displayName, String hostName, UUID rhsmUuid)
        throws RhsmConduitException {
        try {
            return hostsInventoryApi.apiHostAddHost(forgeAuthHeader(orgId),
                createHost(orgId, displayName, hostName, rhsmUuid));
        }
        catch (Exception e) {
            // FIXME This should all get removed once the exception mappers are in place.
            log.error("An exception occurred during request.", e);
            int code = 500;
            String message = e.getMessage();
            if (e instanceof ApiException) {
                ApiException apie = (ApiException) e;
                code = apie.getCode();
                message = apie.getResponseBody();
            }
            log.error("Could not update host. Reason: {}", message);
            throw new RhsmConduitException(code, message);
        }
    }

    private byte[] forgeAuthHeader(String accountNumber) {
        return "b".getBytes();
    }

    /**
     * TODO Remove once we are pulling data from the pinhead API.
     *
     * @return the new host.
     */
    private Host createHost(String orgId, String displayName, String fqdn, UUID submanUUID) {
        Map<String, String> rhsmFactMap = new HashMap<>();
        rhsmFactMap.put("orgId", orgId);
        rhsmFactMap.put("is_virt", Boolean.FALSE.toString());

        FactSet rhsmFacts = new FactSet()
            .namespace("rhsm")
            .facts(rhsmFactMap);
        List<FactSet> facts = new LinkedList<>();
        facts.add(rhsmFacts);

        Host host = new Host();
        // Magic account number that tells the inventory app to
        // ignore the auth header account check. When running
        // against production insights-inventory, the account in
        // the header must match what is set on the Host.
        //
        // In hosted the account will be the orgId.
        // TODO Properly set the account when we determine how
        //      auth will work going forward.
        host.setAccount("0000001");
        host.setFqdn(fqdn);
        host.setDisplayName(displayName);
        host.setSubscriptionManagerId(submanUUID);
        host.facts(facts);
        return host;
    }

}
