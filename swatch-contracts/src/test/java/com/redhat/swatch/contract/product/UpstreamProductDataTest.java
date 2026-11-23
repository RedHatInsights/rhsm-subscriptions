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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.clients.product.ProductDataSource;
import com.redhat.swatch.clients.product.api.model.AttributeValue;
import com.redhat.swatch.clients.product.api.model.EngineeringProduct;
import com.redhat.swatch.clients.product.api.model.OperationalProduct;
import com.redhat.swatch.clients.product.api.model.RESTProductTree;
import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.contract.product.umb.ProductAttribute;
import com.redhat.swatch.contract.product.umb.UmbOperationalProduct;
import com.redhat.swatch.contract.repository.OfferingEntity;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UpstreamProductDataTest {

  private static final String SKU = "sla-usage-product";

  @Test
  void leavesAbsentSlaAndUsageNull() {
    var offering = offeringFromTree(productTree());

    assertNull(offering.getServiceLevel());
    assertNull(offering.getUsage());
  }

  @Test
  void leavesBlankSlaAndUsageNull() {
    var offering =
        offeringFromTree(
            productTree(
                new AttributeValue().code("SERVICE_TYPE").value("  "),
                new AttributeValue().code("USAGE").value("")));

    assertNull(offering.getServiceLevel());
    assertNull(offering.getUsage());
  }

  @Test
  void mapsNonEmptyUnrecognizedSlaAndUsageToEmpty() {
    var offering =
        offeringFromTree(
            productTree(
                new AttributeValue().code("SERVICE_TYPE").value("n/a"),
                new AttributeValue().code("USAGE").value("Unknown usage")));

    assertEquals(ServiceLevel.EMPTY, offering.getServiceLevel());
    assertEquals(Usage.EMPTY, offering.getUsage());
  }

  @ParameterizedTest
  @CsvSource({"n/a, none", "'  ', ''"})
  void inheritsSlaAndUsageOverPlaceholders(String parentSla, String parentUsage) {
    String derivedSku = "sla-usage-derived";
    var tree =
        productTree(
            attribute("SERVICE_TYPE", parentSla),
            attribute("USAGE", parentUsage),
            attribute("DERIVED_SKU", derivedSku));
    var standard = attribute("SERVICE_TYPE", "Standard");
    tree.addProductsItem(product("SVC-child", standard));
    var derived = new RESTProductTree();
    var development = attribute("USAGE", "Development/Test");
    derived.addProductsItem(product(derivedSku, development));
    var trees = Map.of(SKU, tree, derivedSku, derived);
    var source = dataSource(trees, new AtomicInteger());

    var result = UpstreamProductData.offeringFromUpstream(SKU, source);
    var offering = result.orElseThrow();

    assertEquals(ServiceLevel.STANDARD, offering.getServiceLevel());
    assertEquals(Usage.DEVELOPMENT_TEST, offering.getUsage());
  }

  @Test
  void retainsParentSlaAndUsagePlaceholdersOverBlankChildValues() {
    var tree = productTree(attribute("SERVICE_TYPE", "n/a"), attribute("USAGE", "none"));
    tree.addProductsItem(
        product("SVC-child", attribute("SERVICE_TYPE", "  "), attribute("USAGE", "")));

    var offering = offeringFromTree(tree);

    assertEquals(ServiceLevel.EMPTY, offering.getServiceLevel());
    assertEquals(Usage.EMPTY, offering.getUsage());
  }

  @Test
  void refreshesExistingEmptyValuesWhenProductEventOmitsThem() {
    var requests = new AtomicInteger();
    var stored =
        OfferingEntity.builder()
            .sku(SKU)
            .serviceLevel(ServiceLevel.EMPTY)
            .usage(Usage.EMPTY)
            .build();
    var event =
        UmbOperationalProduct.builder().sku(SKU).attributes(new ProductAttribute[0]).build();
    var source = dataSource(Map.of(SKU, productTree()), requests);

    var result = UpstreamProductData.offeringFromUmbData(event, stored, source);
    var refreshed = result.orElseThrow();

    assertEquals(1, requests.get());
    assertNull(refreshed.getServiceLevel());
    assertNull(refreshed.getUsage());
  }

  @Test
  void keepsExistingEmptyValuesWhenProductEventRefreshCannotFindTheSku() {
    var requests = new AtomicInteger();
    var stored =
        OfferingEntity.builder()
            .sku(SKU)
            .serviceLevel(ServiceLevel.EMPTY)
            .usage(Usage.EMPTY)
            .build();
    var event =
        UmbOperationalProduct.builder().sku(SKU).attributes(new ProductAttribute[0]).build();
    var source = dataSource(Map.of(), requests);

    var result = UpstreamProductData.offeringFromUmbData(event, stored, source);

    assertTrue(result.isEmpty());
    assertEquals(1, requests.get());
    assertEquals(ServiceLevel.EMPTY, stored.getServiceLevel());
    assertEquals(Usage.EMPTY, stored.getUsage());
  }

  private static OfferingEntity offeringFromTree(RESTProductTree tree) {
    var source = dataSource(Map.of(SKU, tree), new AtomicInteger());
    var result = UpstreamProductData.offeringFromUpstream(SKU, source);
    return result.orElseThrow();
  }

  private static RESTProductTree productTree(AttributeValue... attributes) {
    var product = new OperationalProduct().sku(SKU);
    if (attributes.length > 0) {
      product.attributes(List.of(attributes));
    }
    return new RESTProductTree().addProductsItem(product);
  }

  private static OperationalProduct product(String sku, AttributeValue... attributes) {
    return new OperationalProduct().sku(sku).attributes(List.of(attributes));
  }

  private static AttributeValue attribute(String code, String value) {
    return new AttributeValue().code(code).value(value);
  }

  private static ProductDataSource dataSource(
      Map<String, RESTProductTree> trees, AtomicInteger requests) {
    return new ProductDataSource() {
      @Override
      public Optional<RESTProductTree> getTree(String sku) {
        requests.incrementAndGet();
        return Optional.ofNullable(trees.get(sku));
      }

      @Override
      public Map<String, List<EngineeringProduct>> getEngineeringProductsForSkus(
          Collection<String> skus) {
        return Map.of();
      }
    };
  }
}
