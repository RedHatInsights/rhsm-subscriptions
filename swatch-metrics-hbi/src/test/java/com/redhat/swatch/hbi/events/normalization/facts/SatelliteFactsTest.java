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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostFacts;
import org.junit.jupiter.api.Test;

class SatelliteFactsTest {

  @Test
  void nullHbiHostFactsThrowsException() {
    Throwable e =
        assertThrows(IllegalArgumentException.class, () -> new SatelliteFacts((HbiHostFacts) null));
    assertEquals("Satellite fact collection cannot be null", e.getMessage());
  }

  @Test
  void invalidHbiFactNamespaceThrowsException() {
    HbiHostFacts hbiHostFacts = new HbiHostFacts();
    hbiHostFacts.setNamespace("does_not_match_satellite");
    Throwable e =
        assertThrows(IllegalArgumentException.class, () -> new SatelliteFacts(hbiHostFacts));
    assertEquals("Invalid HBI fact namespace for 'satellite' facts.", e.getMessage());
  }

  @Test
  void testDefaults() {
    HbiHostFacts hbiSatelliteFacts = new HbiHostFacts();
    hbiSatelliteFacts.setNamespace(SatelliteFacts.SATELLITE_FACTS_NAMESPACE);
    SatelliteFacts satelliteFacts = new SatelliteFacts(hbiSatelliteFacts);
    assertNull(satelliteFacts.getSla());
    assertNull(satelliteFacts.getUsage());
    assertNull(satelliteFacts.getRole());
    assertNull(satelliteFacts.getHypervisorUuid());
  }

  @Test
  void testExtractHbiFacts() {
    String expectedSla = "Premium";
    String expectedUsage = "Production";
    String expectedRole = "a_role";
    String expectedHypervisorUuid = "a_hypervisor_uuid";

    HbiHostFacts hbiSatelliteFacts = new HbiHostFacts();
    hbiSatelliteFacts.setNamespace(SatelliteFacts.SATELLITE_FACTS_NAMESPACE);
    hbiSatelliteFacts.getFacts().put(SatelliteFacts.SLA_FACT, expectedSla);
    hbiSatelliteFacts.getFacts().put(SatelliteFacts.USAGE_FACT, expectedUsage);
    hbiSatelliteFacts.getFacts().put(SatelliteFacts.ROLE_FACT, expectedRole);
    hbiSatelliteFacts.getFacts().put(SatelliteFacts.VIRTUAL_HOST_UUID_FACT, expectedHypervisorUuid);

    SatelliteFacts satelliteFacts = new SatelliteFacts(hbiSatelliteFacts);
    assertEquals(expectedSla, satelliteFacts.getSla());
    assertEquals(expectedUsage, satelliteFacts.getUsage());
    assertEquals(expectedRole, satelliteFacts.getRole());
    assertEquals(expectedHypervisorUuid, satelliteFacts.getHypervisorUuid());
  }
}
