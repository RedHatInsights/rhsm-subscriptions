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
package com.redhat.swatch.configuration.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VariantTest {

  @Test
  void sanityCheck() {
    assertTrue(true);
  }

  @Test
  void testMigrationProductFlagTrueWithRole() {
    var variant = Variant.findByRole("Red Hat Enterprise Linux Server", true, false);

    assertTrue(variant.isEmpty());
  }

  @Test
  void testMigrationProductFlagFalseWithRole() {
    var variant = Variant.findByRole("Red Hat Enterprise Linux Server", false, false);

    assertEquals(1, variant.size());

    var expected = "RHEL for x86";
    var actual = variant.iterator().next().getTag();

    assertEquals(expected, actual);
  }

  @Test
  void testMigrationProductFlagWithEngIds() {

    var expected = Set.of("rhel-for-x86-els-unconverted");

    var actual =
        Variant.findByEngProductId("204", false, false).stream()
            .map(Variant::getTag)
            .collect(Collectors.toSet());

    assertEquals(expected, actual);
  }

  @Test
  void testMigrationProductFlagTrueWithEngIds() {
    var expected = Set.of("rhel-for-x86-els-payg");

    var variant =
        Variant.findByEngProductId("204", true, true).stream()
            .map(Variant::getTag)
            .collect(Collectors.toSet());

    assertEquals(expected, variant);
  }

  @Test
  void testGetParentSubscription() {
    var variant = Variant.findByRole("Red Hat Enterprise Linux Compute Node", false, false);
    var expected = "rhel-for-x86";

    assertEquals(1, variant.size());

    var actual = variant.iterator().next().getSubscription().getId();
    assertEquals(expected, actual);
  }

  @Test
  void testFindByEngineeringId() {

    var actual = Variant.findByEngProductId("69", false, false);

    assertEquals(1, actual.size());

    assertEquals("RHEL for x86", actual.iterator().next().getTag());
  }

  @Test
  void testGranularityCompatibility() {
    assertTrue(
        Variant.isGranularityCompatible("RHEL for x86", SubscriptionDefinitionGranularity.DAILY));
  }

  @Test
  void testValidProductTag() {
    assertTrue(Variant.isValidProductTag("RHEL for x86"));
    assertFalse(Variant.isValidProductTag("rhel-for-x86"));
    assertTrue(Variant.isValidProductTag("OpenShift Container Platform"));
    assertFalse(Variant.isValidProductTag("openshift-container-platform"));
  }

  @Test
  void testGranularityCompatibilityNotSupported() {
    assertFalse(
        Variant.isGranularityCompatible("RHEL for x86", SubscriptionDefinitionGranularity.HOURLY));
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "204,479;false;false",
        "204,479;false;true",
        "204,479;true;false",
        "204,479;true;true"
      },
      delimiter = ';')
  void test(String engIds, boolean isMetered, boolean isConverted) {

    var productIds = Arrays.stream(engIds.split(",")).collect(Collectors.toSet());

    String role = null;
    var metricIds = Set.of("Sockets", "vCPUs");

    if (role != null) {
      Predicate<Variant> rolePredicate = variant -> variant.getRoles().contains(role);
    }

    var results = new HashSet<Variant>();

    Predicate<Variant> conversionPredicate =
        variant -> variant.getIsMigrationProduct() == isConverted;

    Predicate<Variant> meteredPredicate =
        variant -> variant.getSubscription().isPaygEligible() == isMetered;

    Predicate<Variant> metricIdsPredicate =
        variant -> metricIds.stream().anyMatch(variant.getSubscription().getMetricIds()::contains);

    Predicate<Variant> productIdsPredicate =
        variant -> productIds.stream().anyMatch(variant.getEngineeringIds()::contains);

    var bothPaygAndNon =
        SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
            .flatMap(subscription -> subscription.getVariants().stream())
            .filter(productIdsPredicate)
            .filter(metricIdsPredicate)
            .filter(conversionPredicate)
            .filter(meteredPredicate)
            .collect(Collectors.toSet());

    System.err.println(
        bothPaygAndNon.stream().map(Variant::getTag).collect(Collectors.joining(",")));
  }
}
