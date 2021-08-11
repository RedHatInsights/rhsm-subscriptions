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
package org.candlepin.subscriptions.registry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.candlepin.subscriptions.db.model.Granularity;
import org.junit.jupiter.api.Test;

class ProductProfileTest {
  @Test
  void testGranularityComparisonPasses() {
    ProductProfile p = new ProductProfile("test", Collections.emptySet(), Granularity.DAILY);

    assertFalse(p.supportsGranularity(Granularity.HOURLY));

    assertTrue(p.supportsGranularity(Granularity.DAILY));
    assertTrue(p.supportsGranularity(Granularity.WEEKLY));
    assertTrue(p.supportsGranularity(Granularity.MONTHLY));
    assertTrue(p.supportsGranularity(Granularity.QUARTERLY));
    assertTrue(p.supportsGranularity(Granularity.YEARLY));
  }

  @Test
  void mapSwatchProductsByRoleTest() {
    ProductProfile p = new ProductProfile("test", Collections.emptySet(), Granularity.DAILY);
    Set<String> swatchProds1 = Set.of("SW_PROD_1", "SW_PROD_2");
    Set<String> swatchProds2 = Set.of("SW_PROD_1");
    p.setSyspurposeRoles(
        Set.of(
            new SyspurposeRole("ROLE_1", swatchProds1),
            new SyspurposeRole("ROLE_2", swatchProds2)));

    Map<String, Set<String>> mapping = p.getSwatchProductsByRoles();
    assertTrue(mapping.containsKey("ROLE_1"));
    assertThat(mapping.get("ROLE_1"), containsInAnyOrder(swatchProds1.toArray()));
    assertTrue(mapping.containsKey("ROLE_2"));
    assertThat(mapping.get("ROLE_2"), containsInAnyOrder(swatchProds2.toArray()));
  }

  @Test
  void mapRolesBySwatchProductsTest() {
    ProductProfile p = new ProductProfile("test", Collections.emptySet(), Granularity.DAILY);
    Set<String> swatchProds1 = Set.of("SW_PROD_1", "SW_PROD_2");
    Set<String> swatchProds2 = Set.of("SW_PROD_1");
    Set<String> swatchProds3 = Set.of("SW_PROD_2", "SW_PROD_3");
    Set<String> swatchProds4 =
        Set.of("SW_PROD_2", "SW_PROD_3"); // intentional duplicate of swatchProds3
    p.setSyspurposeRoles(
        Set.of(
            new SyspurposeRole("ROLE_1", swatchProds1),
            new SyspurposeRole("ROLE_2", swatchProds2),
            new SyspurposeRole("ROLE_3", swatchProds3),
            new SyspurposeRole("ROLE_4", swatchProds4)));

    Map<String, Set<String>> mapping = p.getRolesBySwatchProduct();
    assertAll(
        "Contains the right keys",
        () -> {
          assertThat(mapping, hasKey("SW_PROD_1"));
          assertThat(mapping, hasKey("SW_PROD_2"));
          assertThat(mapping, hasKey("SW_PROD_3"));
        });

    assertAll(
        "Contains the right values",
        () -> {
          assertThat(mapping.get("SW_PROD_1"), containsInAnyOrder("ROLE_1", "ROLE_2"));
          assertThat(mapping.get("SW_PROD_2"), containsInAnyOrder("ROLE_1", "ROLE_3", "ROLE_4"));
          assertThat(mapping.get("SW_PROD_3"), containsInAnyOrder("ROLE_3", "ROLE_4"));
        });
  }

  @Test
  void mapSwatchProductsByRoleHandlesEmptySet() {
    ProductProfile profile1 = new ProductProfile("test", Collections.emptySet(), Granularity.DAILY);
    assertTrue(profile1.getSwatchProductsByRoles().isEmpty());

    ProductProfile profile2 = new ProductProfile();
    assertTrue(profile2.getSwatchProductsByRoles().isEmpty());
  }

  @Test
  void mapSwatchProductsByEngProducts() {
    Set<String> swatchProds1 = Set.of("SW_PROD_1", "SW_PROD_2");
    Set<String> swatchProds2 = Set.of("SW_PROD_1");

    Set<SubscriptionWatchProduct> products =
        Set.of(
            new SubscriptionWatchProduct("PROD_1", swatchProds1),
            new SubscriptionWatchProduct("PROD_2", swatchProds2));

    ProductProfile profile1 = new ProductProfile("test", products, Granularity.DAILY);
    Map<String, Set<String>> mapping = profile1.getSwatchProductsByEngProducts();
    assertTrue(mapping.containsKey("PROD_1"));
    assertThat(mapping.get("PROD_1"), containsInAnyOrder(swatchProds1.toArray()));
    assertTrue(mapping.containsKey("PROD_2"));
    assertThat(mapping.get("PROD_2"), containsInAnyOrder(swatchProds2.toArray()));

    ProductProfile profile2 = new ProductProfile();
    profile2.setProducts(products);
    Map<String, Set<String>> mapping2 = profile2.getSwatchProductsByEngProducts();
    assertTrue(mapping2.containsKey("PROD_1"));
    assertThat(mapping2.get("PROD_1"), containsInAnyOrder(swatchProds1.toArray()));
    assertTrue(mapping2.containsKey("PROD_2"));
    assertThat(mapping2.get("PROD_2"), containsInAnyOrder(swatchProds2.toArray()));

    assertEquals(mapping, mapping2);
  }

  @Test
  void mapSwatchProductsByEngProductsHandlesEmptySet() {
    ProductProfile profile1 = new ProductProfile("test", Collections.emptySet(), Granularity.DAILY);
    assertTrue(profile1.getSwatchProductsByEngProducts().isEmpty());

    ProductProfile profile2 = new ProductProfile();
    assertTrue(profile2.getSwatchProductsByEngProducts().isEmpty());
  }
}
