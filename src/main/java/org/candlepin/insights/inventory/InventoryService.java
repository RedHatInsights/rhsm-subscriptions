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

import org.candlepin.insights.exception.ErrorCode;
import org.candlepin.insights.exception.ExternalServiceException;
import org.candlepin.insights.exception.RhsmConduitException;
import org.candlepin.insights.exception.inventory.InventoryServiceException;
import org.candlepin.insights.exception.inventory.InventoryServiceUnavailableException;
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

import javax.ws.rs.ProcessingException;


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
        // The resteasy client will throw a ProcessingException when it can not connect to
        // the target server.
        catch (ProcessingException pe) {
            throw new InventoryServiceUnavailableException(
                "An error occurred connecting to the inventory service", pe);
        }
        // Catch any errors that occur when a request is made via the API. eg, BadRequestException
        catch (ApiException apie) {
            throw new ExternalServiceException(ErrorCode.INVENTORY_SERVICE_REQUEST_ERROR,
                "An error occurred while sending a host update to the inventory service.", apie);
        }
        // A general catch all block so that any exception from the inventory API is rethrown
        // with a general InventoryServiceException. That any WebApplicationExceptions or other
        // RuntimeExceptions thrown by the client remain in the context of the InventoryService.
        catch (Exception e) {
            throw new InventoryServiceException(
                "An error occurred while sending a host update to the inventory service.", e);
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
