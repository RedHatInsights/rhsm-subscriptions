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
package org.candlepin.subscriptions.conduit.inventory;

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.candlepin.subscriptions.inventory.client.ApiException;
import org.candlepin.subscriptions.inventory.client.InventoryServiceProperties;
import org.candlepin.subscriptions.inventory.client.model.CreateHostIn;
import org.candlepin.subscriptions.inventory.client.model.FactSet;
import org.candlepin.subscriptions.inventory.client.model.SystemProfile;
import org.candlepin.subscriptions.inventory.client.resources.HostsApi;
import org.candlepin.subscriptions.utilization.api.model.OrgInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultInventoryServiceTest {
  @Mock HostsApi api;

  private ConduitFacts createFullyPopulatedConduitFacts() {
    ConduitFacts conduitFacts = new ConduitFacts();
    conduitFacts.setAccountNumber("1234-account");
    conduitFacts.setArchitecture("x86_64");
    conduitFacts.setBiosVendor("bios_vendor");
    conduitFacts.setBiosVersion("bios_version");
    conduitFacts.setBiosUuid("9d9f7927-1f42-4827-bbb8-1791b2b0a1b4");
    conduitFacts.setCloudProvider("cloud");
    conduitFacts.setCpuCores(8);
    conduitFacts.setCpuSockets(4);
    conduitFacts.setCoresPerSocket(2);
    conduitFacts.setFqdn("test.example.com");
    conduitFacts.setIpAddresses(Collections.singletonList("127.0.0.1"));
    conduitFacts.setMacAddresses(Collections.singletonList("de:ad:be:ef:fe:ed"));
    conduitFacts.setMemory(32757752L);
    conduitFacts.setSystemMemoryBytes(1024L);
    conduitFacts.setOrgId("1234-org");
    conduitFacts.setRhProd(Collections.singletonList("72"));
    conduitFacts.setSubscriptionManagerId("108152b1-6b41-4e1b-b908-922c943e7950");
    conduitFacts.setInsightsId("0be977bc-46e9-4d9b-a798-65cd1ed98710");
    conduitFacts.setIsVirtual(true);
    conduitFacts.setVmHost("vm_host");
    conduitFacts.setGuestId("i_am_a_guest");
    conduitFacts.setVmHostUuid("14f64266-f957-4765-8420-c3b6b3002bb7");
    conduitFacts.setSysPurposeRole("test_role");
    conduitFacts.setSysPurposeSla("Premium");
    conduitFacts.setSysPurposeUsage("test_usage");
    conduitFacts.setSysPurposeAddons(Arrays.asList("addon1", "addon2"));
    conduitFacts.setSysPurposeUnits("Sockets");
    conduitFacts.setBillingModel("standard");
    return conduitFacts;
  }

  @Test
  void testSendHostUpdatePopulatesAllFieldsWithFullConduitFactsRecord() throws ApiException {
    InventoryServiceProperties props = new InventoryServiceProperties();
    props.setApiHostUpdateBatchSize(1);

    DefaultInventoryService inventoryService = new DefaultInventoryService(api, props);
    inventoryService.sendHostUpdate(Collections.singletonList(createFullyPopulatedConduitFacts()));
    Map<String, Object> expectedFactMap = new HashMap<>();
    expectedFactMap.put("MEMORY", 32757752L);
    expectedFactMap.put("ARCHITECTURE", "x86_64");
    expectedFactMap.put("IS_VIRTUAL", true);
    expectedFactMap.put("VM_HOST", "vm_host");
    expectedFactMap.put("VM_HOST_UUID", "14f64266-f957-4765-8420-c3b6b3002bb7");
    expectedFactMap.put("GUEST_ID", "i_am_a_guest");
    expectedFactMap.put("RH_PROD", Collections.singletonList("72"));
    expectedFactMap.put("orgId", "1234-org");
    expectedFactMap.put("SYSPURPOSE_ROLE", "test_role");
    expectedFactMap.put("SYSPURPOSE_SLA", "Premium");
    expectedFactMap.put("SYSPURPOSE_USAGE", "test_usage");
    expectedFactMap.put("SYSPURPOSE_ADDONS", Arrays.asList("addon1", "addon2"));
    expectedFactMap.put("SYSPURPOSE_UNITS", "Sockets");
    expectedFactMap.put("BILLING_MODEL", "standard");
    FactSet expectedFacts = new FactSet().namespace("rhsm").facts(expectedFactMap);

    SystemProfile systemProfile =
        new SystemProfile()
            .arch("x86_64")
            .biosVendor("bios_vendor")
            .biosVersion("bios_version")
            .cloudProvider("cloud")
            .coresPerSocket(2)
            .infrastructureType("virtual")
            .systemMemoryBytes(1024L)
            .numberOfSockets(4)
            .ownerId("108152b1-6b41-4e1b-b908-922c943e7950");

    CreateHostIn expectedHostEntry =
        new CreateHostIn()
            .account("1234-account")
            .biosUuid("9d9f7927-1f42-4827-bbb8-1791b2b0a1b4")
            .ipAddresses(Collections.singletonList("127.0.0.1"))
            .macAddresses(Collections.singletonList("de:ad:be:ef:fe:ed"))
            .subscriptionManagerId("108152b1-6b41-4e1b-b908-922c943e7950")
            .insightsId("0be977bc-46e9-4d9b-a798-65cd1ed98710")
            .fqdn("test.example.com")
            .facts(Collections.singletonList(expectedFacts))
            .reporter("rhsm-conduit")
            .systemProfile(systemProfile);

    ArgumentCaptor<List<CreateHostIn>> argument = ArgumentCaptor.forClass(List.class);
    Mockito.verify(api).apiHostAddHostList(argument.capture());

    List<CreateHostIn> resultList = argument.getValue();
    assertEquals(1, resultList.size());
    assertCreateHostEquals(expectedHostEntry, resultList.get(0));
  }

  @Test
  void testGetInventoryForOrgConsumersContainsEquivalentConsumerInventory() {
    InventoryServiceProperties props = new InventoryServiceProperties();
    props.setApiHostUpdateBatchSize(1);

    DefaultInventoryService inventoryService = new DefaultInventoryService(null, props);
    ConduitFacts conduitFacts = createFullyPopulatedConduitFacts();
    OrgInventory orgInventory =
        inventoryService.getInventoryForOrgConsumers(Collections.singletonList(conduitFacts));
    assertEquals(1, orgInventory.getConsumerInventories().size());
    assertEquals(conduitFacts, orgInventory.getConsumerInventories().get(0));
  }

  @Test
  void testStaleTimestampUpdatedBasedOnSyncTimestampAndOffset() throws Exception {
    InventoryServiceProperties props = new InventoryServiceProperties();
    props.setApiHostUpdateBatchSize(1);
    props.setStaleHostOffsetInDays(24);

    DefaultInventoryService inventoryService = new DefaultInventoryService(api, props);
    inventoryService.sendHostUpdate(Collections.singletonList(createFullyPopulatedConduitFacts()));

    ArgumentCaptor<List<CreateHostIn>> argument = ArgumentCaptor.forClass(List.class);
    Mockito.verify(api).apiHostAddHostList(argument.capture());
    assertEquals(1, argument.getValue().size());

    CreateHostIn result = argument.getValue().get(0);
    FactSet rhsm = result.getFacts().get(0);
    assertEquals("rhsm", rhsm.getNamespace());

    Map<String, Object> rhsmFacts = (Map<String, Object>) rhsm.getFacts();
    OffsetDateTime syncDate = (OffsetDateTime) rhsmFacts.get("SYNC_TIMESTAMP");
    assertNotNull(syncDate);
    assertEquals(syncDate.plusHours(props.getStaleHostOffsetInDays()), result.getStaleTimestamp());
  }

  @Test
  void scheduleHostUpdateAutoFlushesWhenMaxQueueDepthIsReached() throws Exception {
    InventoryServiceProperties props = new InventoryServiceProperties();
    props.setApiHostUpdateBatchSize(2);

    DefaultInventoryService inventoryService = new DefaultInventoryService(api, props);

    inventoryService.scheduleHostUpdate(createFullyPopulatedConduitFacts());
    inventoryService.scheduleHostUpdate(createFullyPopulatedConduitFacts());

    ArgumentCaptor<List<CreateHostIn>> argument = ArgumentCaptor.forClass(List.class);
    Mockito.verify(api, Mockito.times(1)).apiHostAddHostList(argument.capture());

    List<CreateHostIn> resultList = argument.getValue();
    assertEquals(2, resultList.size());
  }

  @Test
  void noExceptionsWhenOperatingSystemIsNonRhel() throws ApiException {
    InventoryServiceProperties props = new InventoryServiceProperties();
    props.setApiHostUpdateBatchSize(1);

    DefaultInventoryService inventoryService = new DefaultInventoryService(api, props);
    ConduitFacts facts = createFullyPopulatedConduitFacts();
    facts.setOsName("That other OS");
    facts.setOsVersion("42.42");
    inventoryService.sendHostUpdate(Collections.singletonList(facts));

    ArgumentCaptor<List<CreateHostIn>> argument = ArgumentCaptor.forClass(List.class);
    Mockito.verify(api).apiHostAddHostList(argument.capture());

    List<CreateHostIn> resultList = argument.getValue();
    assertEquals(1, resultList.size());
  }

  /**
   * Compare two CreateHostIn objects excepting the syncTimestamp fact since times will be slightly
   * different between an expected CreateHostIn that we create for a test and the one actually
   * created in the InventoryService.
   */
  @SuppressWarnings("unchecked")
  private void assertCreateHostEquals(CreateHostIn expected, CreateHostIn actual) {
    List<FactSet> actualFactSet = actual.getFacts();
    for (FactSet fs : actualFactSet) {
      Map<String, Object> actualMap = (Map<String, Object>) fs.getFacts();
      if (actualMap.containsKey("SYNC_TIMESTAMP") && actualMap.get("SYNC_TIMESTAMP") != null) {
        OffsetDateTime syncTimestamp = (OffsetDateTime) actualMap.remove("SYNC_TIMESTAMP");
        // Since the stale offset for the service is 0, we can check the stale_timestamp
        // property against the sync timestamp.
        expected.setStaleTimestamp(syncTimestamp);
      } else {
        fail("SYNC_TIMESTAMP is missing from the FactSet");
      }
    }
    assertEquals(expected, actual);
  }
}
