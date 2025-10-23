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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostFacts;
import com.redhat.swatch.hbi.events.normalization.model.facts.RhsmFacts;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RhsmFactsTest {

  @Test
  void nullHbiHostFactsThrowsException() {
    Throwable e =
        assertThrows(IllegalArgumentException.class, () -> new RhsmFacts((HbiHostFacts) null));
    assertEquals("RHSM fact collection cannot be null", e.getMessage());
  }

  @Test
  void invalidHbiFactNamespaceThrowsException() {
    HbiHostFacts hbiHostFacts = new HbiHostFacts();
    hbiHostFacts.setNamespace("does_not_match_rhsm");
    Throwable e = assertThrows(IllegalArgumentException.class, () -> new RhsmFacts(hbiHostFacts));
    assertEquals("Invalid HBI fact namespace for 'rhsm' facts.", e.getMessage());
  }

  @Test
  void testDefaults() {
    HbiHostFacts hbiRhsmFacts = new HbiHostFacts();
    hbiRhsmFacts.setNamespace(RhsmFacts.RHSM_FACTS_NAMESPACE);
    RhsmFacts rhsmFacts = new RhsmFacts(hbiRhsmFacts);
    assertNull(rhsmFacts.getSla());
    assertNull(rhsmFacts.getUsage());
    assertNull(rhsmFacts.getSyncTimestamp());
    assertNull(rhsmFacts.getIsVirtual());
    assertNull(rhsmFacts.getSystemPurposeRole());
    assertNull(rhsmFacts.getSystemPurposeUnits());
    assertNull(rhsmFacts.getBillingModel());
    assertNull(rhsmFacts.getGuestId());
    assertTrue(rhsmFacts.getProductIds().isEmpty());
  }

  @Test
  void testExtractHbiFacts() {
    String expectedSla = "Premium";
    String expectedUsage = "Production";
    String expectedSyncTimestamp = "2024-01-01T00:00:00.000Z";
    boolean expectedIsVirtual = true;
    String expectedSystemPurposeRole = "a_role";
    String expectedSystemPurposeUnits = "Sockets";
    String expectedBillingModel = "a_billing_model";
    String expectedGuestId = "a_guest";
    String expectedProductId = "69";

    HbiHostFacts hbiRhsmFacts = new HbiHostFacts();
    hbiRhsmFacts.setNamespace(RhsmFacts.RHSM_FACTS_NAMESPACE);
    hbiRhsmFacts.getFacts().put(RhsmFacts.SLA_FACT, expectedSla);
    hbiRhsmFacts.getFacts().put(RhsmFacts.USAGE_FACT, expectedUsage);
    hbiRhsmFacts.getFacts().put(RhsmFacts.SYNC_TIMESTAMP_FACT, expectedSyncTimestamp);
    hbiRhsmFacts.getFacts().put(RhsmFacts.IS_VIRTUAL_FACT, expectedIsVirtual);
    hbiRhsmFacts.getFacts().put(RhsmFacts.SYSTEM_PURPOSE_ROLE_FACT, expectedSystemPurposeRole);
    hbiRhsmFacts.getFacts().put(RhsmFacts.SYSTEM_PURPOSE_UNITS_FACT, expectedSystemPurposeUnits);
    hbiRhsmFacts.getFacts().put(RhsmFacts.BILLING_MODEL, expectedBillingModel);
    hbiRhsmFacts.getFacts().put(RhsmFacts.GUEST_ID, expectedGuestId);
    hbiRhsmFacts.getFacts().put(RhsmFacts.PRODUCT_IDS_FACT, List.of(expectedProductId));

    RhsmFacts rhsmFacts = new RhsmFacts(hbiRhsmFacts);
    assertEquals(expectedSla, rhsmFacts.getSla());
    assertEquals(expectedUsage, rhsmFacts.getUsage());
    assertEquals(expectedSyncTimestamp, rhsmFacts.getSyncTimestamp());
    assertTrue(rhsmFacts.getIsVirtual());
    assertEquals(expectedSystemPurposeRole, rhsmFacts.getSystemPurposeRole());
    assertEquals(expectedSystemPurposeUnits, rhsmFacts.getSystemPurposeUnits());
    assertEquals(expectedBillingModel, rhsmFacts.getBillingModel());
    assertEquals(expectedGuestId, rhsmFacts.getGuestId());
    assertEquals(Set.of(expectedProductId), rhsmFacts.getProductIds());
  }
}
