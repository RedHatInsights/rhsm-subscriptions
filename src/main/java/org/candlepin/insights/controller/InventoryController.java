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
package org.candlepin.insights.controller;

import org.candlepin.insights.inventory.ConduitFacts;
import org.candlepin.insights.inventory.InventoryService;
import org.candlepin.insights.inventory.client.model.BulkHostOut;
import org.candlepin.insights.pinhead.PinheadService;
import org.candlepin.insights.pinhead.client.model.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller used to interact with the Inventory service.
 */
@Component
public class InventoryController {

    private static Logger log = LoggerFactory.getLogger(InventoryController.class);

    private static final int KIBIBYTES_PER_GIBIBYTE = 1048576;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PinheadService pinheadService;

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    public ConduitFacts getFactsFromConsumer(Consumer consumer) {
        final Map<String, String> pinheadFacts = consumer.getFacts();
        ConduitFacts facts = new ConduitFacts();

        facts.setSubscriptionManagerId(UUID.fromString(consumer.getUuid()));

        extractNetworkFacts(pinheadFacts, facts);
        extractHardwareFacts(pinheadFacts, facts);
        extractHypervisorFacts(consumer, pinheadFacts, facts);

        List<String> productIds = consumer.getInstalledProducts().stream()
            .map(installedProduct -> installedProduct.getProductId().toString()).collect(Collectors.toList());
        facts.setRhProd(productIds);

        return facts;
    }

    private void extractHardwareFacts(Map<String, String> pinheadFacts, ConduitFacts facts) {
        String systemUuid = pinheadFacts.get("dmi.system.uuid");
        if (!isEmpty(systemUuid)) {
            facts.setBiosUuid(UUID.fromString(systemUuid));
        }

        String cpuSockets = pinheadFacts.get("cpu.cpu_socket(s)");
        Integer numCpuSockets = null;
        if (!isEmpty(cpuSockets)) {
            numCpuSockets = Integer.parseInt(cpuSockets);
            facts.setCpuSockets(numCpuSockets);
        }

        String coresPerSocket = pinheadFacts.get("cpu.core(s)_per_socket");
        if (!isEmpty(coresPerSocket) && !isEmpty(cpuSockets)) {
            Integer numCoresPerSocket = Integer.parseInt(coresPerSocket);
            facts.setCpuCores(numCoresPerSocket * numCpuSockets);
        }

        String memoryTotal = pinheadFacts.get("memory.memtotal");
        if (!isEmpty(memoryTotal)) {
            int memoryBytes = Integer.parseInt(memoryTotal);
            // memtotal is a little less than accessible memory, round up to next GB
            int memoryGigabytes = (int) Math.ceil((float) memoryBytes / (float) KIBIBYTES_PER_GIBIBYTE);
            facts.setMemory(memoryGigabytes);
        }

        String architecture = pinheadFacts.get("uname.machine");
        if (!isEmpty(architecture)) {
            facts.setArchitecture(architecture);
        }
    }

    private void extractNetworkFacts(Map<String, String> pinheadFacts, ConduitFacts facts) {
        String ipAddresses = pinheadFacts.get("Ip-addresses");
        if (!isEmpty(ipAddresses)) {
            String[] ipAddressesSplit = ipAddresses.split(", ");
            facts.setIpAddresses(Arrays.asList(ipAddressesSplit));
        }

        String fqdn = pinheadFacts.get("network.fqdn");
        if (!isEmpty(fqdn)) {
            facts.setFqdn(fqdn);
        }

        String macAddresses = pinheadFacts.get("Mac-addresses");
        if (!isEmpty(macAddresses)) {
            String[] macAddressesSplit = macAddresses.split(", ");
            facts.setMacAddresses(Arrays.asList(macAddressesSplit));
        }
    }

    private void extractHypervisorFacts(Consumer consumer, Map<String, String> pinheadFacts,
        ConduitFacts facts) {

        String isGuest = pinheadFacts.get("virt.is_guest");
        if (!isEmpty(isGuest) && !isGuest.equals("Unknown")) {
            facts.setVirtual(isGuest.equals("True"));
        }

        String vmHost = consumer.getHypervisorName();
        if (!isEmpty(vmHost)) {
            facts.setVmHost(vmHost);
        }
    }

    public void updateInventoryForOrg(String orgId) {
        List<ConduitFacts> conduitFactsForOrg = new LinkedList<>();
        for (Consumer consumer : pinheadService.getOrganizationConsumers(orgId)) {
            conduitFactsForOrg.add(getFactsFromConsumer(consumer));
        }
        BulkHostOut result = inventoryService.sendHostUpdate(orgId, conduitFactsForOrg);
        log.info("Host inventory update completed for org: {}", orgId);
        log.debug("Results for org {}: {}", orgId, result);
    }
}
