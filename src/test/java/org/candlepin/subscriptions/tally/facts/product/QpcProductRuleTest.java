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
package org.candlepin.subscriptions.tally.facts.product;

import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createQpcHost;
import static org.candlepin.subscriptions.tally.facts.product.QpcProductRule.RHEL;
import static org.candlepin.subscriptions.tally.facts.product.QpcProductRule.RHEL_FOR_ARM;
import static org.candlepin.subscriptions.tally.facts.product.QpcProductRule.RHEL_FOR_IBM_POWER;
import static org.candlepin.subscriptions.tally.facts.product.QpcProductRule.RHEL_FOR_X86;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.candlepin.subscriptions.tally.facts.product.ProductRule.ProductRuleContext;
import org.hamcrest.Matchers;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QpcProductRuleTest {

  private final ProductRule rule = new QpcProductRule();

  @ParameterizedTest
  @MethodSource("provideArchCombinations")
  void testQpcSystemArchProduct(String arch, String expectedProduct) {
    var hostFacts = createQpcHost(RHEL, arch, OffsetDateTime.now(Clock.systemUTC()));
    boolean is3rdPartyMigrated = false;
    var skipRhsm = false;

    ProductRuleContext context = new ProductRuleContext(hostFacts, is3rdPartyMigrated, skipRhsm);

    assertTrue(rule.appliesTo(context));
    assertThat(rule.getFilteredProductTags(context), Matchers.hasItem(expectedProduct));
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
}
