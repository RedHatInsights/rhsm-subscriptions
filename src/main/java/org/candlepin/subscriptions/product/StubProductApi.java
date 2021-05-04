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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.candlepin.subscriptions.product.api.model.EngineeringProductMap;
import org.candlepin.subscriptions.product.api.model.EngineeringProducts;
import org.candlepin.subscriptions.product.api.model.RESTProductTree;
import org.candlepin.subscriptions.product.api.model.SkuEngProduct;
import org.candlepin.subscriptions.product.api.resources.ProductApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub version of {@link ProductApi} for {@link ProductApiFactory} for local testing. This will
 * look up product data on the classpath. Look at
 * https://source.redhat.com/groups/public/teamnado/blog/grabbing_real_sku_tree_and_engprod_oid_data_for_swatch_stubproductapi
 * for more info on adding data.
 */
public class StubProductApi extends ProductApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(StubProductApi.class);

  // Get the same type of ObjectMapper as used by ProductApi.
  private static final ObjectMapper MAPPER = (new JSON()).getContext(ObjectMapper.class);

  @Override
  public EngineeringProductMap getEngineeringProductsForSkus(String sku) throws ApiException {
    List<String> skuList = List.of(sku.split(","));
    List<SkuEngProduct> sepList = new ArrayList<>(skuList.size());

    for (String skuItem : skuList) {
      String resName = String.format("/product-stub-data/engprods-%s.json", skuItem);
      try {
        EngineeringProductMap prodMap = readJsonResource(resName, EngineeringProductMap.class);
        // If the SKU exists, then add the correct data.
        if (prodMap != null && prodMap.getEntries() != null) {
          sepList.addAll(prodMap.getEntries());
        } else {
          // If the SKU doesn't exist, then this class should still behave like the real service
          // it is emulating. So add an empty entry, complete with an empty list of eng products.
          SkuEngProduct emptyEntry = new SkuEngProduct();
          emptyEntry.setSku(skuItem);

          EngineeringProducts emptyEngProducts = new EngineeringProducts();
          emptyEngProducts.engProducts(Collections.emptyList());
          emptyEntry.setEngProducts(emptyEngProducts);

          sepList.add(emptyEntry);
        }
      } catch (IOException e) {
        throw new ApiException(e);
      }
    }

    EngineeringProductMap epm = new EngineeringProductMap();
    epm.setEntries(sepList);

    return epm;
  }

  @Override
  public RESTProductTree getProductTree(String sku, Boolean attributes) throws ApiException {
    // The real call will not include attributes unless you ask for it,
    // but this will always return attributes no matter what.
    String resName = String.format("/product-stub-data/tree-%s_attrs-true.json", sku);
    try {
      return readJsonResource(resName, RESTProductTree.class);
    } catch (IOException e) {
      throw new ApiException(e);
    }
  }

  /**
   * Given a json resource and a type, returns an object of that type.
   *
   * @param resName Name of the resource
   * @param clazz The type of object to deserialize into
   * @param <T> The type of object to deserialize into
   * @return The object if the json resource was found, null if not.
   * @throws IOException if deserialization failed.
   */
  private <T> T readJsonResource(String resName, Class<T> clazz) throws IOException {
    try (InputStream res = this.getClass().getResourceAsStream(resName)) {
      if (res == null) {
        LOGGER.warn(
            "Could not find resource=\"{}\" for class=\"{}\"", resName, this.getClass().getName());
        return null;
      }
      return MAPPER.readValue(res, clazz);
    } catch (JsonMappingException | JsonParseException e) {
      throw new IOException(
          "Found resource=\""
              + resName
              + "\" but failed to create an instance of class=\""
              + clazz.getName()
              + "\".",
          e);
    }
  }
}
