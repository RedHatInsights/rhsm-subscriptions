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

/** Wiremock gRPC stubs for Kessel inventory Check used by customer-facing APIs. */
public class KesselAccessControlStubs {

  private static final String CHECK_PATH = "/kessel.inventory.v1beta2.KesselInventoryService/Check";
  private static final String WORKSPACE_PATH = "/api/rbac/v2/workspaces/";
  private static final String SUBSCRIPTIONS_REPORT_VIEW = "subscriptions_report_view";

  private final ContractsWiremockService wiremockService;

  KesselAccessControlStubs(ContractsWiremockService wiremockService) {
    this.wiremockService = wiremockService;
  }

  public void stubDefaultWorkspace(String orgId) {
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
                    WORKSPACE_PATH,
                    "queryParameters",
                    Map.of("type", Map.of("equalTo", "default")),
                    "headers",
                    Map.of("x-rh-rbac-org-id", Map.of("equalTo", orgId))),
                "response",
                Map.of(
                    "status",
                    HttpStatus.SC_OK,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "jsonBody",
                    Map.of(
                        "data",
                        List.of(
                            Map.of(
                                "id",
                                orgId + "-default-workspace",
                                "name",
                                "Default",
                                "type",
                                "default")))),
                "priority",
                1,
                "metadata",
                wiremockService.getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }

  public void stubSubscriptionsAccess(String userId, SubscriptionsAccessLevel accessLevel) {
    String allowed =
        accessLevel == SubscriptionsAccessLevel.DENIED ? "ALLOWED_FALSE" : "ALLOWED_TRUE";

    wiremockService
        .given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "POST",
                    "urlPath",
                    CHECK_PATH,
                    "bodyPatterns",
                    List.of(
                        Map.of(
                            "matchesJsonPath",
                            Map.of(
                                "expression", "$.relation", "equalTo", SUBSCRIPTIONS_REPORT_VIEW)),
                        Map.of(
                            "matchesJsonPath",
                            Map.of(
                                "expression",
                                "$.subject.resource.resourceId",
                                "equalTo",
                                userId)))),
                "response",
                Map.of(
                    "status",
                    HttpStatus.SC_OK,
                    "headers",
                    Map.of("grpc-status-name", "OK"),
                    "jsonBody",
                    Map.of("allowed", allowed)),
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
