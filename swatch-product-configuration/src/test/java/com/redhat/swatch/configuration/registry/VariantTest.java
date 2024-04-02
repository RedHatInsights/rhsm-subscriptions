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

import org.junit.jupiter.api.Test;

class VariantTest {

  @Test
  void sanityCheck() {
    assertTrue(true);
  }

  @Test
  void testFindByRole() {

    var variant = Variant.findByRole("Red Hat Enterprise Linux Server");

    var expected = "RHEL for x86";
    var actual = variant.get().getTag();

    assertEquals(expected, actual);
  }

  @Test
  void testGetParentSubscription() {
    var variant = Variant.findByRole("Red Hat Enterprise Linux Compute Node").get();
    var expected = "rhel-for-x86";
    var actual = variant.getSubscription().getId();

    assertEquals(expected, actual);
  }

  @Test
  void testFindByEngineeringId() {

    var actual = Variant.findByEngProductId("69");

    assertEquals("RHEL for x86", actual.get().getTag());
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
