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
package com.redhat.swatch.contract.test.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.redhat.swatch.clients.product.ProductService;
import com.redhat.swatch.clients.product.api.model.EngineeringProductMap;
import com.redhat.swatch.clients.product.api.model.EngineeringProducts;
import com.redhat.swatch.clients.product.api.model.SkuEngProduct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;

@Slf4j
public class ProductStubForEngProductsWiremockExtension implements ResponseDefinitionTransformerV2 {

  protected static final String NAME = "product-stub-for-engproducts-wiremock-extension";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean applyGlobally() {
    return false;
  }

  @Override
  public ResponseDefinition transform(ServeEvent serveEvent) {
    String[] skus =
        serveEvent.getRequest().getUrl().replace("/mock/product/engproducts/sku=", "").split(",");
    List<SkuEngProduct> sepList = new ArrayList<>(skus.length);
    for (String skuItem : skus) {
      String resName = String.format("/product-stub-data/engprods-%s.json", skuItem);
      EngineeringProductMap prodMap = readJsonResource(resName);
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
    }

    EngineeringProductMap epm = new EngineeringProductMap();
    epm.setEntries(sepList);

    return new ResponseDefinitionBuilder()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(writeJsonResource(epm))
        .build();
  }

  private static String writeJsonResource(EngineeringProductMap epm) {
    try {
      return MAPPER.writeValueAsString(epm);
    } catch (JsonProcessingException e) {
      Assertions.fail("Failed to write JSON object to JSON response", e);
      return null;
    }
  }

  private static EngineeringProductMap readJsonResource(String resName) {
    try (InputStream res = ProductService.class.getResourceAsStream(resName)) {
      if (res == null) {
        log.warn(
            "Could not find resource=\"{}\" for class=\"{}\"",
            resName,
            ProductService.class.getName());
        return null;
      }
      return MAPPER.readValue(res, EngineeringProductMap.class);
    } catch (IOException e) {
      Assertions.fail(
          "Found resource=\""
              + resName
              + "\" but failed to create an instance of class=\""
              + EngineeringProductMap.class.getName()
              + "\".",
          e);
    }

    return null;
  }
}
