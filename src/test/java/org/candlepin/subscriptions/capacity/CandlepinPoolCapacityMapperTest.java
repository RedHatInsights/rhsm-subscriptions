/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.capacity;

import static org.hamcrest.MatcherAssert.*;

import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.utilization.api.model.CandlepinPool;
import org.candlepin.subscriptions.utilization.api.model.CandlepinProductAttribute;
import org.candlepin.subscriptions.utilization.api.model.CandlepinProvidedProduct;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CandlepinPoolCapacityMapperTest {

    private static final OffsetDateTime LONG_AGO = OffsetDateTime.ofInstant(Instant.EPOCH,
        ZoneId.systemDefault());
    private static final OffsetDateTime NOWISH = OffsetDateTime.of(2019, 06, 23, 00, 00, 00, 00,
        ZoneOffset.UTC);

    CapacityProductExtractor productExtractor;

    CandlepinPoolCapacityMapper mapper;

    private static Set<String> physicalProducts = new HashSet<>(Arrays.asList("RHEL", "RHEL Server"));
    private static List<String> physicalProductIds = Arrays.asList("1", "2");
    private static Set<String> virtualProducts = new HashSet<>(Arrays.asList("RHEL", "RHEL Workstation"));
    private static List<String> virtualProductIds = Arrays.asList("3", "4");

    @BeforeAll
    void setup() {
        productExtractor = Mockito.mock(CapacityProductExtractor.class);

        Mockito.when(productExtractor.getProducts(Mockito.eq(physicalProductIds)))
            .thenReturn(physicalProducts);

        Mockito.when(productExtractor.getProducts(Mockito.eq(virtualProductIds)))
            .thenReturn(virtualProducts);

        mapper = new CandlepinPoolCapacityMapper(productExtractor);
    }

    @Test
    void testPoolWithPhysicalProductsProvidesCapacity() {
        CandlepinPool pool = createTestPool(physicalProductIds, null);

        Collection<SubscriptionCapacity> capacities = mapper.mapPoolToSubscriptionCapacity("ownerId",
            pool);

        SubscriptionCapacity expectedRhelCapacity = new SubscriptionCapacity();
        expectedRhelCapacity.setProductId("RHEL");
        expectedRhelCapacity.setPhysicalSockets(20);
        expectedRhelCapacity.setPhysicalCores(40);
        expectedRhelCapacity.setAccountNumber("account");
        expectedRhelCapacity.setBeginDate(LONG_AGO);
        expectedRhelCapacity.setEndDate(NOWISH);
        expectedRhelCapacity.setSubscriptionId("subId");
        expectedRhelCapacity.setOwnerId("ownerId");

        SubscriptionCapacity expectedServerCapacity = new SubscriptionCapacity();
        expectedServerCapacity.setProductId("RHEL Server");
        expectedServerCapacity.setPhysicalSockets(20);
        expectedServerCapacity.setPhysicalCores(40);
        expectedServerCapacity.setAccountNumber("account");
        expectedServerCapacity.setBeginDate(LONG_AGO);
        expectedServerCapacity.setEndDate(NOWISH);
        expectedServerCapacity.setSubscriptionId("subId");
        expectedServerCapacity.setOwnerId("ownerId");

        assertThat(capacities, Matchers.containsInAnyOrder(
            expectedRhelCapacity,
            expectedServerCapacity
        ));
    }

    @Test
    void testQuantityIsDividedByInstanceMultiplier() {
        CandlepinPool pool = createTestPool(physicalProductIds, null, 2);

        Collection<SubscriptionCapacity> capacities = mapper.mapPoolToSubscriptionCapacity("ownerId",
            pool);

        SubscriptionCapacity expectedRhelCapacity = new SubscriptionCapacity();
        expectedRhelCapacity.setProductId("RHEL");
        expectedRhelCapacity.setPhysicalSockets(10);
        expectedRhelCapacity.setPhysicalCores(20);
        expectedRhelCapacity.setAccountNumber("account");
        expectedRhelCapacity.setBeginDate(LONG_AGO);
        expectedRhelCapacity.setEndDate(NOWISH);
        expectedRhelCapacity.setSubscriptionId("subId");
        expectedRhelCapacity.setOwnerId("ownerId");

        SubscriptionCapacity expectedServerCapacity = new SubscriptionCapacity();
        expectedServerCapacity.setProductId("RHEL Server");
        expectedServerCapacity.setPhysicalSockets(10);
        expectedServerCapacity.setPhysicalCores(20);
        expectedServerCapacity.setAccountNumber("account");
        expectedServerCapacity.setBeginDate(LONG_AGO);
        expectedServerCapacity.setEndDate(NOWISH);
        expectedServerCapacity.setSubscriptionId("subId");
        expectedServerCapacity.setOwnerId("ownerId");

        assertThat(capacities, Matchers.containsInAnyOrder(
            expectedRhelCapacity,
            expectedServerCapacity
        ));
    }

    @Test
    void testPoolWithVirtualProductsProvidesCapacity() {
        CandlepinPool pool = createTestPool(null, virtualProductIds);

        Collection<SubscriptionCapacity> capacities = mapper.mapPoolToSubscriptionCapacity("ownerId",
            pool);

        SubscriptionCapacity expectedRhelCapacity = new SubscriptionCapacity();
        expectedRhelCapacity.setProductId("RHEL");
        expectedRhelCapacity.setVirtualSockets(20);
        expectedRhelCapacity.setVirtualCores(40);
        expectedRhelCapacity.setAccountNumber("account");
        expectedRhelCapacity.setBeginDate(LONG_AGO);
        expectedRhelCapacity.setEndDate(NOWISH);
        expectedRhelCapacity.setSubscriptionId("subId");
        expectedRhelCapacity.setOwnerId("ownerId");

        SubscriptionCapacity expectedWorkstationCapacity = new SubscriptionCapacity();
        expectedWorkstationCapacity.setProductId("RHEL Workstation");
        expectedWorkstationCapacity.setVirtualSockets(20);
        expectedWorkstationCapacity.setVirtualCores(40);
        expectedWorkstationCapacity.setAccountNumber("account");
        expectedWorkstationCapacity.setBeginDate(LONG_AGO);
        expectedWorkstationCapacity.setEndDate(NOWISH);
        expectedWorkstationCapacity.setSubscriptionId("subId");
        expectedWorkstationCapacity.setOwnerId("ownerId");

        assertThat(capacities, Matchers.containsInAnyOrder(
            expectedRhelCapacity,
            expectedWorkstationCapacity
        ));
    }

    @Test
    void testPoolWithBothPhysicalAndVirtualProductsProvidesCapacity() {
        CandlepinPool pool = createTestPool(physicalProductIds, virtualProductIds);

        Collection<SubscriptionCapacity> capacities = mapper.mapPoolToSubscriptionCapacity("ownerId",
            pool);

        SubscriptionCapacity expectedRhelCapacity = new SubscriptionCapacity();
        expectedRhelCapacity.setProductId("RHEL");
        expectedRhelCapacity.setPhysicalSockets(20);
        expectedRhelCapacity.setVirtualSockets(20);
        expectedRhelCapacity.setPhysicalCores(40);
        expectedRhelCapacity.setVirtualCores(40);
        expectedRhelCapacity.setAccountNumber("account");
        expectedRhelCapacity.setBeginDate(LONG_AGO);
        expectedRhelCapacity.setEndDate(NOWISH);
        expectedRhelCapacity.setSubscriptionId("subId");
        expectedRhelCapacity.setOwnerId("ownerId");

        SubscriptionCapacity expectedWorkstationCapacity = new SubscriptionCapacity();
        expectedWorkstationCapacity.setProductId("RHEL Workstation");
        expectedWorkstationCapacity.setVirtualSockets(20);
        expectedWorkstationCapacity.setVirtualCores(40);
        expectedWorkstationCapacity.setAccountNumber("account");
        expectedWorkstationCapacity.setBeginDate(LONG_AGO);
        expectedWorkstationCapacity.setEndDate(NOWISH);
        expectedWorkstationCapacity.setSubscriptionId("subId");
        expectedWorkstationCapacity.setOwnerId("ownerId");

        SubscriptionCapacity expectedServerCapacity = new SubscriptionCapacity();
        expectedServerCapacity.setProductId("RHEL Server");
        expectedServerCapacity.setPhysicalSockets(20);
        expectedServerCapacity.setPhysicalCores(40);
        expectedServerCapacity.setAccountNumber("account");
        expectedServerCapacity.setBeginDate(LONG_AGO);
        expectedServerCapacity.setEndDate(NOWISH);
        expectedServerCapacity.setSubscriptionId("subId");
        expectedServerCapacity.setOwnerId("ownerId");

        assertThat(capacities, Matchers.containsInAnyOrder(
            expectedRhelCapacity,
            expectedServerCapacity,
            expectedWorkstationCapacity
        ));
    }

    private CandlepinPool createTestPool(List<String> physicalProductIds, List<String> derivedProductIds) {
        return createTestPool(physicalProductIds, derivedProductIds, null);
    }

    private CandlepinPool createTestPool(List<String> physicalProductIds, List<String> derivedProductIds,
        Integer instanceMultiplier) {
        CandlepinPool pool = new CandlepinPool();
        pool.setAccountNumber("account");
        pool.setStartDate(LONG_AGO);
        pool.setEndDate(NOWISH);
        pool.setQuantity(10L);
        pool.setSubscriptionId("subId");
        if (physicalProductIds != null) {
            pool.setProvidedProducts(
                physicalProductIds.stream().map(id -> new CandlepinProvidedProduct().productId(id))
                .collect(Collectors.toList()));
        }
        if (derivedProductIds != null) {
            pool.setDerivedProvidedProducts(
                derivedProductIds.stream().map(id -> new CandlepinProvidedProduct().productId(id))
                .collect(Collectors.toList()));
        }

        List<CandlepinProductAttribute> attributes = new ArrayList<>(Arrays.asList(
            new CandlepinProductAttribute().name("sockets").value("2"),
            new CandlepinProductAttribute().name("cores").value("4")
        ));
        if (instanceMultiplier != null) {
            attributes.add(new CandlepinProductAttribute()
                .name("instance_multiplier")
                .value(instanceMultiplier.toString())
            );
        }
        pool.setProductAttributes(attributes);
        return pool;
    }
}
