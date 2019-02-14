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
package org.candlepin.insights.inventory;

import org.candlepin.insights.exception.RhsmConduitException;
import org.candlepin.insights.exception.inventory.InventoryServiceException;
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


/**
 * A wrapper for the insights inventory client.
 */
@Component
public class InventoryService {

    private static Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final HostsApi hostsInventoryApi;

    @Autowired
    public InventoryService(HostsApi hostsInventoryApi) {
        this.hostsInventoryApi = hostsInventoryApi;
    }

    // TODO Update the parameters once we know what will be coming from the RHSM service.
    public HostOut sendHostUpdate(String orgId, ConduitFacts facts)
        throws RhsmConduitException {
        try {
            return hostsInventoryApi.apiHostAddHost(forgeAuthHeader(orgId), createHost(orgId, facts));
        }
        catch (Exception e) {
            throw new InventoryServiceException(
                "An error occurred while sending a host update to the inventory service.", e);
        }
    }

    private byte[] forgeAuthHeader(String accountNumber) {
        return "b".getBytes();
    }

    /**
     * Given a set of facts, report them as a host to the inventory service.
     *
     * @return the new host.
     */
    private Host createHost(String orgId, ConduitFacts conduitFacts) {
        Map<String, Object> rhsmFactMap = new HashMap<>();
        rhsmFactMap.put("orgId", orgId);
        if (conduitFacts.getCpuSockets() != null) {
            rhsmFactMap.put("CPU_SOCKETS", conduitFacts.getCpuSockets());
        }
        if (conduitFacts.getCpuCores() != null) {
            rhsmFactMap.put("CPU_CORES", conduitFacts.getCpuCores());
        }
        if (conduitFacts.getMemory() != null) {
            rhsmFactMap.put("MEMORY", conduitFacts.getMemory());
        }
        if (conduitFacts.getArchitecture() != null) {
            rhsmFactMap.put("ARCHITECTURE", conduitFacts.getArchitecture());
        }
        if (conduitFacts.getVirtual() != null) {
            rhsmFactMap.put("IS_VIRTUAL", conduitFacts.getVirtual());
        }
        if (conduitFacts.getVmHost() != null) {
            rhsmFactMap.put("VM_HOST", conduitFacts.getVmHost());
        }
        if (conduitFacts.getRhProd() != null) {
            rhsmFactMap.put("RH_PROD", conduitFacts.getRhProd());
        }

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
        host.setFqdn(conduitFacts.getFqdn());
        host.setSubscriptionManagerId(conduitFacts.getSubscriptionManagerId());
        host.setBiosUuid(conduitFacts.getBiosUuid());
        host.setIpAddresses(conduitFacts.getIpAddresses());
        host.setMacAddresses(conduitFacts.getMacAddresses());
        host.facts(facts);
        return host;
    }

}
