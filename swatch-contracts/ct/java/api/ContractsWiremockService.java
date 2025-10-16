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

import com.redhat.swatch.component.tests.api.WiremockService;
import java.util.Map;

public class ContractsWiremockService extends WiremockService {
  public void givenOfferingExists(
      String sku, String productId, String serviceLevel, String usage, boolean hasUnlimitedUsage) {

    // Create the product API response JSON
    String productResponse =
        String.format(
            """
        {
          "products": [
            {
              "sku": "%s",
              "description": "%s Product Description",
              "status": "ACTIVE",
              "attributes": [
                {
                  "code": "PRODUCT_FAMILY",
                  "value": "Test Family"
                },
                {
                  "code": "SERVICE_TYPE",
                  "value": "%s"
                },
                {
                  "code": "PRODUCT_NAME",
                  "value": "%s"
                },
                {
                  "code": "USAGE",
                  "value": "%s"
                },
                {
                  "code": "HAS_UNLIMITED_USAGE",
                  "value": "%s"
                }
              ],
              "roles": []
            }
          ],
          "parentMap": []
        }
        """,
            sku, productId, serviceLevel, productId, usage, hasUnlimitedUsage ? "Y" : "N");

    // Create wiremock stub for product tree API
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "GET",
                    "urlPathPattern",
                    "/mock/product/products/" + sku + "/tree",
                    "queryParameters",
                    Map.of("attributes", Map.of("equalTo", "true"))),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "body",
                    productResponse),
                "metadata",
                Map.of("component-test-generated", "true")))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);

    // Create wiremock stub for engineering products API
    String engProductsResponse =
        String.format(
            """
        {
          "entries": [
            {
              "sku": "%s",
              "engProducts": {
                "engProducts": [
                  {
                    "oid": 123456,
                    "name": "%s Engineering Product"
                  }
                ]
              }
            }
          ]
        }
        """,
            sku, productId);

    given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of("method", "GET", "urlPathPattern", "/mock/product/engproducts/sku=" + sku),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "body",
                    engProductsResponse),
                "metadata",
                Map.of("component-test-generated", "true")))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }
}
