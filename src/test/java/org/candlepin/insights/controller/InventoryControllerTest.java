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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.candlepin.insights.inventory.ConduitFacts;
import org.candlepin.insights.inventory.InventoryService;
import org.candlepin.insights.inventory.client.model.BulkHostOut;
import org.candlepin.insights.orgsync.OrgListStrategy;
import org.candlepin.insights.pinhead.PinheadService;
import org.candlepin.insights.pinhead.client.model.Consumer;
import org.candlepin.insights.pinhead.client.model.InstalledProducts;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Arrays;
import java.util.UUID;

@SpringBootTest
public class InventoryControllerTest {
    @MockBean
    InventoryService inventoryService;

    @MockBean
    PinheadService pinheadService;

    @MockBean
    OrgListStrategy orgListStrategy;

    @Autowired
    InventoryController controller;

    @Test
    public void testHostAddedForEachConsumer() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        Consumer consumer1 = new Consumer();
        consumer1.setUuid(uuid1.toString());
        Consumer consumer2 = new Consumer();
        consumer2.setUuid(uuid2.toString());
        when(orgListStrategy.getAccountNumberForOrg(isNotNull())).thenReturn("account");
        when(pinheadService.getOrganizationConsumers("123")).thenReturn(
            Arrays.asList(consumer1, consumer2));
        when(inventoryService.sendHostUpdate(isNotNull())).thenReturn(new BulkHostOut());
        controller.updateInventoryForOrg("123");
        Mockito.verify(inventoryService, times(1)).sendHostUpdate(any());
    }

    @Test
    public void testOrgWithoutAccountNumberThrowsError() {
        when(orgListStrategy.getAccountNumberForOrg(isNotNull())).thenReturn(null);
        assertThrows(NullPointerException.class, () -> controller.updateInventoryForOrg("123"));
    }

    @Test
    public void testUnmodifiedFieldsTransferred() {
        String uuid = UUID.randomUUID().toString();
        String systemUuid = UUID.randomUUID().toString();
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        consumer.setOrgId("test_org");
        consumer.setHypervisorName("hypervisor1.test.com");
        consumer.getFacts().put("network.fqdn", "host1.test.com");
        consumer.getFacts().put("dmi.system.uuid", systemUuid);
        consumer.getFacts().put("network.ipv4_address", "192.168.1.1, 10.0.0.1");
        consumer.getFacts().put("network.ipv6_address", "ff::ff:ff, ::1");
        consumer.getFacts().put("net.interface.eth0.mac_address", "00:00:00:00:00:00");
        consumer.getFacts().put("net.interface.virbr0.mac_address", "ff:ff:ff:ff:ff:ff");
        consumer.getFacts().put("cpu.cpu_socket(s)", "2");
        consumer.getFacts().put("uname.machine", "x86_64");
        consumer.getFacts().put("virt.is_guest", "True");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);

        assertEquals(uuid, conduitFacts.getSubscriptionManagerId());
        assertEquals("test_org", conduitFacts.getOrgId());
        assertEquals("hypervisor1.test.com", conduitFacts.getVmHost());
        assertEquals("host1.test.com", conduitFacts.getFqdn());
        assertEquals(systemUuid, conduitFacts.getBiosUuid());
        assertEquals(Arrays.asList("192.168.1.1", "10.0.0.1", "ff::ff:ff", "::1"),
            conduitFacts.getIpAddresses());
        assertEquals(Arrays.asList("00:00:00:00:00:00", "ff:ff:ff:ff:ff:ff"), conduitFacts.getMacAddresses());
        assertEquals(new Integer(2), conduitFacts.getCpuSockets());
        assertEquals("x86_64", conduitFacts.getArchitecture());
        assertTrue(conduitFacts.getIsVirtual());
    }

    @Test
    public void testCpuCoresIsCalculated() {
        String uuid = UUID.randomUUID().toString();
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        consumer.getFacts().put("cpu.cpu_socket(s)", "2");
        consumer.getFacts().put("cpu.core(s)_per_socket", "4");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);

        assertEquals(new Integer(8), conduitFacts.getCpuCores());
    }

    @Test
    public void testMemoryIsNormalizedToGigabytes() {
        String uuid = UUID.randomUUID().toString();
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        consumer.getFacts().put("memory.memtotal", "32757812");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);

        assertEquals(new Integer(32), conduitFacts.getMemory());
    }

    @Test
    public void testInstalledProductsIsMappedToProductId() {
        String uuid = UUID.randomUUID().toString();
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        InstalledProducts product = new InstalledProducts();
        product.setProductId(72L);
        consumer.getInstalledProducts().add(product);

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
        assertEquals(Arrays.asList("72"), conduitFacts.getRhProd());
    }

    @Test
    public void testUnknownMacIsIgnored() {
        String uuid = UUID.randomUUID().toString();
        String systemUuid = UUID.randomUUID().toString();
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        consumer.getFacts().put("net.interface.virbr0.mac_address", "Unknown");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
        assertEquals(uuid, conduitFacts.getSubscriptionManagerId());
        assertTrue(conduitFacts.getMacAddresses().isEmpty());
    }

    @Test
    public void testNoneMacIsIgnored() {
        String uuid = UUID.randomUUID().toString();
        String systemUuid = UUID.randomUUID().toString();
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        consumer.getFacts().put("net.interface.virbr0.mac_address", "none");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
        assertEquals(uuid, conduitFacts.getSubscriptionManagerId());
        assertTrue(conduitFacts.getMacAddresses().isEmpty());
    }
}
