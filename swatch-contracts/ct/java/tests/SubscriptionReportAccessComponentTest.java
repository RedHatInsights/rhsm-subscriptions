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

import api.AuthorizationModel;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.component.tests.utils.SwatchUtils;
import domain.Product;
import domain.SubscriptionsAccessLevel;
import io.restassured.response.Response;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class SubscriptionReportAccessComponentTest extends BaseContractComponentTest {

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("auth-access-TC001")
  void shouldAllowV2ReportWhenAccessGranted(AuthorizationModel authorizationModel) {
    // Given
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    givenSubscriptionsAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.GRANTED_ADMIN);

    // When
    Response response = whenGetV2SkuCapacityReport(requestHeaders);

    // Then
    thenResponseStatusIs(response, HttpStatus.SC_OK);
  }

  @ParameterizedTest(name = "authorizationModel={0}")
  @EnumSource(AuthorizationModel.class)
  @TestPlanName("auth-access-TC002")
  void shouldDenyV2ReportWhenAccessMissing(AuthorizationModel authorizationModel) {
    // Given
    String userId = RandomUtils.generateRandom();
    var requestHeaders = SwatchUtils.securityHeadersWithUserRole(orgId, userId);
    String identityHeader = requestHeaders.get(X_RH_IDENTITY_HEADER);
    givenSubscriptionsAccess(
        authorizationModel, userId, identityHeader, SubscriptionsAccessLevel.DENIED);

    // When
    Response response = whenGetV2SkuCapacityReport(requestHeaders);

    // Then — Quarkus @RolesAllowed denial returns 403 with an empty body (unlike Spring rhsm IQE).
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

  private Response whenGetV2SkuCapacityReport(Map<String, String> requestHeaders) {
    return service.getSkuCapacityByProductId(Product.OPENSHIFT, requestHeaders);
  }

  private void thenResponseStatusIs(Response response, int expectedStatus) {
    assertEquals(expectedStatus, response.statusCode());
  }
}
