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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.candlepin.insights.inventory.ConduitFacts;
import org.candlepin.insights.inventory.InventoryService;
import org.candlepin.insights.inventory.client.model.BulkHostOut;
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
        when(pinheadService.getOrganizationConsumers("123")).thenReturn(
            Arrays.asList(consumer1, consumer2));
        when(inventoryService.sendHostUpdate(anyString(), isNotNull())).thenReturn(new BulkHostOut());
        controller.updateInventoryForOrg("123");
        Mockito.verify(inventoryService, times(1)).sendHostUpdate(eq("123"), any());
    }

    @Test
    public void testUnmodifiedFieldsTransferred() {
        String uuid = UUID.randomUUID().toString();
        String systemUuid = UUID.randomUUID().toString();
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        consumer.setHypervisorName("hypervisor1.test.com");
        consumer.getFacts().put("network.fqdn", "host1.test.com");
        consumer.getFacts().put("dmi.system.uuid", systemUuid);
        consumer.getFacts().put("ip-addresses", "192.168.1.1, 10.0.0.1");
        consumer.getFacts().put("mac-addresses", "00:00:00:00:00:00, ff:ff:ff:ff:ff:ff");
        consumer.getFacts().put("cpu.cpu_socket(s)", "2");
        consumer.getFacts().put("uname.machine", "x86_64");
        consumer.getFacts().put("virt.is_guest", "True");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);

        assertEquals(uuid, conduitFacts.getSubscriptionManagerId().toString());
        assertEquals("hypervisor1.test.com", conduitFacts.getVmHost());
        assertEquals("host1.test.com", conduitFacts.getFqdn());
        assertEquals(systemUuid, conduitFacts.getBiosUuid().toString());
        assertEquals(Arrays.asList("192.168.1.1", "10.0.0.1"), conduitFacts.getIpAddresses());
        assertEquals(Arrays.asList("00:00:00:00:00:00", "ff:ff:ff:ff:ff:ff"), conduitFacts.getMacAddresses());
        assertEquals(new Integer(2), conduitFacts.getCpuSockets());
        assertEquals("x86_64", conduitFacts.getArchitecture());
        assertTrue(conduitFacts.getVirtual());
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
}
