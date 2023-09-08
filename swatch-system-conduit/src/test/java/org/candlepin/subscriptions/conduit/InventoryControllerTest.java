/*
 * Copyright Red Hat, Inc.
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
package org.candlepin.subscriptions.conduit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyString;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.nullable;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.verifyNoInteractions;
import static org.mockito.BDDMockito.verifyNoMoreInteractions;
import static org.mockito.BDDMockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.conduit.inventory.ConduitFacts;
import org.candlepin.subscriptions.conduit.inventory.InventoryService;
import org.candlepin.subscriptions.conduit.inventory.InventoryServiceProperties;
import org.candlepin.subscriptions.conduit.job.DatabaseOrgList;
import org.candlepin.subscriptions.conduit.job.OrgSyncTaskManager;
import org.candlepin.subscriptions.conduit.json.inventory.HbiNetworkInterface;
import org.candlepin.subscriptions.conduit.rhsm.RhsmService;
import org.candlepin.subscriptions.conduit.rhsm.client.ApiException;
import org.candlepin.subscriptions.conduit.rhsm.client.RhsmApiProperties;
import org.candlepin.subscriptions.conduit.rhsm.client.model.Consumer;
import org.candlepin.subscriptions.conduit.rhsm.client.model.InstalledProducts;
import org.candlepin.subscriptions.conduit.rhsm.client.model.OrgInventory;
import org.candlepin.subscriptions.conduit.rhsm.client.model.Pagination;
import org.candlepin.subscriptions.exception.MissingAccountNumberException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"rhsm-conduit", "test", "kafka-queue"})
class InventoryControllerTest {
  @MockBean InventoryService inventoryService;

  @MockBean RhsmService rhsmService;

  @MockBean DatabaseOrgList orgList;

  @MockBean OrgSyncTaskManager taskManager;

  @Autowired InventoryController controller;

  @Autowired RhsmApiProperties rhsmApiProperties;

  @MockBean InventoryServiceProperties inventoryServiceProperties;

  @BeforeEach
  void setup() {
    when(inventoryServiceProperties.getHostLastSyncThreshold()).thenReturn(Duration.ofHours(24));
    when(rhsmService.formattedTime()).thenReturn("");
  }

  @Test
  void testHostAddedForEachConsumer() throws ApiException, MissingAccountNumberException {
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
    expectedFacts1.setRhProd(new ArrayList<>());
    expectedFacts1.setSysPurposeAddons(new ArrayList<>());

    Consumer consumer2 = new Consumer();
    consumer2.setOrgId("123");
    consumer2.setUuid(uuid2.toString());
    consumer2.setAccountNumber("account");

    ConduitFacts expectedFacts2 = new ConduitFacts();
    expectedFacts2.setOrgId("123");
    expectedFacts2.setAccountNumber("account");
    expectedFacts2.setSubscriptionManagerId(uuid2.toString());
    expectedFacts2.setRhProd(new ArrayList<>());
    expectedFacts2.setSysPurposeAddons(new ArrayList<>());

    when(rhsmService.getPageOfConsumers(eq("123"), nullable(String.class), anyString()))
        .thenReturn(pageOf(consumer1, consumer2));
    controller.updateInventoryForOrg("123");

    verify(inventoryService).scheduleHostUpdate(expectedFacts1);
    verify(inventoryService).scheduleHostUpdate(expectedFacts2);
    verify(inventoryService, times(1)).flushHostUpdates();
  }

  @Test
  void testHandlesEmptyList() throws ApiException, MissingAccountNumberException {
    Pagination pagination = new Pagination().count(0L).limit(1L);
    when(rhsmService.getPageOfConsumers(eq("org123"), nullable(String.class), anyString()))
        .thenReturn(new OrgInventory().body(Collections.emptyList()).pagination(pagination));

    controller.updateInventoryForOrg("org123");

    verifyNoInteractions(taskManager);
  }

  private OrgInventory pageOf(Consumer... consumers) {
    int limit = rhsmApiProperties.getRequestBatchSize();
    return new OrgInventory()
        .body(Arrays.stream(consumers).limit(limit).collect(Collectors.toList()))
        .pagination(
            new Pagination().count(Math.min((long) limit, consumers.length)).limit((long) limit));
  }

  @Test
  void testHostSkippedWhenExceptionHappens() throws MissingAccountNumberException, ApiException {
    UUID uuid = UUID.randomUUID();
    Consumer consumer1 = Mockito.mock(Consumer.class);
    Consumer consumer2 = new Consumer();
    consumer2.setUuid(uuid.toString());
    consumer2.setAccountNumber("account");
    consumer2.setOrgId("456");
    when(consumer1.getAccountNumber()).thenReturn("account");
    when(consumer1.getFacts()).thenThrow(new RuntimeException("foobar"));
    when(rhsmService.getPageOfConsumers(eq("123"), nullable(String.class), anyString()))
        .thenReturn(pageOf(consumer1, consumer2));
    controller.updateInventoryForOrg("123");

    ConduitFacts expected = new ConduitFacts();
    expected.setOrgId("456");
    expected.setAccountNumber("account");
    expected.setSubscriptionManagerId(uuid.toString());
    expected.setRhProd(new ArrayList<>());
    expected.setSysPurposeAddons(new ArrayList<>());

    verify(inventoryService).scheduleHostUpdate(expected);
    verify(inventoryService, times(1)).flushHostUpdates();
  }

  @Test
  void testSkipManifestConsumers() throws MissingAccountNumberException, ApiException {
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
    when(rhsmService.getPageOfConsumers(eq("123"), nullable(String.class), anyString()))
        .thenReturn(pageOf(consumer1, candlepinConsumer, satelliteConsumer, samConsumer));
    controller.updateInventoryForOrg("123");

    ConduitFacts expected = new ConduitFacts();
    expected.setOrgId("456");
    expected.setAccountNumber("account");
    expected.setSubscriptionManagerId(uuid1.toString());
    expected.setRhProd(new ArrayList<>());
    expected.setSysPurposeAddons(new ArrayList<>());
    verify(inventoryService).scheduleHostUpdate(expected);
    verify(inventoryService, times(1)).flushHostUpdates();
    verifyNoMoreInteractions(inventoryService);
  }

  @Test
  void testWhenTolerateMissingAccountNumberDisabled_ShortCircuitsOnMissingAccountNumbers()
      throws ApiException, MissingAccountNumberException {
    Consumer consumer1 = new Consumer();
    consumer1.setOrgId("123");
    consumer1.setUuid(UUID.randomUUID().toString());
    Consumer consumer2 = new Consumer();
    consumer1.setOrgId("123");
    consumer2.setUuid(UUID.randomUUID().toString());

    when(inventoryServiceProperties.isTolerateMissingAccountNumber()).thenReturn(false);
    when(rhsmService.getPageOfConsumers(eq("123"), nullable(String.class), anyString()))
        .thenReturn(pageOf(consumer1, consumer2));
    assertThrows(
        MissingAccountNumberException.class, () -> controller.updateInventoryForOrg("123"));
    verify(inventoryService, times(0)).scheduleHostUpdate(any(ConduitFacts.class));
  }

  @Test
  void testWhenTolerateMissingAccountNumberEnabled_DoNotThrowMissingAccountNumberException()
      throws ApiException {
    Consumer consumer1 = new Consumer();
    consumer1.setOrgId("123");
    consumer1.setUuid(UUID.randomUUID().toString());
    Consumer consumer2 = new Consumer();
    consumer1.setOrgId("123");
    consumer2.setUuid(UUID.randomUUID().toString());

    when(inventoryServiceProperties.isTolerateMissingAccountNumber()).thenReturn(true);
    when(rhsmService.getPageOfConsumers(eq("123"), nullable(String.class), anyString()))
        .thenReturn(pageOf(consumer1, consumer2));
    assertDoesNotThrow(() -> controller.updateInventoryForOrg("123"));
    verify(inventoryService, times(0)).scheduleHostUpdate(any(ConduitFacts.class));
  }

  @Test
  void testHandleConsumerWithNoAccountNumber() throws MissingAccountNumberException, ApiException {
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
    expected.setRhProd(new ArrayList<>());
    expected.setSysPurposeAddons(new ArrayList<>());

    when(rhsmService.getPageOfConsumers(eq("123"), nullable(String.class), anyString()))
        .thenReturn(pageOf(consumer1, consumer2));

    controller.updateInventoryForOrg("123");
    verify(inventoryService).scheduleHostUpdate(expected);
    verify(inventoryService, times(1)).flushHostUpdates();
  }

  @Test
  void testUnmodifiedFieldsTransferred() {
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
    consumer.getFacts().put("ocm.billing_model", "standard");

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);

    assertEquals(uuid, conduitFacts.getSubscriptionManagerId());
    assertEquals("test_org", conduitFacts.getOrgId());
    assertEquals("hypervisor1.test.com", conduitFacts.getVmHost());
    assertEquals(hypervisorUuid, conduitFacts.getVmHostUuid());
    assertEquals("guest", conduitFacts.getGuestId());
    assertEquals("host1.test.com", conduitFacts.getFqdn());
    assertEquals(systemUuid, conduitFacts.getBiosUuid());
    assertThat(
        conduitFacts.getIpAddresses(),
        Matchers.containsInAnyOrder("192.168.1.1", "10.0.0.1", "ff::ff:ff", "::1"));
    assertThat(
        conduitFacts.getMacAddresses(),
        Matchers.contains("00:00:00:00:00:00", "ff:ff:ff:ff:ff:ff"));
    assertEquals(new Integer(2), conduitFacts.getCpuSockets());
    assertEquals("x86_64", conduitFacts.getArchitecture());
    assertEquals(true, conduitFacts.getIsVirtual());
    assertEquals("Sockets", conduitFacts.getSysPurposeUnits());
    assertEquals("standard", conduitFacts.getBillingModel());
  }

  @Test
  void testCpuCoresIsCalculated() {
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
  void testMemoryIsNormalizedToGigabytes() {
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
  void testHandleOpenShiftStyleMemtotal() {
    String uuid = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);
    consumer.getFacts().put("memory.memtotal", "33489100800.00B");

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);

    assertEquals(new Long(31L), conduitFacts.getMemory());
    assertEquals(new Long(33489100800L), conduitFacts.getSystemMemoryBytes());
  }

  @Test
  void testInstalledProductsIsMappedToProductId() {
    String uuid = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);
    InstalledProducts product = new InstalledProducts();
    product.setProductId("72");
    consumer.getInstalledProducts().add(product);

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals(Arrays.asList("72"), conduitFacts.getRhProd());
  }

  @Test
  void testUnknownMacIsIgnored() {
    String uuid = UUID.randomUUID().toString();
    String systemUuid = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);
    consumer.getFacts().put("net.interface.virbr0.mac_address", "Unknown");

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals(uuid, conduitFacts.getSubscriptionManagerId());
    assertThat(conduitFacts.getMacAddresses(), Matchers.nullValue());
  }

  @Test
  void testNoneMacIsIgnored() {
    String uuid = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);
    consumer.getFacts().put("net.interface.virbr0.mac_address", "none");

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals(uuid, conduitFacts.getSubscriptionManagerId());
    assertThat(conduitFacts.getMacAddresses(), Matchers.nullValue());
  }

  @Test
  void testTruncatedMacAddressIsIgnored() {
    String truncatedMacFact = "52:54:00:4a:fe:cd,52:54:00:b5:f9:c0,52:54...";
    String uuid = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);
    consumer.getFacts().put("net.interface.virbr0.mac_address", truncatedMacFact);

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals(uuid, conduitFacts.getSubscriptionManagerId());
    assertEquals(2, conduitFacts.getMacAddresses().size());
    assertThat(
        conduitFacts.getMacAddresses(),
        Matchers.containsInAnyOrder("52:54:00:4a:fe:cd", "52:54:00:b5:f9:c0"));
  }

  @Test
  void testTruncatedIpV4AddressIsIgnoredForNics() {
    String factPrefix = "net.interface.virbr0.";

    String truncatedIpFact = "192.168.0.1,192.168.0.2,192.168.0.3,192...";
    String uuid = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);

    consumer.getFacts().put(factPrefix + "mac_address", "C0:FF:E0:00:00:D8");
    consumer.getFacts().put(factPrefix + "ipv4_address_list", truncatedIpFact);

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals(uuid, conduitFacts.getSubscriptionManagerId());
    var nic = conduitFacts.getNetworkInterfaces().get(0);
    assertThat(
        nic.getIpv4Addresses(),
        Matchers.containsInAnyOrder("192.168.0.1", "192.168.0.2", "192.168.0.3"));
    assertEquals(3, nic.getIpv4Addresses().size());
  }

  @Test
  void testFilterLoopbackIpV4Address() {
    String factPrefix = "net.interface.lo.";

    String loIpFact = "192.168.0.1,redacted, removed";
    String uuid = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);

    consumer.getFacts().put(factPrefix + "ipv4_address", loIpFact);

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals(1, conduitFacts.getNetworkInterfaces().get(0).getIpv4Addresses().size());
    assertEquals(
        List.of("192.168.0.1"), conduitFacts.getNetworkInterfaces().get(0).getIpv4Addresses());

    String invalidLoIpFact = "redacted, removed";
    consumer.getFacts().put(factPrefix + "ipv4_address", invalidLoIpFact);

    conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals(1, conduitFacts.getNetworkInterfaces().get(0).getIpv4Addresses().size());
    assertEquals(
        List.of("127.0.0.1"), conduitFacts.getNetworkInterfaces().get(0).getIpv4Addresses());
  }

  @Test
  void testFilterLoopbackIpV6Address() {
    String factPrefix = "net.interface.lo.";

    String loIpFact = "fe80::250:56ff:febe:f55a,redacted, removed";
    String uuid = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);

    consumer.getFacts().put(factPrefix + "ipv6_address", loIpFact);

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals(1, conduitFacts.getNetworkInterfaces().get(0).getIpv6Addresses().size());
    assertEquals(
        List.of("fe80::250:56ff:febe:f55a"),
        conduitFacts.getNetworkInterfaces().get(0).getIpv6Addresses());

    String invalidLoIpFact = "redacted, removed";
    consumer.getFacts().put(factPrefix + "ipv6_address", invalidLoIpFact);

    conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals(1, conduitFacts.getNetworkInterfaces().get(0).getIpv6Addresses().size());
    assertEquals(List.of("::1"), conduitFacts.getNetworkInterfaces().get(0).getIpv6Addresses());
  }

  @Test
  void testReleaseverInFacts_WhenReleaseVerPresent() {
    String uuid = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);
    consumer.setReleaseVer("8.0");

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals("8.0", conduitFacts.getReleaseVer());
  }

  @Test
  void testIsMarketplaceFacts_WhenAzureOfferPresent() {
    String azureOfferFact = "azure_offer";
    String azzureOffer = "RHEL";
    String uuid = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);

    consumer.getFacts().put(azureOfferFact, azzureOffer);

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals(true, conduitFacts.getIsMarketplace());

    azureOfferFact = "azure_offer";
    azzureOffer = " ";
    consumer.getFacts().put(azureOfferFact, azzureOffer);

    conduitFacts = controller.getFactsFromConsumer(consumer);
    assertNull(conduitFacts.getIsMarketplace());

    azureOfferFact = "azure_offer";
    azzureOffer = "rhel-byos";
    consumer.getFacts().put(azureOfferFact, azzureOffer);

    conduitFacts = controller.getFactsFromConsumer(consumer);
    assertNull(conduitFacts.getIsMarketplace());
  }

  @Test
  void testIsMarketplaceFacts_WhenAwsBillingProductsPresent() {
    String awsBillingProductsFact = "aws_billing_products";
    String awsBillingProducts = "bi-6fa54";
    String uuid = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);

    consumer.getFacts().put(awsBillingProductsFact, awsBillingProducts);

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals(true, conduitFacts.getIsMarketplace());

    awsBillingProductsFact = "aws_billing_products";
    awsBillingProducts = " ";
    consumer.getFacts().put(awsBillingProductsFact, awsBillingProducts);

    conduitFacts = controller.getFactsFromConsumer(consumer);
    assertNull(conduitFacts.getIsMarketplace());
  }

  @Test
  void testIsMarketplaceFacts_WhenAzureOfferOrAWSBillingProductsNotPresent() {
    String uuid = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertNull(conduitFacts.getIsMarketplace());
  }

  @Test
  void testIsMarketplaceFacts_WhenGcpLicenseCodeIsRhel() {
    String uuid = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);
    consumer
        .getFacts()
        .put(
            InventoryController.GCP_LICENSE_CODES,
            InventoryController.MARKETPLACE_GCP_LICENSE_CODES.stream().findFirst().orElseThrow());

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertTrue(conduitFacts.getIsMarketplace());
  }

  @Test
  void testIsMarketplaceFacts_WhenGcpLicenseCodeIsNonRhel() {
    String uuid = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);
    consumer.getFacts().put(InventoryController.GCP_LICENSE_CODES, "non-rhel-placeholder");

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertNull(conduitFacts.getIsMarketplace());
  }

  @Test
  void testTruncatedIpV6AddressIsIgnoredForNics() {
    String factPrefix = "net.interface.virbr0.";

    String truncatedIpFact = "fe80::2323:912a:177a:d8e6, 0088::99aa:bbcc:ddee:ff33, fd...";
    String truncatedIpFact2 = "::1,ab...";
    String uuid = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);

    consumer.getFacts().put(factPrefix + "mac_address", "C0:FF:E0:00:00:D8");
    consumer.getFacts().put(factPrefix + "ipv6_address.global_list", truncatedIpFact);
    consumer.getFacts().put(factPrefix + "ipv6_address.link_list", truncatedIpFact2);

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals(uuid, conduitFacts.getSubscriptionManagerId());
    var nic = conduitFacts.getNetworkInterfaces().get(0);
    assertThat(
        nic.getIpv6Addresses(),
        Matchers.containsInAnyOrder(
            "fe80::2323:912a:177a:d8e6", "0088::99aa:bbcc:ddee:ff33", "::1"));
    assertEquals(3, nic.getIpv6Addresses().size());
  }

  @Test
  void testBadMacIsIgnoredForNics() {
    String factPrefix = "net.interface.";

    String badMac = "0.0.0.0";
    String goodMac = "33:33:ff:81:0f:75";
    String ip = "192.168.0.1";
    String uuid = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);

    consumer.getFacts().put(factPrefix + "virbr0.mac_address", badMac);
    consumer.getFacts().put(factPrefix + "virbr0.ipv4_address.global_list", ip);
    consumer.getFacts().put(factPrefix + "virbr0.ipv4_address.link_list", ip);

    consumer.getFacts().put(factPrefix + "virbr0.mac_address", goodMac);
    consumer.getFacts().put(factPrefix + "virbr0.ipv4_address.global_list", ip);
    consumer.getFacts().put(factPrefix + "virbr0.ipv4_address.link_list", ip);

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals(uuid, conduitFacts.getSubscriptionManagerId());
    var nic = conduitFacts.getNetworkInterfaces().get(0);
    assertEquals(nic.getMacAddress(), goodMac);
  }

  @Test
  void testIpAddressesCollected() {
    Map<String, String> rhsmFacts = new HashMap<>();
    rhsmFacts.put("net.interface.eth0.ipv4_address_list", "192.168.1.1, 1.2.3.4");
    rhsmFacts.put("net.interface.eth0.ipv4_address", "192.168.1.1");
    rhsmFacts.put("net.interface.lo.ipv4_address", "127.0.0.1");
    rhsmFacts.put("net.interface.eth0.ipv6_address.link", "fe80::2323:912a:177a:d8e6");
    rhsmFacts.put("net.interface.eth0.ipv6_address.link_list", "0088::99aa:bbcc:ddee:ff33");

    var results = controller.extractIpAddresses(rhsmFacts);

    assertThat(
        results,
        Matchers.containsInAnyOrder(
            "192.168.1.1",
            "1.2.3.4",
            "127.0.0.1",
            "fe80::2323:912a:177a:d8e6",
            "0088::99aa:bbcc:ddee:ff33"));
    // testing whether the duplicates have been removed
    assertEquals(5, results.size());
  }

  @Test
  void testInsightsIdCollected() {
    String uuid = UUID.randomUUID().toString();
    String insightsId = UUID.randomUUID().toString();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);
    consumer.getFacts().put(InventoryController.INSIGHTS_ID, insightsId);

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals(insightsId, conduitFacts.getInsightsId());
  }

  @Test
  void testCanonicalFactsUuidIsNormalizedWithHyphens() {
    String insightsId = "40819041673b443b98765b0a1c2cc1b1\n";
    String uuid = "ca85ccb82d384317a5d14dc05a6ea1e9";
    String systemUuid = "961B0D0151A511CB97AF8FC366E1A390";
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid);
    consumer.getFacts().put(InventoryController.INSIGHTS_ID, insightsId);
    consumer.getFacts().put(InventoryController.DMI_SYSTEM_UUID, systemUuid);

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals("40819041-673b-443b-9876-5b0a1c2cc1b1", conduitFacts.getInsightsId());
    assertEquals("ca85ccb8-2d38-4317-a5d1-4dc05a6ea1e9", conduitFacts.getSubscriptionManagerId());
    assertEquals("961B0D01-51A5-11CB-97AF-8FC366E1A390", conduitFacts.getBiosUuid());
  }

  @Test
  void testUnknownIpsAreIgnored() {
    Map<String, String> rhsmFacts = new HashMap<>();
    rhsmFacts.put("net.interface.eth0.ipv4_address", "192.168.1.1");
    rhsmFacts.put("net.interface.lo.ipv4_address", "127.0.0.1");
    rhsmFacts.put("net.interface.eth0.ipv6_address.link", "fe80::2323:912a:177a:d8e6");
    rhsmFacts.put("net.interface.virbr0-nic.ipv4_address", "Unknown");
    rhsmFacts.put("net.interface.virbr0.ipv4_address", "192.168.122.1");
    rhsmFacts.put("net.interface.wlan0.ipv4_address", "Unknown");

    var results = controller.extractIpAddresses(rhsmFacts);

    assertThat(
        results,
        Matchers.containsInAnyOrder(
            "192.168.1.1", "127.0.0.1", "fe80::2323:912a:177a:d8e6", "192.168.122.1"));
  }

  @Test
  void testTruncatedIPsAreIgnored() {
    Map<String, String> rhsmFacts = new HashMap<>();
    rhsmFacts.put("net.interface.eth0.ipv4_address", "192.168.1.1");
    rhsmFacts.put(
        "net.interface.lo.ipv4_address", "127.0.0.1, 192.168.2.1,192.168.2.2,192...,redacted");

    var results = controller.extractIpAddresses(rhsmFacts);
    assertEquals(4, results.size());
    assertThat(
        results,
        Matchers.containsInAnyOrder("192.168.1.1", "127.0.0.1", "192.168.2.1", "192.168.2.2"));
  }

  @Test
  void testUnparseableBiosUuidsAreIgnored() throws ApiException, MissingAccountNumberException {
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
    when(rhsmService.getPageOfConsumers(eq("456"), nullable(String.class), anyString()))
        .thenReturn(pageOf(consumer1, consumer2));
    controller.updateInventoryForOrg("456");
    ConduitFacts cfacts1 = new ConduitFacts();
    cfacts1.setOrgId("456");
    cfacts1.setAccountNumber("account");
    cfacts1.setSubscriptionManagerId(uuid1.toString());
    cfacts1.setBiosUuid(bios1);
    cfacts1.setRhProd(new ArrayList<>());
    cfacts1.setSysPurposeAddons(new ArrayList<>());
    ConduitFacts cfacts2 = new ConduitFacts();
    cfacts2.setOrgId("456");
    cfacts2.setAccountNumber("account");
    cfacts2.setSubscriptionManagerId(uuid2.toString());
    cfacts2.setRhProd(new ArrayList<>());
    cfacts2.setSysPurposeAddons(new ArrayList<>());
    verify(inventoryService).scheduleHostUpdate(cfacts1);
    verify(inventoryService).scheduleHostUpdate(cfacts2);
    verify(inventoryService, times(1)).flushHostUpdates();
  }

  @Test
  void memtotalFromString() {
    assertEquals(new BigDecimal("12.06"), controller.memtotalFromString("12345.00B"));
    assertEquals(new BigDecimal("12.06"), controller.memtotalFromString("12345.00b"));
    assertEquals(new BigDecimal("12345.05"), controller.memtotalFromString("12345.05"));
    assertEquals(new BigDecimal("12.1"), controller.memtotalFromString("12345.5B"));
    assertEquals(BigDecimal.valueOf(12345), controller.memtotalFromString("12345"));

    assertThrows(NumberFormatException.class, () -> controller.memtotalFromString("123.00BM"));
    assertThrows(NumberFormatException.class, () -> controller.memtotalFromString("12B"));
  }

  @Test
  void handlesNoRegisteredSystemsWithoutException()
      throws MissingAccountNumberException, ApiException {
    when(rhsmService.getPageOfConsumers(eq("456"), nullable(String.class), anyString()))
        .thenReturn(pageOf());
    controller.updateInventoryForOrg("456");
    verify(inventoryService, never()).flushHostUpdates();
  }

  @Test
  void queuesNextPage() throws ApiException, MissingAccountNumberException {
    // Add one to test for off-by-one bugs
    int size = rhsmApiProperties.getRequestBatchSize() * 3 + 1;
    assertThat(size, Matchers.greaterThan(0));
    ArrayList<Consumer> bigCollection = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      Consumer consumer = new Consumer();
      consumer.setId("next-offset");
      consumer.setUuid(UUID.randomUUID().toString());
      consumer.setAccountNumber("account");
      consumer.setOrgId("123");
      bigCollection.add(consumer);
    }

    when(rhsmService.getPageOfConsumers(eq("123"), nullable(String.class), anyString()))
        .thenReturn(pageOf(bigCollection.toArray(new Consumer[] {})));

    controller.updateInventoryForOrg("123");
    verify(inventoryService, times(1)).flushHostUpdates();
    verify(taskManager, times(1)).updateOrgInventory("123", "next-offset");
  }

  @Test
  void doesNotFilterSystemsWithNoCheckin() throws ApiException, MissingAccountNumberException {
    Consumer consumer1 = new Consumer();
    consumer1.setOrgId("123");
    consumer1.setUuid(UUID.randomUUID().toString());
    consumer1.setAccountNumber("account");

    when(rhsmService.getPageOfConsumers(eq("123"), nullable(String.class), anyString()))
        .thenReturn(pageOf(consumer1));
    controller.updateInventoryForOrg("123");
    verify(inventoryService, times(1)).scheduleHostUpdate(any(ConduitFacts.class));
  }

  @Test
  void testServiceLevelIsAdded() throws ApiException, MissingAccountNumberException {
    UUID uuid = UUID.randomUUID();
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid.toString());
    consumer.setAccountNumber("account");
    consumer.setOrgId("456");

    consumer.setServiceLevel("Premium");

    when(rhsmService.getPageOfConsumers(eq("456"), nullable(String.class), anyString()))
        .thenReturn(pageOf(consumer));
    controller.updateInventoryForOrg("456");

    ConduitFacts cfacts = new ConduitFacts();
    cfacts.setOrgId("456");
    cfacts.setAccountNumber("account");
    cfacts.setSubscriptionManagerId(uuid.toString());
    cfacts.setSysPurposeSla("Premium");
    cfacts.setRhProd(new ArrayList<>());
    cfacts.setSysPurposeAddons(new ArrayList<>());
    verify(inventoryService).scheduleHostUpdate(cfacts);
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
    assertEquals("gcp", conduitFacts.getCloudProvider());

    ConduitFacts conduitFactsNegative = controller.getFactsFromConsumer(negative);
    assertNull(conduitFactsNegative.getCloudProvider());
  }

  @Test
  void testOpenShiftClusterIdUsedAsDisplayName()
      throws MissingAccountNumberException, ApiException {
    UUID uuid = UUID.randomUUID();
    Consumer consumer = new Consumer();
    consumer.setOrgId("123");
    consumer.setUuid(uuid.toString());
    consumer.getFacts().put("openshift.cluster_uuid", "JustAnotherCluster");
    consumer.setAccountNumber("account");

    ConduitFacts expected = new ConduitFacts();
    expected.setOrgId("123");
    expected.setAccountNumber("account");
    expected.setDisplayName("JustAnotherCluster");
    expected.setSubscriptionManagerId(uuid.toString());
    expected.setRhProd(new ArrayList<>());
    expected.setSysPurposeAddons(new ArrayList<>());

    when(rhsmService.getPageOfConsumers(eq("123"), nullable(String.class), anyString()))
        .thenReturn(pageOf(consumer));

    controller.updateInventoryForOrg("123");
    verify(inventoryService).scheduleHostUpdate(expected);
    verify(inventoryService, times(1)).flushHostUpdates();
  }

  @Test
  void testAdditionalFactsMapping() {
    Consumer consumer = new Consumer();
    // Operating System
    consumer.getFacts().put("distribution.name", "Red Hat Enterprise Linux Workstation");
    consumer.getFacts().put("distribution.version", "6.3");

    var virbr0NIC = new HbiNetworkInterface();
    virbr0NIC.setName("virbr0");
    virbr0NIC.setIpv4Addresses(List.of("192.168.122.1"));
    virbr0NIC.setMacAddress("C0:FF:E0:00:00:D8");

    var eth0NIC = new HbiNetworkInterface();
    eth0NIC.setName("eth0");
    eth0NIC.setIpv6Addresses(List.of("fe80::f2de:f1ff:fe9e:ccdd"));
    eth0NIC.setMacAddress("CA:FE:D1:9E:CC:DD");

    var loNIC = new HbiNetworkInterface();
    loNIC.setName("lo");
    loNIC.setIpv4Addresses(List.of("127.0.0.1"));
    loNIC.setMacAddress("00:00:00:00:00:00");

    // Consumer responsible for providing rhsm facts
    consumer.getFacts().put("net.interface.lo.ipv4_address", "127.0.0.1");
    consumer.getFacts().put("net.interface.virbr0.ipv4_address", "192.168.122.1");
    consumer
        .getFacts()
        .put("net.interface.virbr0.ipv4_address_list", "192.168.122.1, ipv4ListTest");
    consumer.getFacts().put("net.interface.virbr0.mac_address", "C0:FF:E0:00:00:D8");
    consumer.getFacts().put("net.interface.eth0.ipv6_address.link", "fe80::f2de:f1ff:fe9e:ccdd");
    consumer.getFacts().put("net.interface.eth0.ipv6_address.global", "ipv6Test");
    consumer.getFacts().put("net.interface.eth0.mac_address", "CA:FE:D1:9E:CC:DD");

    ConduitFacts conduitFacts = controller.getFactsFromConsumer(consumer);
    assertEquals("Red Hat Enterprise Linux Workstation", conduitFacts.getOsName());
    assertEquals("6.3", conduitFacts.getOsVersion());
    assertEquals(
        List.of(virbr0NIC, eth0NIC, loNIC).size(), conduitFacts.getNetworkInterfaces().size());
    assertThat(
        conduitFacts.getNetworkInterfaces(),
        Matchers.containsInAnyOrder(virbr0NIC, eth0NIC, loNIC));
  }

  @Test
  void testEmptyUuidNormalizedToNull() throws ApiException, MissingAccountNumberException {
    Consumer consumer = new Consumer();
    UUID uuid = UUID.randomUUID();
    consumer.setUuid(uuid.toString());
    consumer.setOrgId("org123");
    consumer.getFacts().put(InventoryController.INSIGHTS_ID, "");
    consumer.setAccountNumber("account123");

    ConduitFacts expected = new ConduitFacts();
    expected.setOrgId("org123");
    expected.setSubscriptionManagerId(uuid.toString());
    expected.setAccountNumber("account123");
    expected.setRhProd(new ArrayList<>());
    expected.setSysPurposeAddons(new ArrayList<>());

    when(rhsmService.getPageOfConsumers(eq("org123"), nullable(String.class), anyString()))
        .thenReturn(pageOf(consumer));

    controller.updateInventoryForOrg("org123");
    verify(inventoryService).scheduleHostUpdate(expected);
    verify(inventoryService, times(1)).flushHostUpdates();
  }

  @Test
  void testSystemMemoryBytesOverMaxValueSetsToNull()
      throws ApiException, MissingAccountNumberException {
    UUID uuid = UUID.randomUUID();
    BigDecimal memTotal =
        new BigDecimal((InventoryController.MAX_ALLOWED_SYSTEM_MEMORY_BYTES / 1020L) + 1000L);
    Consumer consumer = new Consumer();
    consumer.setUuid(uuid.toString());
    consumer.setAccountNumber("account");
    consumer.setOrgId("456");
    consumer.getFacts().put("memory.memtotal", memTotal.toString());

    when(rhsmService.getPageOfConsumers(eq("456"), nullable(String.class), anyString()))
        .thenReturn(pageOf(consumer));
    controller.updateInventoryForOrg("456");

    ConduitFacts cfacts = new ConduitFacts();
    cfacts.setOrgId("456");
    cfacts.setAccountNumber("account");
    cfacts.setSubscriptionManagerId(uuid.toString());
    cfacts.setMemory(8421505L);
    cfacts.setSystemMemoryBytes(null);
    cfacts.setRhProd(new ArrayList<>());
    cfacts.setSysPurposeAddons(new ArrayList<>());
    verify(inventoryService).scheduleHostUpdate(cfacts);
    verify(inventoryService, times(1)).flushHostUpdates();
  }
}
