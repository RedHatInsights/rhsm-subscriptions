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
package tests;

import static com.redhat.swatch.component.tests.utils.SwatchUtils.X_RH_IDENTITY_HEADER;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.component.tests.api.AuthorizationModel;
import com.redhat.swatch.component.tests.api.SubscriptionsAccessLevel;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.Wiremock;
import com.redhat.swatch.component.tests.api.WiremockService;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.component.tests.utils.SwatchUtils;
import com.redhat.swatch.utilization.openapi.model.OrgPreferencesRequest;
import io.restassured.response.Response;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrgPreferencesAccessComponentTest extends BaseUtilizationComponentTest {

  @Wiremock static WiremockService wiremock = new WiremockService();

  @AfterAll
  static void resetKesselRbacFlag() {
    unleash.disableKesselRbac();
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("org-preferences-auth-TC001")
  void shouldAllowGetOrgPreferencesWhenUserHasAdminAccess(AuthorizationModel authorizationModel) {
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    givenUserHasSubscriptionsAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.GRANTED_ADMIN);

    Response response = whenGetOrgPreferences(requestHeaders);

    thenResponseStatusIs(response, HttpStatus.SC_OK);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("org-preferences-auth-TC002")
  void shouldAllowGetOrgPreferencesWhenUserHasReaderAccess(AuthorizationModel authorizationModel) {
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    givenUserHasSubscriptionsAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.GRANTED_READER);

    Response response = whenGetOrgPreferences(requestHeaders);

    thenResponseStatusIs(response, HttpStatus.SC_OK);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("org-preferences-auth-TC003")
  void shouldDenyGetOrgPreferencesWhenUserHasNoAccess(AuthorizationModel authorizationModel) {
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    givenUserHasSubscriptionsAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.DENIED);

    Response response = whenGetOrgPreferences(requestHeaders);

    thenResponseStatusIs(response, HttpStatus.SC_FORBIDDEN);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("org-preferences-auth-TC004")
  void shouldDenyPostOrgPreferencesWhenUserIsNotOrgAdmin(AuthorizationModel authorizationModel) {
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    givenUserHasSubscriptionsAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.GRANTED_ADMIN);

    Response response = whenPostOrgPreferences(requestHeaders);

    thenResponseStatusIs(response, HttpStatus.SC_FORBIDDEN);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("org-preferences-auth-TC005")
  void shouldAllowPostButDenyGetWhenOrgAdminWithoutPermission(
      AuthorizationModel authorizationModel) {
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId, true);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    givenUserHasSubscriptionsAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.DENIED);

    Response postResponse = whenPostOrgPreferences(requestHeaders);
    Response getResponse = whenGetOrgPreferences(requestHeaders);

    assertAll(
        "org-preferences endpoint responses",
        () -> assertEquals(HttpStatus.SC_OK, postResponse.statusCode(), "POST org-preferences"),
        () ->
            assertEquals(
                HttpStatus.SC_FORBIDDEN, getResponse.statusCode(), "GET org-preferences"));
  }

  @Test
  @TestPlanName("org-preferences-auth-TC006")
  void shouldAllowBothEndpointsForServiceRole() {
    var requestHeaders = SwatchUtils.securityHeadersWithServiceRole(orgId);

    Response getResponse = whenGetOrgPreferences(requestHeaders);
    Response postResponse = whenPostOrgPreferences(requestHeaders);

    assertAll(
        "org-preferences endpoint responses",
        () -> assertEquals(HttpStatus.SC_OK, getResponse.statusCode(), "GET org-preferences"),
        () -> assertEquals(HttpStatus.SC_OK, postResponse.statusCode(), "POST org-preferences"));
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("org-preferences-auth-TC007")
  void shouldAllowGetOrgPreferencesWhenServiceAccountHasAdminAccess(
      AuthorizationModel authorizationModel) {
    String clientId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithServiceAccount(orgId, clientId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    givenServiceAccountHasSubscriptionsAccess(
        authorizationModel, clientId, identityHeader, SubscriptionsAccessLevel.GRANTED_ADMIN);

    Response response = whenGetOrgPreferences(requestHeaders);

    thenResponseStatusIs(response, HttpStatus.SC_OK);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("org-preferences-auth-TC008")
  void shouldDenyBothEndpointsWhenServiceAccountHasNoAccess(
      AuthorizationModel authorizationModel) {
    String clientId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithServiceAccount(orgId, clientId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    givenServiceAccountHasSubscriptionsAccess(
        authorizationModel, clientId, identityHeader, SubscriptionsAccessLevel.DENIED);

    Response getResponse = whenGetOrgPreferences(requestHeaders);
    Response postResponse = whenPostOrgPreferences(requestHeaders);

    assertAll(
        "org-preferences endpoint responses",
        () ->
            assertEquals(
                HttpStatus.SC_FORBIDDEN, getResponse.statusCode(), "GET org-preferences"),
        () ->
            assertEquals(
                HttpStatus.SC_FORBIDDEN, postResponse.statusCode(), "POST org-preferences"));
  }

  @Test
  @TestPlanName("org-preferences-auth-TC009")
  void shouldDenyGetOrgPreferencesWhenKesselUnavailable() {
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    unleash.enableKesselRbac();
    wiremock.forKesselAccessControl().stubDefaultWorkspace(orgId);

    Response response = whenGetOrgPreferences(requestHeaders);

    thenResponseStatusIs(response, HttpStatus.SC_FORBIDDEN);
  }

  @Test
  @TestPlanName("org-preferences-auth-TC010")
  void shouldDenyBothEndpointsForAssociateIdentity() {
    String email = RandomUtils.generateRandom() + "@redhat.com";
    var requestHeaders = SwatchUtils.securityHeadersWithAssociate(orgId, email);

    Response getResponse = whenGetOrgPreferences(requestHeaders);
    Response postResponse = whenPostOrgPreferences(requestHeaders);

    assertAll(
        "org-preferences endpoint responses",
        () ->
            assertEquals(
                HttpStatus.SC_FORBIDDEN, getResponse.statusCode(), "GET org-preferences"),
        () ->
            assertEquals(
                HttpStatus.SC_FORBIDDEN, postResponse.statusCode(), "POST org-preferences"));
  }

  @Test
  @TestPlanName("org-preferences-auth-TC011")
  void shouldDenyGetOrgPreferencesWhenRbacUnavailable() {
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    unleash.disableKesselRbac();

    Response response = whenGetOrgPreferences(requestHeaders);

    thenResponseStatusIs(response, HttpStatus.SC_FORBIDDEN);
  }

  private Response whenGetOrgPreferences(Map<String, String> requestHeaders) {
    return service.getOrgPreferences(requestHeaders);
  }

  private Response whenPostOrgPreferences(Map<String, String> requestHeaders) {
    return service.updateOrgPreferences(requestHeaders, defaultRequest());
  }

  private void thenResponseStatusIs(Response response, int expectedStatus) {
    assertEquals(expectedStatus, response.statusCode());
  }

  private void givenUserHasSubscriptionsAccess(
      AuthorizationModel authorizationModel,
      String userId,
      String identityHeader,
      SubscriptionsAccessLevel accessLevel) {
    if (authorizationModel == AuthorizationModel.KESSEL) {
      unleash.enableKesselRbac();
      wiremock.forKesselAccessControl().stubDefaultWorkspace(orgId);
      wiremock.forKesselAccessControl().stubSubscriptionsAccess(userId, accessLevel);
    } else {
      unleash.disableKesselRbac();
      wiremock.forRbacAccessControl().stubSubscriptionsAccess(identityHeader, accessLevel);
    }
  }

  private void givenServiceAccountHasSubscriptionsAccess(
      AuthorizationModel authorizationModel,
      String clientId,
      String identityHeader,
      SubscriptionsAccessLevel accessLevel) {
    if (authorizationModel == AuthorizationModel.KESSEL) {
      unleash.enableKesselRbac();
      wiremock.forKesselAccessControl().stubDefaultWorkspace(orgId);
      wiremock.forKesselAccessControl().stubSubscriptionsAccess(clientId, accessLevel);
    } else {
      unleash.disableKesselRbac();
      wiremock.forRbacAccessControl().stubSubscriptionsAccess(identityHeader, accessLevel);
    }
  }

  private static OrgPreferencesRequest defaultRequest() {
    return new OrgPreferencesRequest().customThreshold(50);
  }
}