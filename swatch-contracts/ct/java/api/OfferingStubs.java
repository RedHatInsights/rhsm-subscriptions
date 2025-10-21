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
package api;

import domain.Offering;
import java.util.Map;

/** Facade for stubbing Product API (Offering) endpoints. */
public class OfferingStubs {

  private final ContractsWiremockService wiremockService;

  protected OfferingStubs(ContractsWiremockService wiremockService) {
    this.wiremockService = wiremockService;
  }

  /**
   * Stub the offering data for testing. This sets up both upstream product data and engineering
   * products stubs.
   *
   * @param offering the offering test data containing SKU and product attributes
   */
  public void stubOfferingData(Offering offering) {
    stubUpstreamProductData(offering);
    stubEngineeringProducts(offering.getSku());
  }

  /**
   * Stub the upstream product data service to return product tree for a given SKU. This is used by
   * the offering sync API to populate offering data.
   */
  public void stubUpstreamProductData(Offering offering) {
    var attributes = new java.util.ArrayList<Map<String, String>>();
    if (offering.getCores() != null) {
      attributes.add(Map.of("code", "CORES", "value", String.valueOf(offering.getCores())));
    }
    if (offering.getSockets() != null) {
      attributes.add(
          Map.of("code", "SOCKET_LIMIT", "value", String.valueOf(offering.getSockets())));
    }
    if (offering.getLevel1() != null) {
      attributes.add(Map.of("code", "LEVEL_1", "value", offering.getLevel1()));
    }
    if (offering.getLevel2() != null) {
      attributes.add(Map.of("code", "LEVEL_2", "value", offering.getLevel2()));
    }
    if (offering.getMetered() != null) {
      attributes.add(Map.of("code", "METERED", "value", offering.getMetered()));
    }
    if (offering.getServiceLevel() != null) {
      attributes.add(
          Map.of("code", "SERVICE_TYPE", "value", offering.getServiceLevel().toDataModel()));
    }
    if (offering.getUsage() != null) {
      attributes.add(Map.of("code", "USAGE", "value", offering.getUsage().toDataModel()));
    }

    var product =
        Map.of(
            "sku",
            offering.getSku(),
            "description",
            offering.getDescription(),
            "attributes",
            attributes);

    var responseBody = Map.of("products", java.util.List.of(product));

    wiremockService
        .given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "GET",
                    "urlPathPattern",
                    String.format("/mock/product/products/%s/tree.*", offering.getSku())),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "jsonBody",
                    responseBody),
                "priority",
                9,
                "metadata",
                wiremockService.getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }

  /**
   * Stub the engineering products endpoint to return empty engineering products for given SKUs
   * since we don't test engIds.
   *
   * @param skus list of SKUs to mock
   */
  public void stubEngineeringProducts(String... skus) {
    // Return empty engIds entries list for each SKU, return an empty engProducts array
    var entries = new java.util.ArrayList<Map<String, Object>>();
    for (String sku : skus) {
      entries.add(Map.of("sku", sku, "engProducts", Map.of("engProducts", java.util.List.of())));
    }
    var responseBody = Map.of("entries", entries);

    wiremockService
        .given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of("method", "GET", "urlPathPattern", "/mock/product/engproducts/sku=.*"),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "jsonBody",
                    responseBody),
                "priority",
                9,
                "metadata",
                wiremockService.getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }
}
