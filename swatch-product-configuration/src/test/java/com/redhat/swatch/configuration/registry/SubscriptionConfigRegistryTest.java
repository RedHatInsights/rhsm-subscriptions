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

import java.io.IOException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubscriptionConfigRegistryTest {

  SubscriptionConfigRegistry subscriptionConfigRegistry;

  @BeforeEach
  void setUp() throws IOException {
    subscriptionConfigRegistry = new SubscriptionConfigRegistry();
  }

  @Test
  void sanityCheck() {
    assertTrue(true);
  }

  @Test
  void testLoadAllTheThings() throws IOException {

    var actual = subscriptionConfigRegistry.getSubscriptions().size();
    var expected = 14;

    assertEquals(actual, expected);
  }

  @SneakyThrows
  @Test
  void testVariantEngIdLookup() {
    var satelliteCapsule = subscriptionConfigRegistry.findSubscriptionByVariantEngId("269");

    var expected = "Satellite Capsule";
    var actual = satelliteCapsule.get().getId();

    assertEquals(expected, actual);
  }

  @SneakyThrows
  @Test
  void testVariantProductNameLookup() {
    var openshiftContainerPlatform =
        subscriptionConfigRegistry.findSubscriptionByOfferingProductName(
            "OpenShift Container Platform");

    var expected = "OpenShift-metrics";
    var actual = openshiftContainerPlatform.get().getId();

    assertEquals(expected, actual);
  }

  @SneakyThrows
  @Test
  void testVariantRoleLookup() {
    var rosa = subscriptionConfigRegistry.findSubscriptionByRole("moa-hostedcontrolplane");

    var expected = "rosa";
    var actual = rosa.get().getId();

    assertEquals(expected, actual);
  }
}
