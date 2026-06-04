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
package org.candlepin.subscriptions.rbac;

import java.util.List;
import org.candlepin.subscriptions.rbac.model.Access;
import org.candlepin.subscriptions.rbac.resources.AccessApi;

/** A wrapper around the RBAC API. */
public class RbacApiImpl implements RbacApi {

  private AccessApi accessApi;

  public RbacApiImpl(ApiClient client) {
    accessApi = new AccessApi(client);
  }

  @Override
  public List<Access> getCurrentUserAccess(String applicationName) throws RbacApiException {
    try {
      return accessApi.getPrincipalAccess(applicationName, null, null, null, null).getData();
    } catch (ApiException apie) {
      throw new RbacApiException("Unable to get current user access.", apie);
    }
  }

  public List<Access> getCurrentIdentityAccess(String application, String identityCode)
      throws RbacApiException {
    try {
      return accessApi.getPrincipalAccess(application, null, identityCode, null, null).getData();
    } catch (ApiException apie) {
      throw new RbacApiException("Unable to get current identity access.", apie);
    }
  }
}
