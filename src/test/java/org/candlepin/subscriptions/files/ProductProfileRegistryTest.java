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

import static org.candlepin.subscriptions.db.model.Granularity.*;
import static org.candlepin.subscriptions.utilization.api.model.ProductId.*;
import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.utilization.api.model.ProductId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

class ProductProfileRegistryTest {

    private ProductProfileRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ProductProfileRegistry();

        Set<SubscriptionWatchProduct> ids1 = Set.of(
            makeId("1", Set.of(RHEL)),
            makeId("2", Set.of(RHEL, RHEL_COMPUTE_NODE)),
            makeId("3", Set.of(RHEL, RHEL_FOR_X86))
        );
        ProductProfile p1 = new ProductProfile("p1", ids1, DAILY);

        Set<SubscriptionWatchProduct> ids2 = Set.of(
            makeId("4", Set.of(SATELLITE)),
            makeId("5", Set.of(SATELLITE, SATELLITE_SERVER)),
            makeId("6", Set.of(SATELLITE, SATELLITE_CAPSULE))
        );
        ProductProfile p2 = new ProductProfile("p2", ids2, HOURLY);

        Set<SubscriptionWatchProduct> ids3 = Set.of(
            makeId("7", Set.of(OPENSHIFT_CONTAINER_PLATFORM))
        );
        ProductProfile p3 = new ProductProfile("p3", ids3, DAILY);

        registry.addProductProfile(p1);
        registry.addProductProfile(p2);
        registry.addProductProfile(p3);
    }

    SubscriptionWatchProduct makeId(String engineeringProductId, Set<ProductId> productIds) {
        SubscriptionWatchProduct productId = new SubscriptionWatchProduct();
        productId.setProduct(engineeringProductId);
        productId.setProductIds(productIds.stream().map(ProductId::toString).collect(Collectors.toSet()));
        return productId;
    }

    @Test
    void testFindProfileForProductId() {
        assertEquals("p1", registry.findProfileForProductId("RHEL").getName());
    }

    @Test
    void testFindProfileForProduct() {
        assertEquals("p2", registry.findProfileForProduct("4").getName());
    }

    @Test
    void testListProfiles() {
        Set<String> expected = Set.of("p1", "p2", "p3");
        Set<String> actual = registry.listProfiles();
        assertEquals(expected, actual);
    }

    @Test
    void testGetAllProductProfiles() {
        Set<ProductProfile> profiles = registry.getAllProductProfiles();
        assertEquals(3, profiles.size());
        assertEquals(Set.of(HOURLY, DAILY),
            profiles.stream().map(ProductProfile::getFinestGranularity).collect(Collectors.toSet()));
    }

    @Test
    void testReturnsDefault() {
        ProductProfile actual = registry.findProfileForProductId(RHEL_FOR_ARM);
        ProductProfile expected = ProductProfile.getDefault();
        assertEquals(actual, expected);
    }

    @Test
    void productsCanExistOnlyOnce() {
        ProductProfileRegistry r = new ProductProfileRegistry();

        String sameProduct = "1";
        Set<SubscriptionWatchProduct> ids1 = Set.of(
            makeId(sameProduct, Set.of(RHEL))
        );
        ProductProfile p1 = new ProductProfile("p1", ids1, DAILY);

        Set<SubscriptionWatchProduct> ids2 = new HashSet<>(Arrays.asList(
            makeId(sameProduct, Set.of(SATELLITE))
        ));
        ProductProfile p2 = new ProductProfile("p2", ids2, DAILY);

        r.addProductProfile(p1);
        assertThrows(IllegalStateException.class, () -> r.addProductProfile(p2));
    }

    @Test
    void productIdsCanExistOnlyOnce() {
        ProductProfileRegistry r = new ProductProfileRegistry();

        Set<ProductId> sameProductId = Set.of(RHEL);
        Set<SubscriptionWatchProduct> ids1 = Set.of(
            makeId("1", sameProductId)
        );
        ProductProfile p1 = new ProductProfile("p1", ids1, DAILY);

        Set<SubscriptionWatchProduct> ids2 = new HashSet<>(Arrays.asList(
            makeId("2", sameProductId)
        ));
        ProductProfile p2 = new ProductProfile("p2", ids2, DAILY);

        r.addProductProfile(p1);
        assertThrows(IllegalStateException.class, () -> r.addProductProfile(p2));
    }
}
