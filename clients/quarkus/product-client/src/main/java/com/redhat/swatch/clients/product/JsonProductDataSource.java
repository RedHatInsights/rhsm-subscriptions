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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.clients.product.api.model.EngineeringProduct;
import com.redhat.swatch.clients.product.api.model.EngineeringProductMap;
import com.redhat.swatch.clients.product.api.model.OperationalProduct;
import com.redhat.swatch.clients.product.api.model.RESTProductTree;
import com.redhat.swatch.clients.product.api.model.SkuEngProduct;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Data source for product data that uses static JSON. */
public class JsonProductDataSource implements ProductDataSource {
  private final Map<String, RESTProductTree> productTreeMap;
  private final Map<String, RESTProductTree> derivedProductTreeMap;
  private final Map<String, List<EngineeringProduct>> engineeringProductsMap;

  public JsonProductDataSource(
      ObjectMapper objectMapper,
      String offeringsJsonArray,
      String derivedSkuDataJsonArray,
      String engProdJsonArray) {
    try {
      RESTProductTree[] productTrees =
          objectMapper.readValue(offeringsJsonArray, RESTProductTree[].class);
      productTreeMap =
          Arrays.stream(productTrees)
              .collect(Collectors.toMap(JsonProductDataSource::findSku, Function.identity()));

      RESTProductTree[] derivedSkuTrees =
          objectMapper.readValue(derivedSkuDataJsonArray, RESTProductTree[].class);
      derivedProductTreeMap =
          Arrays.stream(derivedSkuTrees)
              .collect(Collectors.toMap(JsonProductDataSource::findSku, Function.identity()));
      EngineeringProductMap[] engineeringProductMaps =
          objectMapper.readValue(engProdJsonArray, EngineeringProductMap[].class);
      engineeringProductsMap =
          Arrays.stream(engineeringProductMaps)
              .map(EngineeringProductMap::getEntries)
              .filter(Objects::nonNull)
              .flatMap(Collection::stream)
              .collect(Collectors.toMap(SkuEngProduct::getSku, this::getEngineeringProducts));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Error processing provided JSON", e);
    }
  }

  public Stream<String> getTopLevelSkus() {
    return productTreeMap.keySet().stream();
  }

  @Override
  public Optional<RESTProductTree> getTree(String sku) {
    if (productTreeMap.containsKey(sku)) {
      return Optional.of(productTreeMap.get(sku));
    }
    if (derivedProductTreeMap.containsKey(sku)) {
      return Optional.of(derivedProductTreeMap.get(sku));
    }
    return Optional.empty();
  }

  @Override
  public Map<String, List<EngineeringProduct>> getEngineeringProductsForSkus(
      Collection<String> skus) {
    return engineeringProductsMap;
  }

  private List<EngineeringProduct> getEngineeringProducts(SkuEngProduct product) {
    if (product.getEngProducts() == null || product.getEngProducts().getEngProducts() == null) {
      return Collections.emptyList();
    }
    return product.getEngProducts().getEngProducts();
  }

  public static String findSku(RESTProductTree skuTree) {
    List<OperationalProduct> products = skuTree.getProducts();
    if (products == null || products.isEmpty()) {
      throw new IllegalArgumentException("SKU data doesn't have any products!");
    }
    return products.get(0).getSku();
  }
}
