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
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.component.tests.utils.SwatchUtils;
import io.restassured.response.Response;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OptInRbacAccessComponentTest extends BaseTallyComponentTest {

  @BeforeEach
  void setUpAccessControl() {
    service.createOptInConfig(orgId);
  }

  @AfterAll
  static void resetKesselRbacFlag() {
    unleash.disableKesselRbac();
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-optin-TC001")
  void shouldAllowOptInWhenUserHasAdminAccess(AuthorizationModel authorizationModel) {
    // Given: User with admin permission
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    rbacHelper.givenUserHasSubscriptionsAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.GRANTED_ADMIN);

    // When/Then: All operations succeed
    assertOptInEndpointsSucceed(requestHeaders);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-optin-TC002")
  void shouldAllowOptInWhenUserHasReaderAccess(AuthorizationModel authorizationModel) {
    // Given: User with reader permission
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    rbacHelper.givenUserHasSubscriptionsAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.GRANTED_READER);

    // When/Then: All operations succeed
    assertOptInEndpointsSucceed(requestHeaders);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-optin-TC003")
  void shouldDenyOptInWhenUserHasNoAccess(AuthorizationModel authorizationModel) {
    // Given: User with no permissions
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    rbacHelper.givenUserHasSubscriptionsAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.DENIED);

    // When/Then: All operations fail (forbidden)
    assertOptInEndpointsForbidden(requestHeaders);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-optin-TC004")
  void shouldAllowOptInWhenServiceAccountHasAdminAccess(AuthorizationModel authorizationModel) {
    // Given: ServiceAccount with admin permission
    String clientId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithServiceAccount(orgId, clientId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    rbacHelper.givenServiceAccountHasSubscriptionsAccess(
        authorizationModel, clientId, identityHeader, SubscriptionsAccessLevel.GRANTED_ADMIN);

    // When/Then: All operations succeed
    assertOptInEndpointsSucceed(requestHeaders);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-optin-TC005")
  void shouldAllowOptInWhenServiceAccountHasReaderAccess(AuthorizationModel authorizationModel) {
    // Given: ServiceAccount with reader permission
    String clientId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithServiceAccount(orgId, clientId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    rbacHelper.givenServiceAccountHasSubscriptionsAccess(
        authorizationModel, clientId, identityHeader, SubscriptionsAccessLevel.GRANTED_READER);

    // When/Then: All operations succeed
    assertOptInEndpointsSucceed(requestHeaders);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-optin-TC006")
  void shouldDenyOptInWhenServiceAccountHasNoAccess(AuthorizationModel authorizationModel) {
    // Given: ServiceAccount with no permissions
    String clientId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithServiceAccount(orgId, clientId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    rbacHelper.givenServiceAccountHasSubscriptionsAccess(
        authorizationModel, clientId, identityHeader, SubscriptionsAccessLevel.DENIED);

    // When/Then: All operations fail (forbidden)
    assertOptInEndpointsForbidden(requestHeaders);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-optin-TC007")
  void shouldDenyOptInForAssociateIdentity(AuthorizationModel authorizationModel) {
    // Given: Associate identity
    String email = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithAssociate(orgId, email);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    rbacHelper.givenAssociateHasSubscriptionsAccess(
        authorizationModel, email, identityHeader, SubscriptionsAccessLevel.GRANTED_ADMIN);

    // When/Then: All operations fail (forbidden)
    assertOptInEndpointsForbidden(requestHeaders);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-optin-TC008")
  void shouldDenyOptInForX509Identity(AuthorizationModel authorizationModel) {
    // Given: X509 identity
    var requestHeaders = SwatchUtils.securityHeadersWithServiceRole(orgId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    rbacHelper.givenX509HasSubscriptionsAccess(
        authorizationModel, identityHeader, SubscriptionsAccessLevel.GRANTED_ADMIN);

    // When/Then: All operations fail (forbidden)
    assertOptInEndpointsForbidden(requestHeaders);
  }

  private void assertOptInEndpointsSucceed(Map<String, String> requestHeaders) {
    assertOptInEndpointsReturn(
        requestHeaders, HttpStatus.SC_OK, HttpStatus.SC_OK, HttpStatus.SC_NO_CONTENT);
  }

  private void assertOptInEndpointsForbidden(Map<String, String> requestHeaders) {
    assertOptInEndpointsReturn(
        requestHeaders, HttpStatus.SC_FORBIDDEN, HttpStatus.SC_FORBIDDEN, HttpStatus.SC_FORBIDDEN);
  }

  private void assertOptInEndpointsReturn(
      Map<String, String> requestHeaders, int expectedGet, int expectedPut, int expectedDelete) {
    Response getResponse = service.getOptInWithHeaders(requestHeaders);
    Response putResponse = service.putOptInWithHeaders(requestHeaders);
    Response deleteResponse = service.deleteOptInWithHeaders(requestHeaders);

    assertAll(
        "opt-in endpoint responses",
        () -> assertEquals(expectedGet, getResponse.statusCode(), "GET /opt-in"),
        () -> assertEquals(expectedPut, putResponse.statusCode(), "PUT /opt-in"),
        () -> assertEquals(expectedDelete, deleteResponse.statusCode(), "DELETE /opt-in"));
  }
}
