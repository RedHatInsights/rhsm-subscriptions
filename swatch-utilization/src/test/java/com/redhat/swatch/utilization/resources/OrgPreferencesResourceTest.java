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
package com.redhat.swatch.utilization.resources;

import static com.redhat.swatch.common.security.RhIdentityHeaderAuthenticationMechanism.RH_IDENTITY_HEADER;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.utilization.openapi.model.OrgPreferencesRequest;
import com.redhat.swatch.utilization.openapi.model.OrgPreferencesResponse;
import com.redhat.swatch.utilization.service.OrgPreferencesService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
class OrgPreferencesResourceTest {

  private static final String ORG_ID = "org123";
  private static final String ORG_PREFERENCES_PATH =
      "/api/rhsm-subscriptions/v1/utilization/org-preferences";

  @InjectMock OrgPreferencesService orgPreferencesService;

  @Test
  void getOrgPreferences_whenPreferencesExist_returnsPreferences() {
    var expected = new OrgPreferencesResponse();
    expected.setCustomThreshold(10);
    when(orgPreferencesService.getOrgPreferences(ORG_ID)).thenReturn(expected);

    var actual =
        whenGetOrgPreferences()
            .statusCode(HttpStatus.SC_OK)
            .extract()
            .as(OrgPreferencesResponse.class);

    assertNotNull(actual);
    assertEquals(expected.getCustomThreshold(), actual.getCustomThreshold());
  }

  @Test
  void getOrgPreferences_whenPreferencesDoNotExist_returnsDefaultThreshold() {
    var expected = new OrgPreferencesResponse();
    expected.setCustomThreshold(80);
    when(orgPreferencesService.getOrgPreferences(ORG_ID)).thenReturn(expected);

    var actual =
        whenGetOrgPreferences()
            .statusCode(HttpStatus.SC_OK)
            .extract()
            .as(OrgPreferencesResponse.class);

    assertNotNull(actual);
    assertEquals(80, actual.getCustomThreshold());
  }

  @Test
  void updateOrgPreferences_whenPayloadValid_invokesServiceWithResolvedOrgId() {
    var expected = new OrgPreferencesResponse();
    expected.setCustomThreshold(4);
    when(orgPreferencesService.updateOrgPreferences(eq(ORG_ID), any(OrgPreferencesRequest.class)))
        .thenReturn(expected);

    var actual =
        whenUpdateOrgPreferencesTo(4)
            .statusCode(HttpStatus.SC_OK)
            .extract()
            .as(OrgPreferencesResponse.class);

    assertNotNull(actual);
    assertEquals(expected.getCustomThreshold(), actual.getCustomThreshold());
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 101})
  void updateOrgPreferences_whenThresholdOutOfRange_returnsBadRequestWithoutCallingService(
      int invalidThreshold) {
    whenUpdateOrgPreferencesTo(invalidThreshold).statusCode(HttpStatus.SC_BAD_REQUEST);

    verify(orgPreferencesService, never())
        .updateOrgPreferences(anyString(), any(OrgPreferencesRequest.class));
  }

  @Test
  void updateOrgPreferences_whenCustomThresholdMissing_returnsBadRequestWithoutCallingService() {
    whenUpdateOrgPreferencesTo(null).statusCode(HttpStatus.SC_BAD_REQUEST);

    verify(orgPreferencesService, never())
        .updateOrgPreferences(anyString(), any(OrgPreferencesRequest.class));
  }

  private static ValidatableResponse whenGetOrgPreferences() {
    return given()
        .header(RH_IDENTITY_HEADER, base64UserIdentity(ORG_ID))
        .when()
        .get(ORG_PREFERENCES_PATH)
        .then();
  }

  private static ValidatableResponse whenUpdateOrgPreferencesTo(Integer customThreshold) {
    OrgPreferencesRequest request = new OrgPreferencesRequest();
    request.setCustomThreshold(customThreshold);

    return given()
        .header(RH_IDENTITY_HEADER, base64UserIdentity(ORG_ID))
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post(ORG_PREFERENCES_PATH)
        .then();
  }

  private static String base64UserIdentity(String orgId) {
    String json = String.format("{\"identity\":{\"type\":\"User\",\"org_id\":\"%s\"}}", orgId);
    return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }
}
