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

import java.util.Map;
import org.apache.http.HttpStatus;

/** Wiremock stubs for Insights export-service upload and error callbacks. */
public class ExportApiStubs {

  private static final String UPLOAD_PATH = "/app/export/v1/.*/.*/.*/upload";
  private static final String ERROR_PATH = "/app/export/v1/.*/.*/.*/error";

  private final ContractsWiremockService wiremockService;

  ExportApiStubs(ContractsWiremockService wiremockService) {
    this.wiremockService = wiremockService;
  }

  public void stubExportCallbacks() {
    stubPath(UPLOAD_PATH);
    stubPath(ERROR_PATH);
  }

  public int countUploadRequests() {
    return wiremockService.countRequests("/upload");
  }

  public int countErrorRequests() {
    return wiremockService.countRequests("/error");
  }

  private void stubPath(String urlPathPattern) {
    wiremockService
        .given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of("method", "POST", "urlPathPattern", urlPathPattern),
                "response",
                Map.of("status", HttpStatus.SC_ACCEPTED),
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
