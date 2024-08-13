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
package com.redhat.swatch.contract.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ProductDenylistTest {
  public static final String TEST_DENYLIST = "classpath:test-product-denylist.txt";

  public static Stream<String> getSkusWithSuffixes() {
    return ProductDenylist.SUFFIXES.stream().map(suffix -> "I1" + suffix);
  }

  @Test
  void loadSetFromClasspath() throws Exception {
    Set<String> expected = Set.of("I1", "I2", "I3");
    Set<String> actual = ProductDenylist.loadSet(TEST_DENYLIST);
    assertEquals(expected, actual);
  }

  @Test
  void loadSetFromFile() throws Exception {
    File f = File.createTempFile("test-denylist", ".txt");
    f.deleteOnExit();

    try (Writer w = new FileWriter(f)) {
      w.write("I1\n");
      w.write("I2\n");
      w.write("I3\n");
    }

    Set<String> expected = Set.of("I1", "I2", "I3");
    Set<String> actual = ProductDenylist.loadSet("file:" + f.getAbsolutePath());
    assertEquals(expected, actual);

    actual = ProductDenylist.loadSet(f.getAbsolutePath());
    assertEquals(expected, actual);
  }

  @Test
  void testUnspecifiedLocationAllowsArbitraryProducts() {
    ProductDenylist denylist = new ProductDenylist(Optional.empty());
    assertFalse(denylist.productIdMatches("hello"));
  }

  @Test
  void testAllowsProductsUnSpecifiedInDenylist() {
    ProductDenylist denylist = new ProductDenylist(Optional.of(TEST_DENYLIST));
    assertTrue(denylist.productIdMatches("I1"));
    assertTrue(denylist.productIdMatches("I2"));
    assertTrue(denylist.productIdMatches("I3"));
    assertFalse(denylist.productIdMatches("I111"));
    assertFalse(denylist.productIdMatches("I112"));
    assertFalse(denylist.productIdMatches(null));
  }

  @ParameterizedTest
  @MethodSource("getSkusWithSuffixes")
  void testConsidersAllSuffixes(String sku) {
    ProductDenylist denylist = new ProductDenylist(Optional.of(TEST_DENYLIST));
    assertTrue(denylist.productIdMatches(sku));
  }

  @Test
  void allProductsIsImmutable() {
    ProductDenylist denylist = new ProductDenylist(Optional.of(TEST_DENYLIST));
    Set<String> allProducts = denylist.allProducts();

    assertThrows(UnsupportedOperationException.class, () -> allProducts.add("hello"));
  }
}
