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
package org.candlepin.insights.inventory;

import org.candlepin.insights.api.model.ConsumerInventory;
import org.candlepin.insights.api.model.OrgInventory;
import org.candlepin.insights.exception.inventory.InventoryServiceException;
import org.candlepin.insights.inventory.client.model.BulkHostOut;
import org.candlepin.insights.inventory.client.model.CreateHostIn;
import org.candlepin.insights.inventory.client.model.FactSet;
import org.candlepin.insights.inventory.client.resources.HostsApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * A wrapper for the insights inventory client.
 *
 * If we get to the point where we are making multiple manipulations to the data stream as it flows through
 * this class consider
 * <code>
 * public interface ConduitVisitor {
 *     default FactSet visit(FactSet factSet) {
 *         return factSet;
 *     }
 *
 *     default CreateHostIn visit(CreateHostIn createHostIn) {
 *         return createHostIn;
 *     }
 *
 *     default BulkHostOut visit(BulkHostOut bulkHostOut) {
 *         return bulkHostOut;
 *     }
 * </code>
 *
 * The visit methods can then get called at the appropriate places in sendHostUpdate and createHost
 * allowing us to externalize manipulations to the implementation(s) of ConduitVisitor.
 */
public class DefaultInventoryService implements InventoryService {
    private static final Logger log = LoggerFactory.getLogger(DefaultInventoryService.class);

    private final HostsApi hostsInventoryApi;

    public DefaultInventoryService(HostsApi hostsInventoryApi) {
        this.hostsInventoryApi = hostsInventoryApi;
    }

    public void sendHostUpdate(List<ConduitFacts> facts) {

        // The same timestamp for the whole batch
        OffsetDateTime now = OffsetDateTime.now();
        List<CreateHostIn> hostsToSend = facts.stream()
            .map(x -> createHost(x, now))
            .collect(Collectors.toList());

        try {
            BulkHostOut hosts = hostsInventoryApi.apiHostAddHostList(hostsToSend);
            log.debug("Finished updating hosts: {}", hosts);
        }
        catch (Exception e) {
            throw new InventoryServiceException(
                "An error occurred while sending a host update to the inventory service.", e);
        }
    }

    /**
     * Given a set of facts, report them as a host to the inventory service.
     *
     * @return the new host.
     */
    protected CreateHostIn createHost(ConduitFacts conduitFacts, OffsetDateTime syncTimestamp) {
        Map<String, Object> rhsmFactMap = new HashMap<>();
        rhsmFactMap.put("orgId", conduitFacts.getOrgId());
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
        if (conduitFacts.getIsVirtual() != null) {
            rhsmFactMap.put("IS_VIRTUAL", conduitFacts.getIsVirtual());
        }
        if (conduitFacts.getVmHost() != null) {
            rhsmFactMap.put("VM_HOST", conduitFacts.getVmHost());
        }
        if (conduitFacts.getRhProd() != null) {
            rhsmFactMap.put("RH_PROD", conduitFacts.getRhProd());
        }

        rhsmFactMap.put("SYNC_TIMESTAMP", syncTimestamp);

        FactSet rhsmFacts = new FactSet()
            .namespace("rhsm")
            .facts(rhsmFactMap);
        List<FactSet> facts = new LinkedList<>();
        facts.add(rhsmFacts);

        CreateHostIn host = new CreateHostIn();
        host.setAccount(conduitFacts.getAccountNumber());
        host.setFqdn(conduitFacts.getFqdn());
        host.setSubscriptionManagerId(conduitFacts.getSubscriptionManagerId());
        host.setBiosUuid(conduitFacts.getBiosUuid());
        host.setIpAddresses(conduitFacts.getIpAddresses());
        host.setMacAddresses(conduitFacts.getMacAddresses());
        host.facts(facts);
        return host;
    }

    public OrgInventory getInventoryForOrgConsumers(List<ConduitFacts> conduitFactsForOrg) {
        List<ConsumerInventory> hosts = new ArrayList<>(conduitFactsForOrg);
        return new OrgInventory().consumerInventories(hosts);
    }
}
