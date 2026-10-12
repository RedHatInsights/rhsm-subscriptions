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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostSystemProfile;
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

    HbiHostSystemProfile profile = new HbiHostSystemProfile();
    profile.setHostType(expectedHostType);
    profile.setHypervisorUuid(expectedHypervisorUuid);
    profile.setInfrastructureType(expectedInfrastructureType);
    profile.setCoresPerSocket(expectedCoresPerSocket);
    profile.setSockets(expectedSockets);
    profile.setCpus(expectedCpus);
    profile.setThreadsPerCore(expectedThreadsPerCore);
    profile.setCloudProvider(expectedCloudProvider);
    profile.setArch(expectedArch);
    profile.setIsMarketplace(expectedIsMarketplace);
    profile.setInstalledProducts(
        expectedProductIds.stream()
            .map(
                id -> {
                  HbiHostSystemProfile.InstalledProduct product =
                      new HbiHostSystemProfile.InstalledProduct();
                  product.setId(id);
                  return product;
                })
            .toList());
    HbiHostSystemProfile.Conversion conversion = new HbiHostSystemProfile.Conversion();
    conversion.setActivity(expectedIs3rdPartyMigrated);
    profile.setConversions(conversion);

    HbiHost hbiHost = new HbiHost();
    hbiHost.setSystemProfile(profile);

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

  @Test
  void jacksonIgnoresExtraFieldsInSystemProfile() throws Exception {
    String jsonWithExtraFields =
        """
        {
          "arch": "x86_64",
          "host_type": "virtualized",
          "number_of_cpus": 8,
          "cores_per_socket": 2,
          "number_of_sockets": 4,
          "threads_per_core": 1,
          "is_marketplace": false,
          "infrastructure_type": "virtual",
          "cloud_provider": "aws",
          "virtual_host_uuid": "abc-123",
          "installed_products": [{"id": "479", "name": "RHEL"}],
          "conversions": {"activity": true},
          "rhsm": {"version": "1.0"},
          "systemd": {"state": "running"},
          "cpu_flags": ["avx", "sse4_2"],
          "kernel_modules": ["virtio_blk"],
          "running_processes": ["python3"],
          "network_interfaces": [{"name": "eth0"}],
          "extra_field_1": "should be ignored",
          "extra_field_2": 12345,
          "extra_field_3": ["ignored", "array"]
        }
        """;

    ObjectMapper mapper = new ObjectMapper();
    HbiHostSystemProfile profile =
        mapper.readValue(jsonWithExtraFields, HbiHostSystemProfile.class);

    assertEquals("x86_64", profile.getArch());
    assertEquals("virtualized", profile.getHostType());
    assertEquals(8, profile.getCpus());
    assertEquals(2, profile.getCoresPerSocket());
    assertEquals(4, profile.getSockets());
    assertEquals(1, profile.getThreadsPerCore());
    assertFalse(profile.getIsMarketplace());
    assertEquals("virtual", profile.getInfrastructureType());
    assertEquals("aws", profile.getCloudProvider());
    assertEquals("abc-123", profile.getHypervisorUuid());
    assertEquals(1, profile.getInstalledProducts().size());
    assertEquals("479", profile.getInstalledProducts().get(0).getId());
    assertNotNull(profile.getConversions());
    assertTrue(profile.getConversions().getActivity());

    HbiHost hbiHost = new HbiHost();
    hbiHost.setSystemProfile(profile);

    SystemProfileFacts facts = new SystemProfileFacts(hbiHost);
    assertEquals("x86_64", facts.getArch());
    assertEquals("virtualized", facts.getHostType());
    assertEquals(8, facts.getCpus());
    assertEquals(2, facts.getCoresPerSocket());
    assertEquals(4, facts.getSockets());
    assertEquals(1, facts.getThreadsPerCore());
    assertFalse(facts.getIsMarketplace());
    assertEquals("virtual", facts.getInfrastructureType());
    assertEquals("aws", facts.getCloudProvider());
    assertEquals("abc-123", facts.getHypervisorUuid());
    assertEquals(Set.of("479"), facts.getProductIds());
    assertTrue(facts.getIs3rdPartyMigrated());
  }
}
