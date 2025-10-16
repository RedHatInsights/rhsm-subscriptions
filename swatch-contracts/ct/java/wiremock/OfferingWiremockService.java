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
package wiremock;

import com.redhat.swatch.component.tests.api.WiremockService;
import dto.OfferingTestData;
import java.util.Map;

/** WireMock service for offering-related API stubs. */
public class OfferingWiremockService extends WiremockService {

  /**
   * Stub the offering data for testing. This sets up both upstream product data and engineering
   * products stubs.
   *
   * @param offeringData the offering test data containing SKU and product attributes
   */
  public void stubOfferingData(OfferingTestData offeringData) {
    stubUpstreamProductData(
        offeringData.getSku(),
        offeringData.getDescription(),
        offeringData.getCores(),
        offeringData.getSockets(),
        offeringData.getLevel1(),
        offeringData.getLevel2(),
        offeringData.getMetered());
    stubEngineeringProducts(offeringData.getSku());
  }

  /**
   * Stub the upstream product data service to return product tree for a given SKU. This is used by
   * the offering sync API to populate offering data.
   *
   * @param sku the SKU to mock (e.g., "MW02393")
   * @param description product description
   * @param cores number of cores (optional, can be null)
   * @param sockets number of sockets (optional, can be null)
   * @param level1 level 1 categorization (e.g., "OpenShift")
   * @param level2 level 2 categorization (e.g., "ROSA - RH OpenShift on AWS")
   * @param metered metered flag (optional, can be null, e.g., "Y" or "N")
   */
  public void stubUpstreamProductData(
      String sku,
      String description,
      Integer cores,
      Integer sockets,
      String level1,
      String level2,
      String metered) {

    var attributes = new java.util.ArrayList<Map<String, String>>();

    if (cores != null) {
      attributes.add(Map.of("code", "CORES", "value", String.valueOf(cores)));
    }
    if (sockets != null) {
      attributes.add(Map.of("code", "SOCKET_LIMIT", "value", String.valueOf(sockets)));
    }
    if (level1 != null) {
      attributes.add(Map.of("code", "LEVEL_1", "value", level1));
    }
    if (level2 != null) {
      attributes.add(Map.of("code", "LEVEL_2", "value", level2));
    }
    if (metered != null) {
      attributes.add(Map.of("code", "METERED", "value", metered));
    }

    var product = Map.of("sku", sku, "description", description, "attributes", attributes);

    var responseBody = Map.of("products", java.util.List.of(product));

    given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "GET",
                    "urlPathPattern",
                    String.format("/mock/product/products/%s/tree.*", sku)),
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
                Map.of(METADATA_TAG, "true")))
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

    given()
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
                Map.of(METADATA_TAG, "true")))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }
}
