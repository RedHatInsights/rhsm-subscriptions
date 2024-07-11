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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.OffsetDateTime;
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

  ProductNormalizer productNormalizer = new ProductNormalizer();

  @ParameterizedTest
  @MethodSource("provideArchCombinations")
  void testQpcSystemArchProduct(String arch, String expectedProduct) {
    var inventoryHostFacts = createQpcHost("RHEL", arch, OffsetDateTime.now(Clock.systemUTC()));

    boolean is3rdPartyMigrated = false;

    var skipRhsm = false;
    assertThat(
        productNormalizer.normalizeProducts(inventoryHostFacts, is3rdPartyMigrated, skipRhsm),
        Matchers.hasItem(expectedProduct));
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

  @Test
  void testQpcProductIdFromEngId() {
    var host = createQpcHost("RHEL", "Test", OffsetDateTime.now(Clock.systemUTC()));
    host.setSystemProfileProductIds("69");

    boolean is3rdPartyMigrated = false;
    var skipRhsm = false;

    var actual = productNormalizer.normalizeProducts(host, is3rdPartyMigrated, skipRhsm);
    var expected = Set.of("RHEL Ungrouped", "RHEL for x86", "RHEL");
    assertEquals(expected, actual);
  }
}
