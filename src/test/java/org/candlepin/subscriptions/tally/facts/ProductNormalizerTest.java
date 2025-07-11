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
package org.candlepin.subscriptions.tally.facts;

import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createQpcHost;
import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createSystemProfileHost;
import static org.candlepin.subscriptions.tally.facts.product.QpcProductRule.RHEL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
@ExtendWith(OutputCaptureExtension.class)
class ProductNormalizerTest {

  @Autowired ProductNormalizer productNormalizer;

  @Test
  void testProductIdFromEngId() {
    // it should aggregate the RHEL product using the QPC product rule
    var host = createQpcHost(RHEL, "Test", OffsetDateTime.now(Clock.systemUTC()));
    // and RHEL for x86 from the System Profile product IDs product rule
    host.setSystemProfileProductIds("69");
    boolean is3rdPartyMigrated = false;
    var skipRhsm = false;

    var actual = productNormalizer.normalizeProducts(host, is3rdPartyMigrated, skipRhsm);
    // "RHEL Ungrouped" is added after reconciling all the products from rules
    var expected = Set.of("RHEL Ungrouped", "RHEL for x86", "RHEL");
    assertEquals(expected, actual);
  }

  @Test
  void testProductIdIsConfiguredButNotMatchedThenLogIsTraced(CapturedOutput output) {
    // given a host using the product ID 479, but using third party migration enabled will not be
    // matched
    var host = createSystemProfileHost("org123", List.of(479), 1, 4, OffsetDateTime.now());
    boolean is3rdPartyMigrated = true;
    var skipRhsm = false;

    var actual = productNormalizer.normalizeProducts(host, is3rdPartyMigrated, skipRhsm);

    assertTrue(actual.isEmpty());
    assertTrue(output.getAll().contains("No products matched for host with name"));
  }

  @Test
  void testProductIdIsNotConfiguredAndNotMatchedThenLogIsNotTraced(CapturedOutput output) {
    // given a host using the non-existing product ID 666
    var host = createSystemProfileHost("org123", List.of(666), 1, 4, OffsetDateTime.now());
    boolean is3rdPartyMigrated = true;
    var skipRhsm = false;

    var actual = productNormalizer.normalizeProducts(host, is3rdPartyMigrated, skipRhsm);

    assertTrue(actual.isEmpty());
    assertFalse(output.getAll().contains("No products matched for host with name"));
  }

  @Test
  void testDebugSpecificHostFactsMissingRhelForX86() {
    // Test with the exact InventoryHostFacts data provided by the user
    // This host has systemProfileProductIds=[479] and satelliteRole="Red Hat Enterprise Linux
    // Server"
    // but should still get "RHEL for x86" from the satellite role

    InventoryHostFacts hostFacts = new InventoryHostFacts();
    hostFacts.setInventoryId(UUID.fromString("09a90cff-50d3-47f9-9df3-32c4827806ea"));
    hostFacts.setModifiedOn(OffsetDateTime.parse("2025-07-11T03:22:51.416842Z"));
    hostFacts.setAccount("88888888");
    hostFacts.setDisplayName("oracle-ecc02");
    hostFacts.setOrgId("77777777");
    hostFacts.setSyncTimestamp("");
    hostFacts.setProducts(""); // Empty string for empty products
    hostFacts.setSystemProfileInfrastructureType("virtual");
    hostFacts.setSystemProfileCoresPerSocket(1);
    hostFacts.setSystemProfileSockets(4);
    hostFacts.setSystemProfileCpus(4);
    hostFacts.setSystemProfileThreadsPerCore(1);
    hostFacts.setSystemProfileArch("x86_64");
    hostFacts.setMarketplace(false);
    hostFacts.setConversionsActivity(true);
    hostFacts.setVirtual(false);
    hostFacts.setHypervisorUuid(null);
    hostFacts.setSatelliteHypervisorUuid(null);
    hostFacts.setSatelliteRole("Red Hat Enterprise Linux Server");
    hostFacts.setSatelliteSla("Standard");
    hostFacts.setSatelliteUsage("Production");
    hostFacts.setGuestId(null);
    hostFacts.setSubscriptionManagerId("575b9f67-3b56-4c03-b54f-38b30d2132da");
    hostFacts.setInsightsId("575b9f67-3b56-4c03-b54f-38b30d2132da");
    hostFacts.setProviderId(null);
    hostFacts.setQpcProducts(""); // Empty string for empty QPC products
    hostFacts.setSystemProfileProductIds("479"); // String representation of product ID
    hostFacts.setSyspurposeRole(null);
    hostFacts.setSyspurposeSla(null);
    hostFacts.setSyspurposeUsage(null);
    hostFacts.setSyspurposeUnits(null);
    hostFacts.setBillingModel(null);
    hostFacts.setCloudProvider(null);
    hostFacts.setStaleTimestamp(OffsetDateTime.parse("2025-07-12T08:22:51.211788Z"));
    hostFacts.setHardwareSubmanId("575b9f67-3b56-4c03-b54f-38b30d2132da");

    boolean is3rdPartyMigrated = false;
    boolean skipRhsm = false;

    Set<String> actual =
        productNormalizer.normalizeProducts(hostFacts, is3rdPartyMigrated, skipRhsm);

    // Debug output to see what products were found
    System.out.println("Actual products found: " + actual);

    // The host has satelliteRole="Red Hat Enterprise Linux Server" which should map to "RHEL for
    // x86"
    // via the SatelliteRoleProductRule
    assertTrue(
        actual.contains("RHEL for x86"),
        "Expected 'RHEL for x86' to be included in products. Actual products: " + actual);

    // Also check if any other RHEL products are present
    boolean hasAnyRhelProduct = actual.stream().anyMatch(product -> product.startsWith("RHEL"));
    assertTrue(
        hasAnyRhelProduct,
        "Expected at least one RHEL product to be present. Actual products: " + actual);
  }
}
