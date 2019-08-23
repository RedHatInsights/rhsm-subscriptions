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

import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.candlepin.insights.inventory.ConduitFacts;
import org.candlepin.insights.inventory.InventoryService;
import org.candlepin.insights.orgsync.OrgListStrategy;
import org.candlepin.insights.pinhead.PinheadService;
import org.candlepin.insights.pinhead.client.model.Consumer;
import org.candlepin.insights.pinhead.client.model.InstalledProducts;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        consumer1.setAccountNumber("account");
        Consumer consumer2 = new Consumer();
        consumer2.setUuid(uuid2.toString());
        consumer2.setAccountNumber("account");
        when(pinheadService.getOrganizationConsumers("123")).thenReturn(
            Arrays.asList(consumer1, consumer2));
        controller.updateInventoryForOrg("123");
        Mockito.verify(inventoryService, times(1)).sendHostUpdate(any());
    }

    @Test
    void testHostSkippedWhenExceptionHappens() {
        UUID uuid = UUID.randomUUID();
        Consumer consumer1 = Mockito.mock(Consumer.class);
        Consumer consumer2 = new Consumer();
        consumer2.setUuid(uuid.toString());
        consumer2.setAccountNumber("account");
        consumer2.setOrgId("456");
        when(consumer1.getFacts()).thenThrow(new RuntimeException("foobar"));
        when(pinheadService.getOrganizationConsumers("123")).thenReturn(
            Arrays.asList(consumer1, consumer2));
        controller.updateInventoryForOrg("123");
        ConduitFacts expected = new ConduitFacts();
        expected.setOrgId("456");
        expected.setAccountNumber("account");
        expected.setSubscriptionManagerId(uuid.toString());
        verify(inventoryService).sendHostUpdate(Mockito.eq(Collections.singletonList(expected)));
    }

    @Test
    public void testHandleConsumerWithNoAccountNumber() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        Consumer consumer1 = new Consumer();
        consumer1.setUuid(uuid1.toString());
        consumer1.setAccountNumber("account");
        Consumer consumer2 = new Consumer();
        consumer2.setUuid(uuid2.toString());
        when(pinheadService.getOrganizationConsumers("123")).thenReturn(
            Arrays.asList(consumer1, consumer2));
        controller.updateInventoryForOrg("123");
        Mockito.verify(inventoryService, times(1)).sendHostUpdate(any());
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
        consumer.getFacts().put("net.interface.eth0.ipv4_address_list", "192.168.1.1, 10.0.0.1");
        consumer.getFacts().put("net.interface.eth0.ipv6_address.link_list", "ff::ff:ff, ::1");
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
        assertContainSameElements(Arrays.asList("192.168.1.1", "10.0.0.1", "ff::ff:ff", "::1"),
            conduitFacts.getIpAddresses());
        assertEquals(Arrays.asList("00:00:00:00:00:00", "ff:ff:ff:ff:ff:ff"), conduitFacts.getMacAddresses());
        assertEquals(new Integer(2), conduitFacts.getCpuSockets());
        assertEquals("x86_64", conduitFacts.getArchitecture());
        assertEquals(true, conduitFacts.getIsVirtual());
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
    void testBadMemoryFactIsIgnored() {
        String uuid = UUID.randomUUID().toString();
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        consumer.getFacts().put("memory.memtotal", "12345678.00B");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);

        assertNull(conduitFacts.getMemory());
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
        assertThat(conduitFacts.getMacAddresses(), Matchers.empty());
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
        assertThat(conduitFacts.getMacAddresses(), Matchers.empty());
    }

    @Test
    public void testIpAddressesCollected() {
        Map<String, String> pinheadFacts = new HashMap<String, String>();
        pinheadFacts.put("net.interface.eth0.ipv4_address_list", "192.168.1.1, 1.2.3.4");
        pinheadFacts.put("net.interface.eth0.ipv4_address", "192.168.1.1");
        pinheadFacts.put("net.interface.lo.ipv4_address", "127.0.0.1");
        pinheadFacts.put("net.interface.eth0.ipv6_address.link", "fe80::2323:912a:177a:d8e6");
        pinheadFacts.put("net.interface.eth0.ipv6_address.link_list", "0088::99aa:bbcc:ddee:ff33");

        ConduitFacts conduitFacts = new ConduitFacts();
        controller.extractIpAddresses(pinheadFacts, conduitFacts);

        assertContainSameElements(
            Arrays.asList("192.168.1.1", "1.2.3.4", "127.0.0.1", "fe80::2323:912a:177a:d8e6",
            "0088::99aa:bbcc:ddee:ff33"),
            conduitFacts.getIpAddresses());
        // testing whether the duplicates have been removed
        assertEquals(5, conduitFacts.getIpAddresses().size());
    }

    @Test
    public void testInsightsIdCollected() {
        String uuid = UUID.randomUUID().toString();
        String insightsId = UUID.randomUUID().toString();
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        consumer.getFacts().put(InventoryController.INSIGHTS_ID, insightsId);

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
        assertEquals(insightsId, conduitFacts.getInsightsId());
    }

    private void assertContainSameElements(List<String> list1, List<String> list2) {
        Collections.sort(list1);
        Collections.sort(list2);
        assertEquals(list1, list2);
    }

}
