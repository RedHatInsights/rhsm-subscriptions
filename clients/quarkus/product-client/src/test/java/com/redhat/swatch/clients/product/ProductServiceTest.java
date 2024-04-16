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
package com.redhat.swatch.clients.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.product.api.model.EngineeringProduct;
import com.redhat.swatch.clients.product.api.model.EngineeringProductMap;
import com.redhat.swatch.clients.product.api.model.EngineeringProducts;
import com.redhat.swatch.clients.product.api.model.RESTProductTree;
import com.redhat.swatch.clients.product.api.model.SkuEngProduct;
import com.redhat.swatch.clients.product.api.resources.ApiException;
import com.redhat.swatch.clients.product.api.resources.ProductApi;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock ProductApi productApi;
  ProductService subject;

  @BeforeEach
  void setup() {
    subject = new ProductService(productApi);
  }

  @Test
  void testGetTree() throws ApiException {
    // The real http api would return a tree with info, but we'll return a simple non-null tree.
    RESTProductTree expectedTree = new RESTProductTree();
    when(productApi.getProductTree(anyString(), anyBoolean())).thenReturn(expectedTree);

    String sku = "RH00003";
    Optional<RESTProductTree> actual = subject.getTree(sku);

    verify(productApi).getProductTree(sku, Boolean.TRUE);
    assertTrue(actual.isPresent(), "Should have a product tree.");
    assertEquals(expectedTree, actual.get());
  }

  @Test
  void testGetTreeWhenNotFound() throws ApiException {
    // The real http api returns a 204 status and no body when the sku is not found.
    when(productApi.getProductTree(anyString(), anyBoolean())).thenReturn(null);

    String sku = "bogus";
    Optional<RESTProductTree> actual = subject.getTree(sku);

    verify(productApi).getProductTree(sku, Boolean.TRUE);
    assertTrue(actual.isEmpty(), "Should not have a product tree.");
  }

  @Test
  void testGetEngineeringProductsForSku() throws ApiException {
    // The real http api returns more eng prods for this SKU, but one will suffice.
    String sku = "SVCRH00003";
    EngineeringProduct actualEngProd = new EngineeringProduct().oid(588);
    EngineeringProductMap productMap = new EngineeringProductMap();
    SkuEngProduct engProds =
        new SkuEngProduct()
            .sku(sku)
            .engProducts(new EngineeringProducts().addEngProductsItem(actualEngProd));
    productMap.addEntriesItem(engProds);
    when(productApi.getEngineeringProductsForSkus(anyString())).thenReturn(productMap);

    List<EngineeringProduct> actualEngProds = subject.getEngineeringProductsForSku(sku);

    verify(productApi).getEngineeringProductsForSkus(sku);
    assertEquals(
        engProds.getEngProducts().getEngProducts(),
        actualEngProds,
        "EngProds from service do not match engProds from http api.");
  }

  @Test
  void testGetEngineeringProductsForSkuWhenNotFound() throws ApiException {
    // When the sku isn't found the real http api still returns a 200 and a product map with the
    // sku entry containing an empty eng prods array, which is replicated here.
    String sku = "bogus";
    EngineeringProductMap productMap = new EngineeringProductMap();
    SkuEngProduct engProds =
        new SkuEngProduct()
            .sku(sku)
            .engProducts(new EngineeringProducts().engProducts(Collections.emptyList()));
    productMap.addEntriesItem(engProds);
    when(productApi.getEngineeringProductsForSkus(anyString())).thenReturn(productMap);

    List<EngineeringProduct> actualEngProds = subject.getEngineeringProductsForSku(sku);

    verify(productApi).getEngineeringProductsForSkus(sku);
    assertTrue(
        actualEngProds.isEmpty(),
        "Requesting eng products for a bogus sku should return an empty list.");
  }

  @Test
  void testGetEngineeringProductsForSkus() throws ApiException {
    // The real http api returns more eng prods for these SKUs, but one each will suffice.
    EngineeringProductMap productMap = new EngineeringProductMap();

    String sku1 = "SVCRH00003";
    EngineeringProduct actualEngProd1 = new EngineeringProduct().oid(588);
    SkuEngProduct engProds1 =
        new SkuEngProduct()
            .sku(sku1)
            .engProducts(new EngineeringProducts().addEngProductsItem(actualEngProd1));
    productMap.addEntriesItem(engProds1);

    String sku2 = "SVCRH00009";
    EngineeringProduct actualEngProd2 = new EngineeringProduct().oid(273);
    SkuEngProduct engProds2 =
        new SkuEngProduct()
            .sku(sku2)
            .engProducts(new EngineeringProducts().addEngProductsItem(actualEngProd2));
    productMap.addEntriesItem(engProds2);

    when(productApi.getEngineeringProductsForSkus(anyString())).thenReturn(productMap);

    Collection<String> skus = Arrays.asList(sku1, sku2);
    Map<String, List<EngineeringProduct>> actualEngProds =
        subject.getEngineeringProductsForSkus(skus);

    // The actual http call is the list of skus joined with commas
    verify(productApi).getEngineeringProductsForSkus(String.join(",", skus));
    assertEquals(2, actualEngProds.size(), "Asked for two skus, should have got two entries back.");
    assertEquals(
        engProds1.getEngProducts().getEngProducts(),
        actualEngProds.get(sku1),
        sku1 + " doesn't match expected engProds from http api.");
    assertEquals(
        engProds2.getEngProducts().getEngProducts(),
        actualEngProds.get(sku2),
        sku2 + " doesn't match expected engProds from http api.");
  }

  @Test
  void testGetEngineeringProductsForSkusWhenNotFound() throws ApiException {
    // When requesting multiple skus, for any sku that isn't found, the real http api still
    // returns a 200 and a product map. Each unfound sku will have an entry wit an empty eng
    // prods array, which is replicated here.
    EngineeringProductMap productMap = new EngineeringProductMap();

    String sku1 = "foo";
    SkuEngProduct engProds1 =
        new SkuEngProduct()
            .sku(sku1)
            .engProducts(new EngineeringProducts().engProducts(Collections.emptyList()));
    productMap.addEntriesItem(engProds1);

    String sku2 = "bar";
    SkuEngProduct engProds2 =
        new SkuEngProduct()
            .sku(sku2)
            .engProducts(new EngineeringProducts().engProducts(Collections.emptyList()));
    productMap.addEntriesItem(engProds2);

    when(productApi.getEngineeringProductsForSkus(anyString())).thenReturn(productMap);

    Collection<String> skus = Arrays.asList(sku1, sku2);
    Map<String, List<EngineeringProduct>> actualEngProds =
        subject.getEngineeringProductsForSkus(skus);

    // The actual http call is the list of skus joined with commas
    verify(productApi).getEngineeringProductsForSkus(String.join(",", skus));
    assertEquals(
        2,
        actualEngProds.size(),
        "Asked for two skus, should have got two entries back, even if they weren't found.");
    assertTrue(
        actualEngProds.get(sku1).isEmpty(),
        sku1 + " should have an empty eng prods list because it wasn't found.");
    assertTrue(
        actualEngProds.get(sku2).isEmpty(),
        sku2 + " should have an empty eng prods list because it wasn't found.");
  }
}
