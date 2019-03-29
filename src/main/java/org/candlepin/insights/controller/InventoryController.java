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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;

/**
 * Controller used to interact with the Inventory service.
 */
@Component
public class InventoryController {
    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private static final int KIBIBYTES_PER_GIBIBYTE = 1048576;

    public static final String DMI_SYSTEM_UUID = "dmi.system.uuid";
    public static final String IP_ADDRESSES = "Ip-addresses";
    public static final String NETWORK_FQDN = "network.fqdn";
    public static final String MAC_ADDRESSES = "Mac-addresses";
    public static final String CPU_SOCKETS = "cpu.cpu_socket(s)";
    public static final String CPU_CORES_PER_SOCKET = "cpu.core(s)_per_socket";
    public static final String MEMORY_MEMTOTAL = "memory.memtotal";
    public static final String UNAME_MACHINE = "uname.machine";
    public static final String VIRT_IS_GUEST = "virt.is_guest";

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PinheadService pinheadService;

    @Autowired
    private Validator validator;

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
        String systemUuid = pinheadFacts.get(DMI_SYSTEM_UUID);
        if (!isEmpty(systemUuid)) {
            facts.setBiosUuid(UUID.fromString(systemUuid));
        }

        String cpuSockets = pinheadFacts.get(CPU_SOCKETS);
        String coresPerSocket = pinheadFacts.get(CPU_CORES_PER_SOCKET);
        if (!isEmpty(cpuSockets)) {
            Integer numCpuSockets = Integer.parseInt(cpuSockets);
            facts.setCpuSockets(numCpuSockets);
            if (!isEmpty(coresPerSocket)) {
                Integer numCoresPerSocket = Integer.parseInt(coresPerSocket);
                facts.setCpuCores(numCoresPerSocket * numCpuSockets);
            }
        }

        String memoryTotal = pinheadFacts.get(MEMORY_MEMTOTAL);
        if (!isEmpty(memoryTotal)) {
            int memoryBytes = Integer.parseInt(memoryTotal);
            // memtotal is a little less than accessible memory, round up to next GB
            int memoryGigabytes = (int) Math.ceil((float) memoryBytes / (float) KIBIBYTES_PER_GIBIBYTE);
            facts.setMemory(memoryGigabytes);
        }

        String architecture = pinheadFacts.get(UNAME_MACHINE);
        if (!isEmpty(architecture)) {
            facts.setArchitecture(architecture);
        }
    }

    private void extractNetworkFacts(Map<String, String> pinheadFacts, ConduitFacts facts) {
        String ipAddresses = pinheadFacts.get(IP_ADDRESSES);
        if (!isEmpty(ipAddresses)) {
            String[] ipAddressesSplit = ipAddresses.split(",\\s*");
            facts.setIpAddresses(Arrays.asList(ipAddressesSplit));
        }

        String fqdn = pinheadFacts.get(NETWORK_FQDN);
        if (!isEmpty(fqdn)) {
            facts.setFqdn(fqdn);
        }

        String macAddresses = pinheadFacts.get(MAC_ADDRESSES);
        if (!isEmpty(macAddresses)) {
            String[] macAddressesSplit = macAddresses.split(",\\s*");
            facts.setMacAddresses(Arrays.asList(macAddressesSplit));
        }
    }

    private void extractHypervisorFacts(Consumer consumer, Map<String, String> pinheadFacts,
        ConduitFacts facts) {

        String isGuest = pinheadFacts.get(VIRT_IS_GUEST);
        if (!isEmpty(isGuest) && !isGuest.equalsIgnoreCase("Unknown")) {
            facts.setVirtual(isGuest.equalsIgnoreCase("True"));
        }

        String vmHost = consumer.getHypervisorName();
        if (!isEmpty(vmHost)) {
            facts.setVmHost(vmHost);
        }
    }

    public void updateInventoryForOrg(String orgId) {
        List<ConduitFacts> conduitFactsForOrg = new LinkedList<>();
        for (Consumer consumer : pinheadService.getOrganizationConsumers(orgId)) {
            ConduitFacts facts = getFactsFromConsumer(consumer);

            Set<ConstraintViolation<ConduitFacts>> violations = validator.validate(facts);
            if (violations.isEmpty()) {
                conduitFactsForOrg.add(getFactsFromConsumer(consumer));
            }
            else if (log.isInfoEnabled()) {
                log.info("Consumer {} failed validation: {}", consumer.getName(),
                    violations.stream().map(x -> String.format("%s: %s", x.getPropertyPath(), x.getMessage()))
                    .collect(Collectors.joining("; "))
                );
            }

        }
        BulkHostOut result = inventoryService.sendHostUpdate(orgId, conduitFactsForOrg);
        log.info("Host inventory update completed for org: {}", orgId);
        log.debug("Results for org {}: {}", orgId, result);
    }
}
