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
package org.candlepin.subscriptions.db.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class OfferingTest {

    @Test
    void veriftySimpleGetSetTest() {
        final List<String> skus = Arrays.asList("childsku1", "childsku2");
        final List<Integer> productIds = Arrays.asList(1, 2);
        final Offering offering = new Offering();
        offering.setSku("testsku");
        offering.setChildSkus(skus);
        offering.setProductIds(productIds);
        offering.setUsage(Usage.DEVELOPMENT_TEST);
        offering.setServiceLevel(ServiceLevel.PREMIUM);
        offering.setRole("testrole");
        offering.setEntitlementQuantity(1);
        offering.setPhysicalCores(2);
        offering.setPhysicalSockets(3);
        offering.setVirtualCores(4);
        offering.setVirtualSockets(5);
        offering.setProductFamily("testproductfamily");
        offering.setProductName("testproductname");
        assertEquals("testsku", offering.getSku());
        assertArrayEquals(skus.toArray(), offering.getChildSkus().toArray());
        assertArrayEquals(productIds.toArray(), offering.getProductIds().toArray());
        assertEquals(Usage.DEVELOPMENT_TEST, offering.getUsage());
        assertEquals(ServiceLevel.PREMIUM, offering.getServiceLevel());
        assertEquals("testrole", offering.getRole());
        assertEquals(1, offering.getEntitlementQuantity());
        assertEquals(2, offering.getPhysicalCores());
        assertEquals(3, offering.getPhysicalSockets());
        assertEquals(4, offering.getVirtualCores());
        assertEquals(5, offering.getVirtualSockets());
        assertEquals("testproductfamily", offering.getProductFamily());
        assertEquals("testproductname", offering.getProductName());
    }
}
