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
package com.redhat.swatch.hbi.events.normalization.facts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.normalization.model.facts.SystemProfileFacts;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SystemProfileFactsTest {

  @Test
  void nullHbiHostThrowsException() {
    Throwable e = assertThrows(IllegalArgumentException.class, () -> new SystemProfileFacts(null));
    assertEquals("HbiHost cannot be null when initializing system profile facts", e.getMessage());
  }

  @Test
  void testDefaults() {
    SystemProfileFacts facts = new SystemProfileFacts(new HbiHost());
    assertNull(facts.getHostType());
    assertNull(facts.getHypervisorUuid());
    assertNull(facts.getInfrastructureType());
    assertNull(facts.getCoresPerSocket());
    assertNull(facts.getSockets());
    assertNull(facts.getCpus());
    assertNull(facts.getThreadsPerCore());
    assertNull(facts.getCloudProvider());
    assertNull(facts.getArch());
    assertFalse(facts.getIsMarketplace());
    assertFalse(facts.getIs3rdPartyMigrated());
    assertTrue(facts.getProductIds().isEmpty());
  }

  @Test
  void testExtractHbiFacts() {
    String expectedHostType = "host_type";
    String expectedHypervisorUuid = "hypervisor_uuid";
    String expectedInfrastructureType = "infrastructure_type";
    Integer expectedCoresPerSocket = 4;
    Integer expectedSockets = 2;
    Integer expectedCpus = 1;
    Integer expectedThreadsPerCore = 2;
    String expectedCloudProvider = "cloud_provider";
    String expectedArch = "x86_64";
    Boolean expectedIsMarketplace = true;
    Boolean expectedIs3rdPartyMigrated = true;
    Set<String> expectedProductIds = Set.of("60", "70");

    Map<String, Object> systemProfileHbiFacts = new HashMap<>();
    systemProfileHbiFacts.put(SystemProfileFacts.HOST_TYPE_FACT, expectedHostType);
    systemProfileHbiFacts.put(SystemProfileFacts.HYPERVISOR_UUID_FACT, expectedHypervisorUuid);
    systemProfileHbiFacts.put(
        SystemProfileFacts.INFRASTRUCTURE_TYPE_FACT, expectedInfrastructureType);
    systemProfileHbiFacts.put(SystemProfileFacts.CORES_PER_SOCKET_FACT, expectedCoresPerSocket);
    systemProfileHbiFacts.put(SystemProfileFacts.SOCKETS_FACT, expectedSockets);
    systemProfileHbiFacts.put(SystemProfileFacts.CPUS_FACT, expectedCpus);
    systemProfileHbiFacts.put(SystemProfileFacts.THREADS_PER_CORE_FACT, expectedThreadsPerCore);
    systemProfileHbiFacts.put(SystemProfileFacts.CLOUD_PROVIDER_FACT, expectedCloudProvider);
    systemProfileHbiFacts.put(SystemProfileFacts.ARCH_FACT, expectedArch);
    systemProfileHbiFacts.put(SystemProfileFacts.IS_MARKETPLACE_FACT, expectedIsMarketplace);
    systemProfileHbiFacts.put(
        SystemProfileFacts.CONVERSIONS_FACT,
        Map.of(SystemProfileFacts.CONVERSIONS_ACTIVITY, expectedIs3rdPartyMigrated));
    systemProfileHbiFacts.put(
        SystemProfileFacts.INSTALLED_PRODUCTS_FACT,
        expectedProductIds.stream()
            .map(
                pid ->
                    Map.of(
                        SystemProfileFacts.INSTALLED_PRODUCT_ID_FACT,
                        pid,
                        // No constant for name since we don't use the name during extraction.
                        // Just making the data consistent.
                        "name",
                        "Product " + pid))
            .toList());

    HbiHost hbiHost = new HbiHost();
    hbiHost.setSystemProfile(systemProfileHbiFacts);

    SystemProfileFacts facts = new SystemProfileFacts(hbiHost);
    assertEquals(expectedHostType, facts.getHostType());
    assertEquals(expectedHypervisorUuid, facts.getHypervisorUuid());
    assertEquals(expectedInfrastructureType, facts.getInfrastructureType());
    assertEquals(expectedCoresPerSocket, facts.getCoresPerSocket());
    assertEquals(expectedSockets, facts.getSockets());
    assertEquals(expectedCpus, facts.getCpus());
    assertEquals(expectedThreadsPerCore, facts.getThreadsPerCore());
    assertEquals(expectedCloudProvider, facts.getCloudProvider());
    assertEquals(expectedArch, facts.getArch());
    assertTrue(facts.getIsMarketplace());
    assertTrue(facts.getIs3rdPartyMigrated());
    assertEquals(expectedProductIds, facts.getProductIds());
  }
}
