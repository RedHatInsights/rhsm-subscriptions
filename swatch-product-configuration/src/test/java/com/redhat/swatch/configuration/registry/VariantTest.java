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

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class VariantTest {

  @Test
  void sanityCheck() {
    assertTrue(true);
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
}
