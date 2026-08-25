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
package utils;

import api.TallyUnleashService;
import api.TallyWiremockService;
import com.redhat.swatch.component.tests.api.AuthorizationModel;
import com.redhat.swatch.component.tests.api.SubscriptionsAccessLevel;

/** Helper methods to create security headers for tally/api rbac component tests. */
public class RbacAccessTestHelper {

  private final TallyUnleashService unleash;
  private final TallyWiremockService wiremock;
  private final String orgId;

  public RbacAccessTestHelper(
      TallyUnleashService unleash, TallyWiremockService wiremock, String orgId) {
    this.unleash = unleash;
    this.wiremock = wiremock;
    this.orgId = orgId;
  }

  public void givenUserHasSubscriptionsAccess(
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

  public void givenServiceAccountHasSubscriptionsAccess(
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

  public void givenAssociateHasSubscriptionsAccess(
      AuthorizationModel authorizationModel,
      String email,
      String identityHeader,
      SubscriptionsAccessLevel accessLevel) {
    if (authorizationModel == AuthorizationModel.KESSEL) {
      unleash.enableKesselRbac();
      wiremock.forKesselAccessControl().stubDefaultWorkspace(orgId);
      wiremock.forKesselAccessControl().stubSubscriptionsAccess(email, accessLevel);
    } else {
      unleash.disableKesselRbac();
      wiremock.forRbacAccessControl().stubSubscriptionsAccess(identityHeader, accessLevel);
    }
  }

  public void givenX509HasSubscriptionsAccess(
      AuthorizationModel authorizationModel,
      String identityHeader,
      SubscriptionsAccessLevel accessLevel) {
    if (authorizationModel == AuthorizationModel.KESSEL) {
      unleash.enableKesselRbac();
      wiremock.forKesselAccessControl().stubDefaultWorkspace(orgId);
      wiremock.forKesselAccessControl().stubSubscriptionsAccess(orgId, accessLevel);
    } else {
      unleash.disableKesselRbac();
      wiremock.forRbacAccessControl().stubSubscriptionsAccess(identityHeader, accessLevel);
    }
  }
}
