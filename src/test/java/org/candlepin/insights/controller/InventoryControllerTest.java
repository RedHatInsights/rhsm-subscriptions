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
import org.candlepin.insights.inventory.client.InventoryServiceProperties;
import org.candlepin.insights.orgsync.db.DatabaseOrgList;
import org.candlepin.insights.pinhead.PinheadService;
import org.candlepin.insights.pinhead.client.PinheadApiProperties;
import org.candlepin.insights.pinhead.client.model.Consumer;
import org.candlepin.insights.pinhead.client.model.InstalledProducts;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
    DatabaseOrgList orgList;

    @Autowired
    InventoryController controller;

    @Autowired
    PinheadApiProperties pinheadApiProperties;

    @MockBean
    InventoryServiceProperties inventoryServiceProperties;

    @BeforeEach
    void setup() {
        when(inventoryServiceProperties.getHostLastSyncThreshold()).thenReturn(Duration.ofHours(24));
    }

    @Test
    public void testHostAddedForEachConsumer() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        Consumer consumer1 = new Consumer();
        consumer1.setOrgId("123");
        consumer1.setUuid(uuid1.toString());
        consumer1.setAccountNumber("account");

        ConduitFacts expectedFacts1 = new ConduitFacts();
        expectedFacts1.setOrgId("123");
        expectedFacts1.setAccountNumber("account");
        expectedFacts1.setSubscriptionManagerId(uuid1.toString());

        Consumer consumer2 = new Consumer();
        consumer2.setOrgId("123");
        consumer2.setUuid(uuid2.toString());
        consumer2.setAccountNumber("account");

        ConduitFacts expectedFacts2 = new ConduitFacts();
        expectedFacts2.setOrgId("123");
        expectedFacts2.setAccountNumber("account");
        expectedFacts2.setSubscriptionManagerId(uuid2.toString());

        when(pinheadService.getOrganizationConsumers("123")).thenReturn(
            Arrays.asList(consumer1, consumer2));
        controller.updateInventoryForOrg("123");

        verify(inventoryService).scheduleHostUpdate(Mockito.eq(expectedFacts1));
        verify(inventoryService).scheduleHostUpdate(Mockito.eq(expectedFacts2));
        verify(inventoryService, times(1)).flushHostUpdates();
    }

    @Test
    void testHostSkippedWhenExceptionHappens() {
        UUID uuid = UUID.randomUUID();
        Consumer consumer1 = Mockito.mock(Consumer.class);
        Consumer consumer2 = new Consumer();
        consumer2.setUuid(uuid.toString());
        consumer2.setAccountNumber("account");
        consumer2.setOrgId("456");
        when(consumer1.getAccountNumber()).thenReturn("account");
        when(consumer1.getFacts()).thenThrow(new RuntimeException("foobar"));
        when(pinheadService.getOrganizationConsumers("123")).thenReturn(
            Arrays.asList(consumer1, consumer2));
        controller.updateInventoryForOrg("123");

        ConduitFacts expected = new ConduitFacts();
        expected.setOrgId("456");
        expected.setAccountNumber("account");
        expected.setSubscriptionManagerId(uuid.toString());
        verify(inventoryService).scheduleHostUpdate(Mockito.eq(expected));
        verify(inventoryService, times(1)).flushHostUpdates();
    }

    @Test
    void testSkipManifestConsumers() {
        UUID uuid1 = UUID.randomUUID();
        Consumer consumer1 = new Consumer();
        Consumer candlepinConsumer = new Consumer();
        Consumer satelliteConsumer = new Consumer();
        Consumer samConsumer = new Consumer();
        consumer1.setUuid(uuid1.toString());
        consumer1.setAccountNumber("account");
        consumer1.setOrgId("456");
        consumer1.setType("system");
        candlepinConsumer.setUuid(UUID.randomUUID().toString());
        candlepinConsumer.setAccountNumber("account");
        candlepinConsumer.setOrgId("456");
        candlepinConsumer.setType("candlepin");
        satelliteConsumer.setUuid(UUID.randomUUID().toString());
        satelliteConsumer.setAccountNumber("account");
        satelliteConsumer.setOrgId("456");
        satelliteConsumer.setType("satellite");
        samConsumer.setUuid(UUID.randomUUID().toString());
        samConsumer.setAccountNumber("account");
        samConsumer.setOrgId("456");
        samConsumer.setType("sam");
        when(pinheadService.getOrganizationConsumers("123")).thenReturn(
            Arrays.asList(consumer1, candlepinConsumer, satelliteConsumer, samConsumer));
        controller.updateInventoryForOrg("123");

        ConduitFacts expected = new ConduitFacts();
        expected.setOrgId("456");
        expected.setAccountNumber("account");
        expected.setSubscriptionManagerId(uuid1.toString());
        verify(inventoryService).scheduleHostUpdate(Mockito.eq(expected));
        verify(inventoryService, times(1)).flushHostUpdates();
        verifyNoMoreInteractions(inventoryService);
    }

    @Test
    public void testShortCircuitsOnMissingAccountNumbers() {
        Consumer consumer1 = new Consumer();
        consumer1.setOrgId("123");
        consumer1.setUuid(UUID.randomUUID().toString());
        Consumer consumer2 = new Consumer();
        consumer1.setOrgId("123");
        consumer2.setUuid(UUID.randomUUID().toString());

        when(pinheadService.getOrganizationConsumers("123")).thenReturn(Arrays.asList(consumer1, consumer2));
        controller.updateInventoryForOrg("123");
        verify(inventoryService, times(0)).scheduleHostUpdate(any(ConduitFacts.class));
    }

    @Test
    public void testHandleConsumerWithNoAccountNumber() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        Consumer consumer1 = new Consumer();
        consumer1.setOrgId("123");
        consumer1.setUuid(uuid1.toString());
        consumer1.setAccountNumber("account");
        Consumer consumer2 = new Consumer();
        consumer2.setUuid(uuid2.toString());

        ConduitFacts expected = new ConduitFacts();
        expected.setOrgId("123");
        expected.setAccountNumber("account");
        expected.setSubscriptionManagerId(uuid1.toString());

        when(pinheadService.getOrganizationConsumers("123")).thenReturn(
            Arrays.asList(consumer1, consumer2));

        controller.updateInventoryForOrg("123");
        verify(inventoryService).scheduleHostUpdate(Mockito.eq(expected));
        verify(inventoryService, times(1)).flushHostUpdates();
    }

    @Test
    public void testUnmodifiedFieldsTransferred() {
        String uuid = UUID.randomUUID().toString();
        String systemUuid = UUID.randomUUID().toString();
        String hypervisorUuid = UUID.randomUUID().toString();
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        consumer.setOrgId("test_org");
        consumer.setHypervisorName("hypervisor1.test.com");
        consumer.setHypervisorUuid(hypervisorUuid);
        consumer.setGuestId("guest");
        consumer.getFacts().put("network.fqdn", "host1.test.com");
        consumer.getFacts().put("dmi.system.uuid", systemUuid);
        consumer.getFacts().put("net.interface.eth0.ipv4_address_list", "192.168.1.1, 10.0.0.1");
        consumer.getFacts().put("net.interface.eth0.ipv6_address.link_list", "ff::ff:ff, ::1");
        consumer.getFacts().put("net.interface.eth0.mac_address", "00:00:00:00:00:00");
        consumer.getFacts().put("net.interface.virbr0.mac_address", "ff:ff:ff:ff:ff:ff");
        consumer.getFacts().put("cpu.cpu_socket(s)", "2");
        consumer.getFacts().put("uname.machine", "x86_64");
        consumer.getFacts().put("virt.is_guest", "True");
        consumer.getFacts().put("ocm.units", "Sockets");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);

        assertEquals(uuid, conduitFacts.getSubscriptionManagerId());
        assertEquals("test_org", conduitFacts.getOrgId());
        assertEquals("hypervisor1.test.com", conduitFacts.getVmHost());
        assertEquals(hypervisorUuid, conduitFacts.getVmHostUuid());
        assertEquals("guest", conduitFacts.getGuestId());
        assertEquals("host1.test.com", conduitFacts.getFqdn());
        assertEquals(systemUuid, conduitFacts.getBiosUuid());
        assertThat(conduitFacts.getIpAddresses(), Matchers.containsInAnyOrder(
            "192.168.1.1",
            "10.0.0.1",
            "ff::ff:ff",
            "::1")
        );
        assertThat(conduitFacts.getMacAddresses(), Matchers.contains(
            "00:00:00:00:00:00",
            "ff:ff:ff:ff:ff:ff")
        );
        assertEquals(new Integer(2), conduitFacts.getCpuSockets());
        assertEquals("x86_64", conduitFacts.getArchitecture());
        assertEquals(true, conduitFacts.getIsVirtual());
        assertEquals("Sockets", conduitFacts.getSysPurposeUnits());
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
        assertEquals(new Integer(4), conduitFacts.getCoresPerSocket());
    }

    @Test
    public void testMemoryIsNormalizedToGigabytes() {
        String uuid = UUID.randomUUID().toString();
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        consumer.getFacts().put("memory.memtotal", "32757812");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);

        assertEquals(new Long(32), conduitFacts.getMemory());
        assertEquals(new Long(33543999488L), conduitFacts.getSystemMemoryBytes());
    }

    @Test
    void testBadMemoryFactIsIgnored() {
        String uuid = UUID.randomUUID().toString();
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        consumer.getFacts().put("memory.memtotal", "12345678DDD");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);

        assertNull(conduitFacts.getMemory());
    }

    @Test
    public void testHandleOpenShiftStyleMemtotal() {
        String uuid = UUID.randomUUID().toString();
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        consumer.getFacts().put("memory.memtotal", "33489100800.00B");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);

        assertEquals(new Long(31L), conduitFacts.getMemory());
        assertEquals(new Long(33489100800L), conduitFacts.getSystemMemoryBytes());
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
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        consumer.getFacts().put("net.interface.virbr0.mac_address", "none");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
        assertEquals(uuid, conduitFacts.getSubscriptionManagerId());
        assertThat(conduitFacts.getMacAddresses(), Matchers.empty());
    }

    @Test
    public void testTruncatedMacAddressIsIgnored() {
        String truncatedMacFact = "52:54:00:4a:fe:cd,52:54:00:b5:f9:c0,52:54...";
        String uuid = UUID.randomUUID().toString();
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        consumer.getFacts().put("net.interface.virbr0.mac_address", truncatedMacFact);

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
        assertEquals(uuid, conduitFacts.getSubscriptionManagerId());
        assertEquals(2, conduitFacts.getMacAddresses().size());
        assertThat(conduitFacts.getMacAddresses(), Matchers.containsInAnyOrder(
            "52:54:00:4a:fe:cd", "52:54:00:b5:f9:c0"
        ));
    }

    @Test
    public void testIpAddressesCollected() {
        Map<String, String> pinheadFacts = new HashMap<>();
        pinheadFacts.put("net.interface.eth0.ipv4_address_list", "192.168.1.1, 1.2.3.4");
        pinheadFacts.put("net.interface.eth0.ipv4_address", "192.168.1.1");
        pinheadFacts.put("net.interface.lo.ipv4_address", "127.0.0.1");
        pinheadFacts.put("net.interface.eth0.ipv6_address.link", "fe80::2323:912a:177a:d8e6");
        pinheadFacts.put("net.interface.eth0.ipv6_address.link_list", "0088::99aa:bbcc:ddee:ff33");

        ConduitFacts conduitFacts = new ConduitFacts();
        controller.extractIpAddresses(pinheadFacts, conduitFacts);

        assertThat(conduitFacts.getIpAddresses(), Matchers.containsInAnyOrder(
            "192.168.1.1",
            "1.2.3.4",
            "127.0.0.1",
            "fe80::2323:912a:177a:d8e6",
            "0088::99aa:bbcc:ddee:ff33")
        );
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

    @Test
    void testInsightsIdIsNormalized() {
        String uuid = UUID.randomUUID().toString();
        String insightsId = "40819041673b443b98765b0a1c2cc1b1\n";
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        consumer.getFacts().put(InventoryController.INSIGHTS_ID, insightsId);

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
        assertEquals("40819041673b443b98765b0a1c2cc1b1", conduitFacts.getInsightsId());
    }

    @Test
    void testInsightsIdIsNormalizedWithHyphens() {
        String uuid = UUID.randomUUID().toString();
        String insightsId = "40819041673b443b98765b0a1c2cc1b1\n";
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        consumer.getFacts().put(InventoryController.INSIGHTS_ID, insightsId);

        when(inventoryServiceProperties.isAddUuidHyphens()).thenReturn(true);

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
        assertEquals("40819041-673b-443b-9876-5b0a1c2cc1b1", conduitFacts.getInsightsId());
    }

    @Test
    public void testUnknownIpsAreIgnored() {
        Map<String, String> pinheadFacts = new HashMap<>();
        pinheadFacts.put("net.interface.eth0.ipv4_address", "192.168.1.1");
        pinheadFacts.put("net.interface.lo.ipv4_address", "127.0.0.1");
        pinheadFacts.put("net.interface.eth0.ipv6_address.link", "fe80::2323:912a:177a:d8e6");
        pinheadFacts.put("net.interface.virbr0-nic.ipv4_address", "Unknown");
        pinheadFacts.put("net.interface.virbr0.ipv4_address", "192.168.122.1");
        pinheadFacts.put("net.interface.wlan0.ipv4_address", "Unknown");

        ConduitFacts conduitFacts = new ConduitFacts();
        controller.extractIpAddresses(pinheadFacts, conduitFacts);

        assertThat(conduitFacts.getIpAddresses(), Matchers.containsInAnyOrder(
            "192.168.1.1",
            "127.0.0.1",
            "fe80::2323:912a:177a:d8e6",
            "192.168.122.1")
        );
    }

    @Test
    public void testTruncatedIPsAreIgnored() {
        Map<String, String> pinheadFacts = new HashMap<>();
        pinheadFacts.put("net.interface.eth0.ipv4_address", "192.168.1.1");
        pinheadFacts.put("net.interface.lo.ipv4_address", "127.0.0.1, 192.168.2.1,192.168.2.2,192...");

        ConduitFacts conduitFacts = new ConduitFacts();
        controller.extractIpAddresses(pinheadFacts, conduitFacts);
        assertEquals(4, conduitFacts.getIpAddresses().size());
        assertThat(conduitFacts.getIpAddresses(), Matchers.containsInAnyOrder(
            "192.168.1.1",
            "127.0.0.1",
            "192.168.2.1",
            "192.168.2.2")
        );
    }

    @Test
    public void testUnparseableBiosUuidsAreIgnored() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        Consumer consumer1 = new Consumer();
        Consumer consumer2 = new Consumer();
        consumer1.setUuid(uuid1.toString());
        consumer1.setAccountNumber("account");
        consumer1.setOrgId("456");
        // consumer1 has a valid BIOS UUID
        String bios1 = UUID.randomUUID().toString();
        consumer1.getFacts().put("dmi.system.uuid", bios1);
        consumer2.setUuid(uuid2.toString());
        consumer2.setAccountNumber("account");
        consumer2.setOrgId("456");
        // consumer2 has not
        consumer2.getFacts().put("dmi.system.uuid", "Not present");
        when(pinheadService.getOrganizationConsumers("456")).thenReturn(
            Arrays.asList(consumer1, consumer2));
        controller.updateInventoryForOrg("456");
        ConduitFacts cfacts1 = new ConduitFacts();
        cfacts1.setOrgId("456");
        cfacts1.setAccountNumber("account");
        cfacts1.setSubscriptionManagerId(uuid1.toString());
        cfacts1.setBiosUuid(bios1);
        ConduitFacts cfacts2 = new ConduitFacts();
        cfacts2.setOrgId("456");
        cfacts2.setAccountNumber("account");
        cfacts2.setSubscriptionManagerId(uuid2.toString());
        verify(inventoryService).scheduleHostUpdate(Mockito.eq(cfacts1));
        verify(inventoryService).scheduleHostUpdate(Mockito.eq(cfacts2));
        verify(inventoryService, times(1)).flushHostUpdates();
    }

    @Test
    public void memtotalFromString() {
        assertEquals(new BigDecimal("12.06"), controller.memtotalFromString("12345.00B"));
        assertEquals(new BigDecimal("12.06"), controller.memtotalFromString("12345.00b"));
        assertEquals(new BigDecimal("12345.05"), controller.memtotalFromString("12345.05"));
        assertEquals(new BigDecimal("12.1"), controller.memtotalFromString("12345.5B"));
        assertEquals(BigDecimal.valueOf(12345), controller.memtotalFromString("12345"));

        assertThrows(NumberFormatException.class, () -> controller.memtotalFromString("123.00BM"));
        assertThrows(NumberFormatException.class, () -> controller.memtotalFromString("12B"));
    }

    @Test
    void handlesNoRegisteredSystemsWithoutException() {
        when(pinheadService.getOrganizationConsumers("456")).thenReturn(Collections.emptyList());
        controller.updateInventoryForOrg("456");
        verify(inventoryService, never()).flushHostUpdates();
    }

    @Test
    void flushesUpdatesInBatches() {
        // Add one to test for off-by-one bugs
        int size = pinheadApiProperties.getRequestBatchSize() * 3 + 1;
        assertThat(size, Matchers.greaterThan(0));
        List<Consumer> bigCollection = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Consumer consumer = new Consumer();
            consumer.setUuid(UUID.randomUUID().toString());
            consumer.setAccountNumber("account");
            consumer.setOrgId("123");
            bigCollection.add(consumer);
        }

        when(pinheadService.getOrganizationConsumers("123")).thenReturn(bigCollection);

        controller.updateInventoryForOrg("123");
        int expectedBatches = (int) Math.ceil((double) size / pinheadApiProperties.getRequestBatchSize());
        verify(inventoryService, times(expectedBatches)).flushHostUpdates();
    }

    @Test
    void doesNotFilterSystemsWithNoCheckin() {
        Consumer consumer1 = new Consumer();
        consumer1.setOrgId("123");
        consumer1.setUuid(UUID.randomUUID().toString());
        consumer1.setAccountNumber("account");

        when(pinheadService.getOrganizationConsumers("123")).thenReturn(
            Arrays.asList(consumer1));
        controller.updateInventoryForOrg("123");
        verify(inventoryService, times(1)).scheduleHostUpdate(any(ConduitFacts.class));
    }

    @Test
    void filtersInactiveSystems() {
        Consumer consumer1 = new Consumer();
        consumer1.setOrgId("123");
        consumer1.setUuid(UUID.randomUUID().toString());
        consumer1.setAccountNumber("account");
        consumer1.setLastCheckin(OffsetDateTime.now());

        Consumer consumer2 = new Consumer();
        consumer2.setOrgId("123");
        consumer2.setUuid(UUID.randomUUID().toString());
        consumer2.setAccountNumber("account");
        consumer2.setLastCheckin(OffsetDateTime.now().minus(5, ChronoUnit.YEARS));

        when(pinheadService.getOrganizationConsumers("123")).thenReturn(
            Arrays.asList(consumer1, consumer2));
        controller.updateInventoryForOrg("123");
        verify(inventoryService, times(1)).scheduleHostUpdate(any(ConduitFacts.class));
    }

    @Test
    public void testServiceLevelIsAdded() {
        UUID uuid = UUID.randomUUID();
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid.toString());
        consumer.setAccountNumber("account");
        consumer.setOrgId("456");

        consumer.setServiceLevel("Premium");

        when(pinheadService.getOrganizationConsumers("456")).thenReturn(Collections.singletonList(consumer));
        controller.updateInventoryForOrg("456");

        ConduitFacts cfacts = new ConduitFacts();
        cfacts.setOrgId("456");
        cfacts.setAccountNumber("account");
        cfacts.setSubscriptionManagerId(uuid.toString());
        cfacts.setSysPurposeSla("Premium");
        verify(inventoryService).scheduleHostUpdate(Mockito.eq(cfacts));
        verify(inventoryService, times(1)).flushHostUpdates();
    }

    @Test
    void testExtractsBiosIdentifiers() {
        Consumer consumer = new Consumer();
        consumer.getFacts().put("dmi.bios.version", "1.0.0");
        consumer.getFacts().put("dmi.bios.vendor", "foobar");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
        assertEquals("1.0.0", conduitFacts.getBiosVersion());
        assertEquals("foobar", conduitFacts.getBiosVendor());
    }

    @Test
    void testCloudProviderEmptyIfNotDetected() {
        Consumer consumer = new Consumer();

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
        assertNull(conduitFacts.getCloudProvider());
    }

    @Test
    void testDetectsAlibabaHost() {
        Consumer consumer = new Consumer();
        Consumer negative = new Consumer();
        consumer.getFacts().put("dmi.system.manufacturer", "Alibaba Cloud");
        negative.getFacts().put("dmi.system.manufacturer", "foobar");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
        assertEquals("alibaba", conduitFacts.getCloudProvider());

        ConduitFacts conduitFactsNegative = controller.getFactsFromConsumer(negative);
        assertNull(conduitFactsNegative.getCloudProvider());
    }

    @Test
    void testDetectsAwsHost() {
        Consumer consumer = new Consumer();
        Consumer negative = new Consumer();
        consumer.getFacts().put("dmi.bios.version", "4.2.amazon");
        negative.getFacts().put("dmi.bios.version", "4.2");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
        assertEquals("aws", conduitFacts.getCloudProvider());

        ConduitFacts conduitFactsNegative = controller.getFactsFromConsumer(negative);
        assertNull(conduitFactsNegative.getCloudProvider());
    }

    @Test
    void testDetectsAzureHost() {
        Consumer consumer = new Consumer();
        Consumer negative = new Consumer();
        consumer.getFacts().put("dmi.chassis.asset_tag", "7783-7084-3265-9085-8269-3286-77");
        negative.getFacts().put("dmi.chassis.asset_tag", "foobar");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
        assertEquals("azure", conduitFacts.getCloudProvider());

        ConduitFacts conduitFactsNegative = controller.getFactsFromConsumer(negative);
        assertNull(conduitFactsNegative.getCloudProvider());
    }

    @Test
    void testDetectsGoogleHost() {
        Consumer consumer = new Consumer();
        Consumer negative = new Consumer();
        consumer.getFacts().put("dmi.bios.vendor", "Google");
        negative.getFacts().put("dmi.bios.vendor", "foobar");

        ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
        assertEquals("google", conduitFacts.getCloudProvider());

        ConduitFacts conduitFactsNegative = controller.getFactsFromConsumer(negative);
        assertNull(conduitFactsNegative.getCloudProvider());
    }
}
