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

import static com.redhat.swatch.component.tests.utils.SwatchUtils.securityHeadersWithServiceRole;
import static org.apache.http.HttpStatus.SC_OK;

import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.utilization.openapi.model.OrgPreferencesRequest;
import com.redhat.swatch.utilization.openapi.model.OrgPreferencesResponse;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Objects;

public class UtilizationSwatchService extends SwatchService {

  private static final String ORG_PREFERENCES_ENDPOINT =
      "/api/rhsm-subscriptions/v1/utilization/org-preferences";

  public Response getOrgPreferences(String orgId) {
    Objects.requireNonNull(orgId, "orgId must not be null");

    return given()
        .headers(securityHeadersWithServiceRole(orgId))
        .when()
        .get(ORG_PREFERENCES_ENDPOINT);
  }

  public OrgPreferencesResponse getOrgPreferencesExpectSuccess(String orgId) {
    Objects.requireNonNull(orgId, "orgId must not be null");

    return getOrgPreferences(orgId)
        .then()
        .statusCode(SC_OK)
        .extract()
        .as(OrgPreferencesResponse.class);
  }

  public Response updateOrgPreferences(String orgId, OrgPreferencesRequest request) {
    Objects.requireNonNull(orgId, "orgId must not be null");
    Objects.requireNonNull(request, "request must not be null");

    return given()
        .headers(securityHeadersWithServiceRole(orgId))
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post(ORG_PREFERENCES_ENDPOINT);
  }

  public OrgPreferencesResponse updateOrgPreferencesExpectSuccess(
      String orgId, OrgPreferencesRequest request) {
    Objects.requireNonNull(orgId, "orgId must not be null");
    Objects.requireNonNull(request, "request must not be null");

    return updateOrgPreferences(orgId, request)
        .then()
        .statusCode(SC_OK)
        .extract()
        .as(OrgPreferencesResponse.class);
  }
}
