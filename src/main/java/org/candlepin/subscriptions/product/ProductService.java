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
package org.candlepin.subscriptions.product;

import java.util.*;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.product.api.model.EngineeringProduct;
import org.candlepin.subscriptions.product.api.model.RESTProductTree;
import org.candlepin.subscriptions.product.api.model.SkuEngProduct;
import org.candlepin.subscriptions.product.api.resources.ProductApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Retrieves product information. */
@Component
public class ProductService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductService.class);

  private final ProductApi productApi;

  public ProductService(ProductApi productApi) {
    this.productApi = productApi;
  }

  /**
   * Given a SKU, retrieves a product tree containing the SKU as either the parent or child in the
   * tree.
   *
   * @param sku the SKU identifying an operational product
   * @return An optional containing the product tree, or an empty if the pro
   * @throws ApiException if fails to make API call
   */
  public Optional<RESTProductTree> getTree(String sku) throws ApiException {
    LOGGER.debug("Retrieving product tree for sku={}", sku);
    Optional<RESTProductTree> skuTree =
        Optional.ofNullable(productApi.getProductTree(sku, Boolean.TRUE));

    /*
     * Some (not all) inactive or obsolete products can be treeless. See also:
     * https://docs.google.com/document/d/1t5OlyWanEpwXOA7ysPKuZW61cvYnIScwRMl--hmajXY/edit#heading=h.dumnkdjz1o1u
     */
    if (skuTree.isEmpty()) {
      LOGGER.warn("sku={} does not exist, no product tree returned.", sku);
    }

    return skuTree;
  }

  public List<EngineeringProduct> getEngineeringProductsForSku(String sku) throws ApiException {
    Collection<String> skus = Collections.singletonList(sku);
    return getEngineeringProductsForSkus(skus).get(sku);
  }

  public Map<String, List<EngineeringProduct>> getEngineeringProductsForSkus(
      Collection<String> skus) throws ApiException {
    String skusQuery = String.join(",", skus);
    return productApi.getEngineeringProductsForSkus(skusQuery).getEntries().stream()
        .collect(
            Collectors.toUnmodifiableMap(
                SkuEngProduct::getSku,
                skuEng ->
                    Collections.unmodifiableList(
                        new ArrayList<>(skuEng.getEngProducts().getEngProducts()))));
  }
}
