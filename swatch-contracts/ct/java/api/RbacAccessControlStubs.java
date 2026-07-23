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

import domain.SubscriptionsAccessLevel;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;

/** Wiremock stubs for RBAC permission checks used by customer-facing APIs. */
public class RbacAccessControlStubs {

  // RBAC OpenAPI path is "/access/" — the generated REST client includes the trailing slash.
  private static final String RBAC_ACCESS_PATH = "/api/rbac/v1/access/";
  private static final String SUBSCRIPTIONS_APPLICATION = "subscriptions";

  private final ContractsWiremockService wiremockService;

  RbacAccessControlStubs(ContractsWiremockService wiremockService) {
    this.wiremockService = wiremockService;
  }

  public void stubSubscriptionsAccess(String identityHeader, SubscriptionsAccessLevel accessLevel) {
    var responseBody =
        accessLevel == SubscriptionsAccessLevel.DENIED
            ? Map.of("data", List.of(), "meta", Map.of("count", 0))
            : Map.of(
                "data",
                List.of(Map.of("permission", accessLevel.permission())),
                "meta",
                Map.of("count", 1));

    wiremockService
        .given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "GET",
                    "urlPath",
                    RBAC_ACCESS_PATH,
                    "queryParameters",
                    Map.of("application", Map.of("equalTo", SUBSCRIPTIONS_APPLICATION)),
                    "headers",
                    Map.of("x-rh-identity", Map.of("equalTo", identityHeader))),
                "response",
                Map.of(
                    "status",
                    HttpStatus.SC_OK,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "jsonBody",
                    responseBody),
                "priority",
                1,
                "metadata",
                wiremockService.getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }
}
