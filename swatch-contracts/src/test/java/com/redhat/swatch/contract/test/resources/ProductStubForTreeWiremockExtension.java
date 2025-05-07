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

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.redhat.swatch.clients.product.ProductService;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProductStubForTreeWiremockExtension implements ResponseDefinitionTransformerV2 {

  protected static final String NAME = "product-stub-for-tree-wiremock-extension";
  private static final Pattern PATTERN =
      Pattern.compile("/mock/product/products/(.+?)/tree\\?attributes=true");

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
    Matcher matcher = PATTERN.matcher(serveEvent.getRequest().getUrl());
    if (matcher.matches()) {
      String sku = matcher.group(1);
      try (InputStream is =
          ProductService.class.getResourceAsStream(
              "/product-stub-data/tree-%s_attrs-true.json".formatted(sku))) {
        if (is != null) {
          String content = new String(is.readAllBytes());
          return new ResponseDefinitionBuilder()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(content)
              .build();
        }

      } catch (IOException e) {
        // ignoring if it does not exist
      }
    }
    // not found if there is no linked resource
    return new ResponseDefinitionBuilder().withStatus(404).build();
  }
}
