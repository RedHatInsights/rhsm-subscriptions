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

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.insights.api.model.OrgInventory;
import org.candlepin.insights.inventory.client.ApiException;
import org.candlepin.insights.inventory.client.model.CreateHostIn;
import org.candlepin.insights.inventory.client.model.FactSet;
import org.candlepin.insights.inventory.client.resources.HostsApi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class DefaultInventoryServiceTest {
    @Mock
    HostsApi api;

    private ConduitFacts createFullyPopulatedConduitFacts() {
        ConduitFacts conduitFacts = new ConduitFacts();
        conduitFacts.setAccountNumber("1234-account");
        conduitFacts.setArchitecture("x86_64");
        conduitFacts.setBiosUuid("9d9f7927-1f42-4827-bbb8-1791b2b0a1b4");
        conduitFacts.setCpuCores(8);
        conduitFacts.setCpuSockets(4);
        conduitFacts.setFqdn("test.example.com");
        conduitFacts.setIpAddresses(Collections.singletonList("127.0.0.1"));
        conduitFacts.setMacAddresses(Collections.singletonList("de:ad:be:ef:fe:ed"));
        conduitFacts.setMemory(32757752L);
        conduitFacts.setOrgId("1234-org");
        conduitFacts.setRhProd(Collections.singletonList("72"));
        conduitFacts.setSubscriptionManagerId("108152b1-6b41-4e1b-b908-922c943e7950");
        conduitFacts.setInsightsId("0be977bc-46e9-4d9b-a798-65cd1ed98710");
        conduitFacts.setIsVirtual(true);
        conduitFacts.setVmHost("vm_host");
        conduitFacts.setGuestId("i_am_a_guest");
        conduitFacts.setVmHostUuid("14f64266-f957-4765-8420-c3b6b3002bb7");
        conduitFacts.setSysPurposeRole("test_role");
        conduitFacts.setSysPurposeUsage("test_usage");
        conduitFacts.setSysPurposeAddons(Arrays.asList("addon1", "addon2"));
        return conduitFacts;
    }

    @Test
    public void testSendHostUpdatePopulatesAllFieldsWithFullConduitFactsRecord() throws ApiException {
        DefaultInventoryService inventoryService = new DefaultInventoryService(api, 1);
        inventoryService.sendHostUpdate(Collections.singletonList(createFullyPopulatedConduitFacts()));
        Map<String, Object> expectedFactMap = new HashMap<>();
        expectedFactMap.put("CPU_SOCKETS", 4);
        expectedFactMap.put("CPU_CORES", 8);
        expectedFactMap.put("MEMORY", 32757752L);
        expectedFactMap.put("ARCHITECTURE", "x86_64");
        expectedFactMap.put("IS_VIRTUAL", true);
        expectedFactMap.put("VM_HOST", "vm_host");
        expectedFactMap.put("VM_HOST_UUID", "14f64266-f957-4765-8420-c3b6b3002bb7");
        expectedFactMap.put("GUEST_ID", "i_am_a_guest");
        expectedFactMap.put("RH_PROD", Collections.singletonList("72"));
        expectedFactMap.put("orgId", "1234-org");
        expectedFactMap.put("SYSPURPOSE_ROLE", "test_role");
        expectedFactMap.put("SYSPURPOSE_USAGE", "test_usage");
        expectedFactMap.put("SYSPURPOSE_ADDONS", Arrays.asList("addon1", "addon2"));
        FactSet expectedFacts = new FactSet().namespace("rhsm").facts(expectedFactMap);

        CreateHostIn expectedHostEntry = new CreateHostIn()
            .account("1234-account")
            .biosUuid("9d9f7927-1f42-4827-bbb8-1791b2b0a1b4")
            .ipAddresses(Collections.singletonList("127.0.0.1"))
            .macAddresses(Collections.singletonList("de:ad:be:ef:fe:ed"))
            .subscriptionManagerId("108152b1-6b41-4e1b-b908-922c943e7950")
            .insightsId("0be977bc-46e9-4d9b-a798-65cd1ed98710")
            .fqdn("test.example.com")
            .facts(Collections.singletonList(expectedFacts));

        ArgumentCaptor<List<CreateHostIn>> argument = ArgumentCaptor.forClass(List.class);
        Mockito.verify(api).apiHostAddHostList(argument.capture());

        List<CreateHostIn> resultList = argument.getValue();
        assertEquals(1, resultList.size());
        assertCreateHostEquals(expectedHostEntry, resultList.get(0));
    }

    @Test
    public void testGetInventoryForOrgConsumersContainsEquivalentConsumerInventory() {
        DefaultInventoryService inventoryService = new DefaultInventoryService(null, 1);
        ConduitFacts conduitFacts = createFullyPopulatedConduitFacts();
        OrgInventory orgInventory = inventoryService.getInventoryForOrgConsumers(
            Collections.singletonList(conduitFacts));
        assertEquals(1, orgInventory.getConsumerInventories().size());
        assertEquals(conduitFacts, orgInventory.getConsumerInventories().get(0));
    }

    @Test
    public void scheduleHostUpdateAutoFlushesWhenMaxQueueDepthIsReached() throws Exception {
        DefaultInventoryService inventoryService = new DefaultInventoryService(api, 2);

        inventoryService.scheduleHostUpdate(createFullyPopulatedConduitFacts());
        inventoryService.scheduleHostUpdate(createFullyPopulatedConduitFacts());

        ArgumentCaptor<List<CreateHostIn>> argument = ArgumentCaptor.forClass(List.class);
        Mockito.verify(api, Mockito.times(1)).apiHostAddHostList(argument.capture());

        List<CreateHostIn> resultList = argument.getValue();
        assertEquals(2, resultList.size());
    }

    /**
     * Compare two CreateHostIn objects excepting the syncTimestamp fact since times will be slightly
     * different between an expected CreateHostIn that we create for a test and the one actually created in
     * the InventoryService.
     */
    @SuppressWarnings("unchecked")
    private void assertCreateHostEquals(CreateHostIn expected, CreateHostIn actual) {
        List<FactSet> actualFactSet = actual.getFacts();
        for (FactSet fs : actualFactSet) {
            Map<String, Object> actualMap = (Map<String, Object>) fs.getFacts();
            if (actualMap.containsKey("SYNC_TIMESTAMP") && actualMap.get("SYNC_TIMESTAMP") != null) {
                actualMap.remove("SYNC_TIMESTAMP");
            }
            else {
                fail("SYNC_TIMESTAMP is missing from the FactSet");
            }
        }
        assertEquals(expected, actual);
    }

}
