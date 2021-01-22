/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.util;

import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.files.ProductIdToProductsMapSource;
import org.candlepin.subscriptions.tally.InventoryAccountUsageCollectorTest;
import org.candlepin.subscriptions.tally.files.RoleToProductsMapSource;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Why are we doing this? Because when we use a MockBean annotation on the MapSources, we don't get
 * access to the mock until an @BeforeEach method. However, we need to mock the getValue() call
 * before that so the FactNormalizer gets a populated list when it is constructed. The solution is
 * to replace the bean definition of the MapSource with the ones below.
 */
@TestConfiguration
public class MockProductAndRoleConfiguration {

  @Bean
  @Primary
  public ProductIdToProductsMapSource testProductIdToProductsMapSource() throws IOException {
    Map<Integer, List<String>> productList = new HashMap<>();
    productList.put(
        InventoryAccountUsageCollectorTest.TEST_PRODUCT_ID,
        InventoryAccountUsageCollectorTest.RHEL_PRODUCTS);
    productList.put(
        InventoryAccountUsageCollectorTest.NON_RHEL_PRODUCT_ID,
        InventoryAccountUsageCollectorTest.NON_RHEL_PRODUCTS);

    ProductIdToProductsMapSource source = mock(ProductIdToProductsMapSource.class);
    when(source.getValue()).thenReturn(productList);
    return source;
  }

  @Bean
  @Primary
  public RoleToProductsMapSource testRoleToProducsMapSource() throws IOException {
    RoleToProductsMapSource source = mock(RoleToProductsMapSource.class);
    when(source.getValue()).thenReturn(Collections.emptyMap());
    return source;
  }
}
