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
package com.redhat.swatch.hbi.events.normalization;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.hbi.events.normalization.facts.QpcFacts;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SatelliteFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SystemProfileFacts;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProductNormalizerTest {

  static final String RHEL_FOR_X86 = "RHEL for x86";
  static final String RHEL_FOR_ARM = "RHEL for ARM";
  static final String RHEL_FOR_IBM_POWER = "RHEL for IBM Power";

  @Test
  void applyingNullSystemProfileProductsOk() {
    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts("x86_64", false, null),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            false);
    assertTrue(productNormalizer.getProductTags().isEmpty());
    assertTrue(productNormalizer.getProductIds().isEmpty());
  }

  @Test
  void appliesSystemProfileProducts() {
    Set<String> definedProductIds = Set.of("69", "408");
    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts("x86_64", false, definedProductIds),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            false);
    assertEquals(1, productNormalizer.getProductTags().size());
    assertThat(productNormalizer.getProductTags(), Matchers.hasItem("RHEL for x86"));
    assertEquals(2, productNormalizer.getProductIds().size());
    assertThat(productNormalizer.getProductIds(), Matchers.hasItems("69", "408"));
  }

  @Test
  void appliesSystemProfileProductsFilteringByMigration() {
    Set<String> definedProductIds = Set.of("69", "204");
    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts("x86_64", true, definedProductIds),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            false);
    assertEquals(1, productNormalizer.getProductTags().size());
    assertThat(productNormalizer.getProductTags(), Matchers.hasItem("rhel-for-x86-els-converted"));
    assertEquals(2, productNormalizer.getProductIds().size());
    assertThat(productNormalizer.getProductIds(), Matchers.hasItems("69", "204"));
  }

  @Test
  void appliesQpcProductsWithNoSystemProfileArchSpecified() {
    Set<String> rhIntalledProducts = Set.of("EAP", "RHEL");
    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts(null, false, Set.of()),
            Optional.empty(),
            Optional.empty(),
            Optional.of(qpcFacts(rhIntalledProducts)),
            false);
    assertEquals(1, productNormalizer.getProductTags().size());
    assertThat(productNormalizer.getProductTags(), Matchers.equalTo(Set.of("RHEL")));
    assertTrue(productNormalizer.getProductIds().isEmpty());
  }

  @Test
  void appliesQpcProductsWithUnrecognizedSystemProfileArch() {
    Set<String> rhInstalledProducts = Set.of("RHEL");
    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts("s390x", false, Set.of()),
            Optional.empty(),
            Optional.empty(),
            Optional.of(qpcFacts(rhInstalledProducts)),
            false);

    assertThat(productNormalizer.getProductTags(), Matchers.equalTo(Set.of("RHEL")));
    assertTrue(productNormalizer.getProductIds().isEmpty());
  }

  private static Stream<Arguments> provideArchCombinations() {

    return Stream.of(
        Arguments.of("x86_64", RHEL_FOR_X86),
        Arguments.of("i386", RHEL_FOR_X86),
        Arguments.of("i686", RHEL_FOR_X86),
        Arguments.of("i686", RHEL_FOR_X86),
        Arguments.of("ppc64le", RHEL_FOR_IBM_POWER),
        Arguments.of("aarch64", RHEL_FOR_ARM));
  }

  @ParameterizedTest
  @MethodSource("provideArchCombinations")
  void appliesQpcProductsWithNoSystemProfileArchSpecified(String arch, String expectedProduct) {
    Set<String> rhIntalledProducts = Set.of("RHEL");
    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts(arch, false, Set.of()),
            Optional.empty(),
            Optional.empty(),
            Optional.of(qpcFacts(rhIntalledProducts)),
            false);
    assertEquals(2, productNormalizer.getProductTags().size());
    assertThat(
        productNormalizer.getProductTags(), Matchers.equalTo(Set.of("RHEL", expectedProduct)));
    assertTrue(productNormalizer.getProductIds().isEmpty());
  }

  @Test
  void appliesNoProductsWhenRhelNotDefinedInQpcProducts() {
    Set<String> rhIntalledProducts = Set.of("QPC_PRODUCT");
    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts("x86_64", false, Set.of()),
            Optional.empty(),
            Optional.empty(),
            Optional.of(qpcFacts(rhIntalledProducts)),
            false);
    assertTrue(productNormalizer.getProductTags().isEmpty());
    assertTrue(productNormalizer.getProductIds().isEmpty());
  }

  private static Stream<Arguments> provideRhsmProducts() {
    return Stream.of(
        Arguments.of(null, Set.of(), Set.of()),
        Arguments.of(Set.of(), Set.of(), Set.of()),
        Arguments.of(Set.of("420"), Set.of("RHEL for IBM Power"), Set.of("420")),
        Arguments.of(
            Set.of("69", "76"), Set.of("RHEL for x86", "RHEL Compute Node"), Set.of("69", "76")));
  }

  @ParameterizedTest
  @MethodSource("provideRhsmProducts")
  void appliesRhsmProducts(
      Set<String> rhsmProductIds, Set<String> expectedProductTags, Set<String> expectedProductIds) {
    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts("x86_64", false, Set.of()),
            Optional.of(rhsmFacts(rhsmProductIds)),
            Optional.empty(),
            Optional.empty(),
            false);

    assertThat(productNormalizer.getProductTags(), Matchers.equalTo(expectedProductTags));
    assertThat(productNormalizer.getProductIds(), Matchers.equalTo(expectedProductIds));
  }

  private static Stream<Arguments> provideRhsmMigration() {
    return Stream.of(
        Arguments.of(true, Set.of("rhel-for-x86-els-converted")),
        Arguments.of(false, Set.of("rhel-for-x86-els-unconverted")));
  }

  @ParameterizedTest
  @MethodSource("provideRhsmMigration")
  void appliesCorrectRhsmProductsWhenSystemIsMigrated(
      boolean is3rdPartyMigrated, Set<String> expectedProductTags) {
    // rhel_for_x86_els
    Set<String> productIds = Set.of("204");
    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts("x86_64", is3rdPartyMigrated, Set.of()),
            Optional.of(rhsmFacts(productIds)),
            Optional.empty(),
            Optional.empty(),
            false);

    assertThat(productNormalizer.getProductTags(), Matchers.equalTo(expectedProductTags));
    assertThat(productNormalizer.getProductIds(), Matchers.equalTo(productIds));
  }

  @Test
  void noRhsmProductsAppliedWhenSkipRhsmFactsIsTrue() {
    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts("x86_64", false, Set.of()),
            Optional.of(rhsmFacts(Set.of("69"))),
            Optional.empty(),
            Optional.empty(),
            true);
    assertTrue(productNormalizer.getProductTags().isEmpty());
    assertTrue(productNormalizer.getProductIds().isEmpty());
  }

  @Test
  void testQpcProductIdFromEngId() {
    Set<String> rhIntalledProducts = Set.of("RHEL");
    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts("test_arch", false, Set.of("69")),
            Optional.empty(),
            Optional.empty(),
            Optional.of(qpcFacts(rhIntalledProducts)),
            false);
    assertEquals(2, productNormalizer.getProductTags().size());
    assertThat(
        productNormalizer.getProductTags(), Matchers.equalTo(Set.of("RHEL", "RHEL for x86")));
    assertEquals(1, productNormalizer.getProductIds().size());
    assertThat(productNormalizer.getProductIds(), Matchers.hasItems("69"));
  }

  @Test
  void appliesSatelliteRoleProducts() {
    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts("x86_64", false, Set.of()),
            Optional.empty(),
            Optional.of(satelliteFacts("Red Hat Enterprise Linux Workstation")),
            Optional.empty(),
            false);
    assertEquals(1, productNormalizer.getProductTags().size());
    assertThat(productNormalizer.getProductTags(), Matchers.equalTo(Set.of("RHEL Workstation")));
    assertTrue(productNormalizer.getProductIds().isEmpty());
  }

  private SystemProfileFacts systemProfileFacts(
      String arch, boolean is3rdPartyMigrated, Set<String> productIds) {
    return new SystemProfileFacts(
        "host_type",
        "hypervisor_uuid",
        "infra",
        2,
        2,
        null,
        null,
        null,
        arch,
        false,
        is3rdPartyMigrated,
        productIds);
  }

  private RhsmFacts rhsmFacts(Set<String> productIds) {
    return new RhsmFacts(
        "Premium",
        "Production",
        OffsetDateTime.now().toString(),
        false,
        "a_role",
        "sockets",
        null,
        null,
        productIds);
  }

  private SatelliteFacts satelliteFacts(String role) {
    return new SatelliteFacts("Premium", "Production", role, "hypervisor_uuid");
  }

  private QpcFacts qpcFacts(Set<String> products) {
    return new QpcFacts(products);
  }

  @Test
  void testGetProductTagsForMetricRhelSockets() {
    // RHEL only supports Sockets, not Cores
    Set<String> productTags =
        ProductNormalizer.getProductTagsForMetric(
            "Sockets",
            systemProfileFacts("x86_64", false, Set.of("69")),
            Optional.of(rhsmFacts(Set.of("69"))),
            Optional.empty(),
            Optional.empty(),
            false);

    assertEquals(Set.of(RHEL_FOR_X86), productTags, "RHEL should support Sockets metric");
  }

  @Test
  void testGetProductTagsForMetricRhelCores() {
    // RHEL does NOT support Cores metric
    Set<String> productTags =
        ProductNormalizer.getProductTagsForMetric(
            "Cores",
            systemProfileFacts("x86_64", false, Set.of("69")),
            Optional.of(rhsmFacts(Set.of("69"))),
            Optional.empty(),
            Optional.empty(),
            false);

    assertTrue(productTags.isEmpty(), "RHEL should NOT support Cores metric");
  }

  @Test
  void testGetProductTagsForMetricSkipStaleRhsm() {
    // When skipRhsmFacts=true, RHSM products should be excluded
    Set<String> productTags =
        ProductNormalizer.getProductTagsForMetric(
            "Sockets",
            systemProfileFacts("x86_64", false, Set.of()),
            Optional.of(rhsmFacts(Set.of("69"))),
            Optional.empty(),
            Optional.empty(),
            true); // skipRhsmFacts=true

    assertTrue(
        productTags.isEmpty(), "Stale RHSM facts should be excluded from product tags for metric");
  }

  @Test
  void testGetProductTagsForMetricIncludeNonStaleRhsm() {
    // When skipRhsmFacts=false, RHSM products should be included
    Set<String> productTags =
        ProductNormalizer.getProductTagsForMetric(
            "Sockets",
            systemProfileFacts("x86_64", false, Set.of()),
            Optional.of(rhsmFacts(Set.of("69"))),
            Optional.empty(),
            Optional.empty(),
            false); // skipRhsmFacts=false

    assertEquals(
        Set.of(RHEL_FOR_X86), productTags, "Non-stale RHSM facts should be included for metric");
  }

  @Test
  void testGetProductTagsForMetricSatelliteSockets() {
    // Satellite only supports Sockets
    Set<String> productTags =
        ProductNormalizer.getProductTagsForMetric(
            "Sockets",
            systemProfileFacts("x86_64", false, Set.of("250")),
            Optional.empty(),
            Optional.of(satelliteFacts("Red Hat Enterprise Linux Server")),
            Optional.empty(),
            false);

    assertEquals(
        Set.of("Satellite Server"), productTags, "Satellite should support Sockets metric");
  }

  @Test
  void testGetProductTagsForMetricSatelliteCores() {
    // Satellite does NOT support Cores
    Set<String> productTags =
        ProductNormalizer.getProductTagsForMetric(
            "Cores",
            systemProfileFacts("x86_64", false, Set.of("250")),
            Optional.empty(),
            Optional.of(satelliteFacts("Red Hat Enterprise Linux Server")),
            Optional.empty(),
            false);

    assertTrue(productTags.isEmpty(), "Satellite should NOT support Cores metric");
  }

  @Test
  void testGetProductTagsForMetricOpenShiftBothMetrics() {
    // OpenShift supports both Cores and Sockets
    Set<String> coresTags =
        ProductNormalizer.getProductTagsForMetric(
            "Cores",
            systemProfileFacts("x86_64", false, Set.of("290")),
            Optional.of(rhsmFacts(Set.of("290"))),
            Optional.empty(),
            Optional.empty(),
            false);

    Set<String> socketsTags =
        ProductNormalizer.getProductTagsForMetric(
            "Sockets",
            systemProfileFacts("x86_64", false, Set.of("290")),
            Optional.of(rhsmFacts(Set.of("290"))),
            Optional.empty(),
            Optional.empty(),
            false);

    assertEquals(
        Set.of("OpenShift Container Platform"), coresTags, "OpenShift should support Cores metric");
    assertEquals(
        Set.of("OpenShift Container Platform"),
        socketsTags,
        "OpenShift should support Sockets metric");
  }

  @Test
  void testGetProductTagsForMetricIncludedSubscriptionPruning() {
    // When both Satellite and RHEL present, RHEL should be pruned (included subscription)
    Set<String> productTags =
        ProductNormalizer.getProductTagsForMetric(
            "Sockets",
            systemProfileFacts("x86_64", false, Set.of("250")),
            Optional.of(rhsmFacts(Set.of("69"))),
            Optional.of(satelliteFacts("Red Hat Enterprise Linux Server")),
            Optional.empty(),
            false);

    assertEquals(
        Set.of("Satellite Server"),
        productTags,
        "RHEL should be pruned when Satellite present (included subscription)");
  }

  @Test
  void testMultipleRhelVariantsDoNotAddRhelUngrouped() {
    // Test with multiple RHEL role-based variants: variantCount > 1
    // Product IDs: 71 (Workstation), 76 (Compute Node), 69 (Server)
    // Expected: All variants kept, but "RHEL Ungrouped" NOT added
    Set<String> productIds = Set.of("69", "71", "76");
    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts("x86_64", false, productIds),
            Optional.of(rhsmFacts(productIds)),
            Optional.empty(),
            Optional.empty(),
            false);

    Set<String> productTags = productNormalizer.getProductTags();

    // Verify all three variants are present
    assertTrue(
        productTags.contains("RHEL for x86"), "RHEL for x86 should be present (from product 69)");
    assertTrue(
        productTags.contains("RHEL Workstation"),
        "RHEL Workstation should be present (from product 71)");
    assertTrue(
        productTags.contains("RHEL Compute Node"),
        "RHEL Compute Node should be present (from product 76)");

    // Verify "RHEL Ungrouped" is NOT added (this is the key assertion)
    assertFalse(
        productTags.contains("RHEL Ungrouped"),
        "RHEL Ungrouped should NOT be added even with multiple variants (variantCount=2)");

    // Expected tags: 3 real variants (no "RHEL Ungrouped")
    assertEquals(
        Set.of("RHEL for x86", "RHEL Workstation", "RHEL Compute Node"),
        productTags,
        "Should have exactly 3 real product variants, no RHEL Ungrouped marker");
  }
}
