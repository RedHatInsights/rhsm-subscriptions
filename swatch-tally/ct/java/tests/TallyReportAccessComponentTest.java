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

class TallyReportAccessComponentTest extends BaseTallyComponentTest {

  private static final String PRODUCT_TAG = "rhel-for-x86-els-payg";
  private static final String METRIC_ID = "vCPUs";

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
  @TestPlanName("rbac-parity-TC001")
  void shouldAllowTallyReportWhenAdminAccessGranted(AuthorizationModel authorizationModel) {
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    rbacHelper.givenUserHasSubscriptionsAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.GRANTED_ADMIN);

    Response response = whenGetTallyReport(requestHeaders);

    thenResponseStatusIs(response, HttpStatus.SC_OK);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-parity-TC003")
  void shouldAllowTallyReportWhenReaderAccessGranted(AuthorizationModel authorizationModel) {
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    rbacHelper.givenUserHasSubscriptionsAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.GRANTED_READER);

    Response response = whenGetTallyReport(requestHeaders);

    thenResponseStatusIs(response, HttpStatus.SC_OK);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-parity-TC005")
  void shouldDenyTallyReportWhenAccessDenied(AuthorizationModel authorizationModel) {
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    rbacHelper.givenUserHasSubscriptionsAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.DENIED);

    Response response = whenGetTallyReport(requestHeaders);

    thenResponseStatusIs(response, HttpStatus.SC_FORBIDDEN);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-parity-TC006")
  void shouldAllowTallyReportWhenServiceAccountAdminAccessGranted(
      AuthorizationModel authorizationModel) {
    String clientId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithServiceAccount(orgId, clientId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    rbacHelper.givenServiceAccountHasSubscriptionsAccess(
        authorizationModel, clientId, identityHeader, SubscriptionsAccessLevel.GRANTED_ADMIN);

    Response response = whenGetTallyReport(requestHeaders);

    thenResponseStatusIs(response, HttpStatus.SC_OK);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-parity-TC006")
  void shouldAllowTallyReportWhenServiceAccountReaderAccessGranted(
      AuthorizationModel authorizationModel) {
    String clientId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithServiceAccount(orgId, clientId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    rbacHelper.givenServiceAccountHasSubscriptionsAccess(
        authorizationModel, clientId, identityHeader, SubscriptionsAccessLevel.GRANTED_READER);

    Response response = whenGetTallyReport(requestHeaders);

    thenResponseStatusIs(response, HttpStatus.SC_OK);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("rbac-parity-TC007")
  void shouldDenyTallyReportWhenServiceAccountAccessDenied(AuthorizationModel authorizationModel) {
    String clientId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithServiceAccount(orgId, clientId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    rbacHelper.givenServiceAccountHasSubscriptionsAccess(
        authorizationModel, clientId, identityHeader, SubscriptionsAccessLevel.DENIED);

    Response response = whenGetTallyReport(requestHeaders);

    thenResponseStatusIs(response, HttpStatus.SC_FORBIDDEN);
  }

  private Response whenGetTallyReport(Map<String, String> requestHeaders) {
    return service.getTallyReportWithHeaders(PRODUCT_TAG, METRIC_ID, requestHeaders);
  }

  private void thenResponseStatusIs(Response response, int expectedStatus) {
    assertEquals(expectedStatus, response.statusCode());
  }
}
