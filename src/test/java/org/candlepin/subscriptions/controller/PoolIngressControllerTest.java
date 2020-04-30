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
package org.candlepin.subscriptions.controller;

import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.capacity.CandlepinPoolCapacityMapper;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityKey;
import org.candlepin.subscriptions.files.ProductWhitelist;
import org.candlepin.subscriptions.utilization.api.model.CandlepinPool;
import org.candlepin.subscriptions.utilization.api.model.CandlepinProductAttribute;
import org.candlepin.subscriptions.utilization.api.model.CandlepinProvidedProduct;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.hamcrest.MockitoHamcrest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.Collections;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
class PoolIngressControllerTest {

    @Autowired
    PoolIngressController controller;

    @MockBean
    SubscriptionCapacityRepository repository;

    @MockBean
    CandlepinPoolCapacityMapper mapper;

    @MockBean
    ProductWhitelist whitelist;

    @Test
    void testNothingSavedIfFilteredByWhitelist() {
        when(whitelist.productIdMatches(any())).thenReturn(false);
        when(repository.findByKeyOwnerIdAndKeySubscriptionIdIn(anyString(), anyList()))
            .thenReturn(Collections.emptyList());

        CandlepinPool pool = createTestPool();
        controller.updateCapacityForOrg("org", Collections.singletonList(pool));

        verifyZeroInteractions(mapper);
        verify(repository).saveAll(eq(Collections.emptyList()));
    }

    @Test
    void testSavesPoolsProvidedByMapper() {
        when(whitelist.productIdMatches(any())).thenReturn(true);
        when(repository.findByKeyOwnerIdAndKeySubscriptionIdIn(anyString(), anyList()))
            .thenReturn(Collections.emptyList());
        SubscriptionCapacity capacity = new SubscriptionCapacity();
        when(mapper.mapPoolToSubscriptionCapacity(anyString(), any(), eq(Collections.emptyMap())))
            .thenReturn(Collections.singletonList(capacity));

        CandlepinPool pool = createTestPool();
        controller.updateCapacityForOrg("org", Collections.singletonList(pool));

        verify(repository).saveAll(eq(Collections.singletonList(capacity)));
    }

    @Test
    void testRemovesExistingCapacityRecordsIfNoLongerNeeded() {
        SubscriptionCapacity stale1 = createCapacity("owner", "RHEL");
        SubscriptionCapacity stale2 = createCapacity("owner", "RHEL Workstation");
        SubscriptionCapacity expected = createCapacity("owner", "OpenShift Container Platform");

        when(whitelist.productIdMatches(any())).thenReturn(true);
        when(repository.findByKeyOwnerIdAndKeySubscriptionIdIn(anyString(), anyList()))
            .thenReturn(Arrays.asList(stale1, stale2, expected));
        when(mapper.mapPoolToSubscriptionCapacity(anyString(), any(CandlepinPool.class), anyMap()))
            .thenReturn(Collections.singletonList(expected));

        CandlepinPool pool = createTestPool();
        controller.updateCapacityForOrg("org", Collections.singletonList(pool));

        verify(repository).saveAll(eq(Collections.singletonList(expected)));
        verify(repository).deleteAll(MockitoHamcrest.argThat(Matchers.containsInAnyOrder(stale1,
            stale2)));
    }

    @Test
    void testRemovesAllCapacityRecordsIfSkuIsFiltered() {
        SubscriptionCapacity stale1 = createCapacity("owner", "RHEL");
        SubscriptionCapacity stale2 = createCapacity("owner", "RHEL Workstation");

        when(whitelist.productIdMatches(anyString())).thenReturn(false);
        when(repository.findByKeyOwnerIdAndKeySubscriptionIdIn(anyString(), anyList()))
            .thenReturn(Arrays.asList(stale1, stale2));
        when(mapper.mapPoolToSubscriptionCapacity(anyString(), any(CandlepinPool.class), anyMap()))
            .thenReturn(Arrays.asList(stale1, stale2));

        CandlepinPool pool = createTestPool();
        controller.updateCapacityForOrg("org", Collections.singletonList(pool));

        verify(repository).saveAll(eq(Collections.emptyList()));
        verify(repository).deleteAll(MockitoHamcrest.argThat(Matchers.containsInAnyOrder(stale1,
            stale2)));
    }

    private SubscriptionCapacity createCapacity(String owner, String product) {
        SubscriptionCapacityKey key = new SubscriptionCapacityKey();
        key.setOwnerId(owner);
        key.setProductId(product);
        key.setSubscriptionId("12345");
        SubscriptionCapacity capacity = new SubscriptionCapacity();
        capacity.setKey(key);
        return capacity;
    }

    private CandlepinPool createTestPool() {
        CandlepinPool pool = new CandlepinPool();
        pool.setAccountNumber("account-1234");
        pool.setActiveSubscription(true);
        pool.setSubscriptionId("12345");
        CandlepinProvidedProduct providedProduct = new CandlepinProvidedProduct();
        providedProduct.setProductId("product-1");
        pool.setProvidedProducts(Collections.singletonList(providedProduct));
        pool.setQuantity(4L);
        CandlepinProductAttribute socketAttribute = new CandlepinProductAttribute();
        socketAttribute.setName("sockets");
        socketAttribute.setValue("4");
        pool.setProductAttributes(Collections.singletonList(socketAttribute));
        return pool;
    }
}

