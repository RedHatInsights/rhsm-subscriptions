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

import api.ContractsUnleashService;
import api.ContractsWiremockService;
import com.redhat.swatch.component.tests.api.AuthorizationModel;
import com.redhat.swatch.component.tests.api.SubscriptionsAccessLevel;

/** Configures RBAC v1 or Kessel stubs for contracts access-control tests. */
public class RbacAccessTestHelper {

  private final ContractsUnleashService unleash;
  private final ContractsWiremockService wiremock;
  private final String orgId;

  public RbacAccessTestHelper(
      ContractsUnleashService unleash, ContractsWiremockService wiremock, String orgId) {
    this.unleash = unleash;
    this.wiremock = wiremock;
    this.orgId = orgId;
  }

  public void givenUserHasSubscriptionsAccess(
      AuthorizationModel authorizationModel,
      String userId,
      String identityHeader,
      SubscriptionsAccessLevel accessLevel) {
    givenPrincipalHasSubscriptionsAccess(authorizationModel, userId, identityHeader, accessLevel);
  }

  public void givenServiceAccountHasSubscriptionsAccess(
      AuthorizationModel authorizationModel,
      String clientId,
      String identityHeader,
      SubscriptionsAccessLevel accessLevel) {
    givenPrincipalHasSubscriptionsAccess(authorizationModel, clientId, identityHeader, accessLevel);
  }

  private void givenPrincipalHasSubscriptionsAccess(
      AuthorizationModel authorizationModel,
      String principalId,
      String identityHeader,
      SubscriptionsAccessLevel accessLevel) {
    if (authorizationModel == AuthorizationModel.KESSEL) {
      unleash.enableKesselRbac();
      wiremock.forKesselAccessControl().stubDefaultWorkspace(orgId);
      wiremock.forKesselAccessControl().stubSubscriptionsAccess(principalId, accessLevel);
    } else {
      unleash.disableKesselRbac();
      wiremock.forRbacAccessControl().stubSubscriptionsAccess(identityHeader, accessLevel);
    }
  }
}
