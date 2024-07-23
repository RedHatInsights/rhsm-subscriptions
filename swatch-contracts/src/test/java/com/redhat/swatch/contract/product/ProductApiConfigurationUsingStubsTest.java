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
package com.redhat.swatch.contract.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.clients.product.ProductClient;
import com.redhat.swatch.clients.product.api.resources.ApiException;
import com.redhat.swatch.clients.product.api.resources.ProductApi;
import com.redhat.swatch.contract.test.resources.ProductUseStubService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(ProductUseStubService.class)
class ProductApiConfigurationUsingStubsTest {
  private static final String SKU = "SKU00790";

  @ProductClient ProductApi productApi;

  @Test
  void testGetEngineeringProductsForSkusToUseStubResources() throws ApiException {
    var actual = productApi.getEngineeringProductsForSkus(SKU);

    assertNotNull(actual);
    assertNotNull(actual.getEntries());
    assertFalse(actual.getEntries().isEmpty());
    var product = actual.getEntries().get(0);
    assertEquals(SKU, product.getSku());
    assertNotNull(product.getEngProducts());
    assertNotNull(product.getEngProducts().getEngProducts());
    assertEquals(2, product.getEngProducts().getEngProducts().size());
    assertEquals(290, product.getEngProducts().getEngProducts().get(0).getOid());
    assertEquals(70, product.getEngProducts().getEngProducts().get(1).getOid());
  }

  @Test
  void testGetProductTreeToUseStubResources() throws ApiException {
    var actual = productApi.getProductTree(SKU, true);

    assertNotNull(actual);
    assertNotNull(actual.getProducts());
    assertFalse(actual.getProducts().isEmpty());
    var product = actual.getProducts().get(0);
    assertEquals(SKU, product.getSku());
    assertNotNull(product.getDescription());
    assertNotNull(product.getStatus());
    assertTrue(
        product.getAttributes().stream()
            .anyMatch(
                a ->
                    "PRODUCT_FAMILY".equals(a.getCode())
                        && "OpenShift Enterprise".equals(a.getValue())));
  }
}
