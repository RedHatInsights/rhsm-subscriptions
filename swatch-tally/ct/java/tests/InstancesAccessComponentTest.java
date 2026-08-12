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
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.component.tests.utils.SwatchUtils;
import io.restassured.response.Response;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class InstancesAccessComponentTest extends BaseTallyComponentTest {

  private static final String PRODUCT_TAG = "rhel-for-x86-els-payg";

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
  void shouldAllowInstancesWhenAdminAccessGranted(AuthorizationModel authorizationModel) {
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    givenSubscriptionsAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.GRANTED_ADMIN);

    Response response = whenGetInstances(requestHeaders);

    thenResponseStatusIs(response, HttpStatus.SC_OK);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  void shouldAllowInstancesWhenReaderAccessGranted(AuthorizationModel authorizationModel) {
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    givenSubscriptionsAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.GRANTED_READER);

    Response response = whenGetInstances(requestHeaders);

    thenResponseStatusIs(response, HttpStatus.SC_OK);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  void shouldDenyInstancesWhenAccessDenied(AuthorizationModel authorizationModel) {
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    givenSubscriptionsAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.DENIED);

    Response response = whenGetInstances(requestHeaders);

    thenResponseStatusIs(response, HttpStatus.SC_FORBIDDEN);
  }

  private void givenSubscriptionsAccess(
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

  private Response whenGetInstances(Map<String, String> requestHeaders) {
    return service.getInstancesByProductWithHeaders(PRODUCT_TAG, requestHeaders);
  }

  private void thenResponseStatusIs(Response response, int expectedStatus) {
    assertEquals(expectedStatus, response.statusCode());
  }
}
