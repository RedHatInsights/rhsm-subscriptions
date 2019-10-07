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
import org.candlepin.subscriptions.files.ProductWhitelist;
import org.candlepin.subscriptions.utilization.api.model.CandlepinPool;
import org.candlepin.subscriptions.utilization.api.model.CandlepinProductAttribute;
import org.candlepin.subscriptions.utilization.api.model.CandlepinProvidedProduct;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

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

        CandlepinPool pool = createTestPool();
        controller.updateCapacityForOrg("org", Collections.singletonList(pool));

        verifyZeroInteractions(mapper);
        verifyZeroInteractions(repository);
    }

    @Test
    void testSavesPoolsProvidedByMapper() {
        when(whitelist.productIdMatches(any())).thenReturn(true);
        SubscriptionCapacity capacity = new SubscriptionCapacity();
        when(mapper.mapPoolToSubscriptionCapacity(anyString(), any()))
            .thenReturn(Collections.singletonList(capacity));

        CandlepinPool pool = createTestPool();
        controller.updateCapacityForOrg("org", Collections.singletonList(pool));

        verify(repository).save(eq(capacity));
    }

    private CandlepinPool createTestPool() {
        CandlepinPool pool = new CandlepinPool();
        pool.setAccountNumber("account-1234");
        pool.setActiveSubscription(true);
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
