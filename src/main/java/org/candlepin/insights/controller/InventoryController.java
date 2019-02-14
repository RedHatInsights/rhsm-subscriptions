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
package org.candlepin.insights.controller;

import org.candlepin.insights.inventory.ConduitFacts;
import org.candlepin.insights.inventory.InventoryService;
import org.candlepin.insights.pinhead.PinheadService;
import org.candlepin.insights.pinhead.client.model.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller used to interact with the Inventory service.
 */
@Component
public class InventoryController {

    private static final int KIBIBYTES_PER_GIBIBYTE = 1048576;
    private static Logger log = LoggerFactory.getLogger(InventoryController.class);

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PinheadService pinheadService;

    public ConduitFacts getFactsFromConsumer(Consumer consumer) {
        ConduitFacts facts = new ConduitFacts();

        facts.setSubscriptionManagerId(UUID.fromString(consumer.getUuid()));

        String systemUuid = consumer.getFacts().get("dmi.system.uuid");
        if (systemUuid != null) {
            facts.setBiosUuid(UUID.fromString(systemUuid));
        }

        String ipAddresses = consumer.getFacts().get("ip-addresses");
        if (ipAddresses != null) {
            String[] ipAddressesSplit = ipAddresses.split(", ");
            facts.setIpAddresses(Arrays.asList(ipAddressesSplit));
        }

        facts.setFqdn(consumer.getFacts().get("network.fqdn"));

        String macAddresses = consumer.getFacts().get("mac-addresses");
        if (macAddresses != null) {
            String[] macAddressesSplit = macAddresses.split(", ");
            facts.setMacAddresses(Arrays.asList(macAddressesSplit));
        }

        String cpuSockets = consumer.getFacts().get("cpu.cpu_socket(s)");
        Integer numCpuSockets = null;
        if (cpuSockets != null) {
            numCpuSockets = Integer.parseInt(cpuSockets);
            facts.setCpuSockets(numCpuSockets);
        }

        String coresPerSocket = consumer.getFacts().get("cpu.core(s)_per_socket");
        if (coresPerSocket != null && cpuSockets != null) {
            Integer numCoresPerSocket = Integer.parseInt(coresPerSocket);
            facts.setCpuCores(numCoresPerSocket * numCpuSockets);
        }

        String memoryTotal = consumer.getFacts().get("memory.memtotal");
        if (memoryTotal != null) {
            int memoryBytes = Integer.parseInt(memoryTotal);
            // memtotal is a little less than accessible memory, round up to next GB
            int memoryGigabytes = (int) Math.ceil((float) memoryBytes / (float) KIBIBYTES_PER_GIBIBYTE);
            facts.setMemory(memoryGigabytes);
        }

        facts.setArchitecture(consumer.getFacts().get("uname.machine"));

        String isGuest = consumer.getFacts().get("virt.is_guest");
        if (isGuest != null && !isGuest.equals("Unknown")) {
            facts.setVirtual(isGuest.equals("True"));
        }

        facts.setVmHost(consumer.getHypervisorName());

        List<String> productIds = consumer.getInstalledProducts().stream()
            .map(installedProduct -> installedProduct.getProductId().toString()).collect(Collectors.toList());
        facts.setRhProd(productIds);

        return facts;
    }

    public void updateInventoryForOrg(String orgId) {
        for (Consumer consumer : pinheadService.getOrganizationConsumers(orgId)) {
            ConduitFacts facts = getFactsFromConsumer(consumer);
            inventoryService.sendHostUpdate(orgId, facts);
        }
    }
}
