/*
 * Copyright (c) 2020 Red Hat, Inc.
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
package org.candlepin.subscriptions.files;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.db.model.Granularity;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

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
        p.setSyspurposeRoles(Set.of(
            new SyspurposeRole("ROLE_1", swatchProds1),
            new SyspurposeRole("ROLE_2", swatchProds2)
        ));
        Map<String, Set<String>> mapping = p.mapSwatchProductsByRole();
        assertTrue(mapping.containsKey("ROLE_1"));
        assertThat(mapping.get("ROLE_1"), Matchers.containsInAnyOrder(swatchProds1.toArray()));
        assertTrue(mapping.containsKey("ROLE_2"));
        assertThat(mapping.get("ROLE_2"), Matchers.containsInAnyOrder(swatchProds2.toArray()));
    }

    @Test
    void mapSwatchProductsByRoleHandlesNull() {
        ProductProfile p = new ProductProfile("test", Collections.emptySet(), Granularity.DAILY);
        Map<String, Set<String>> mapping = p.mapSwatchProductsByRole();
        assertTrue(mapping.isEmpty());
    }

    @Test
    void mapSwatchProductsByEngProducts() {
        Set<String> swatchProds1 = Set.of("SW_PROD_1", "SW_PROD_2");
        Set<String> swatchProds2 = Set.of("SW_PROD_1");

        Set<SubscriptionWatchProduct> products = Set.of(
            new SubscriptionWatchProduct("PROD_1", swatchProds1),
            new SubscriptionWatchProduct("PROD_2", swatchProds2)
        );

        ProductProfile p = new ProductProfile("test", products, Granularity.DAILY);

        Map<String, Set<String>> mapping = p.mapSwatchProductsByEngProducts();
        assertTrue(mapping.containsKey("PROD_1"));
        assertThat(mapping.get("PROD_1"), Matchers.containsInAnyOrder(swatchProds1.toArray()));
        assertTrue(mapping.containsKey("PROD_2"));
        assertThat(mapping.get("PROD_2"), Matchers.containsInAnyOrder(swatchProds2.toArray()));
    }

    @Test
    void mapSwatchProductsByEngProductsHandlesNull() {
        ProductProfile p = new ProductProfile("test", Collections.emptySet(), Granularity.DAILY);
        Map<String, Set<String>> mapping = p.mapSwatchProductsByEngProducts();
        assertTrue(mapping.isEmpty());
    }

}
