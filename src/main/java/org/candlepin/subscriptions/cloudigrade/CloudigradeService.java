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
package org.candlepin.subscriptions.cloudigrade;

import java.time.LocalDate;
import org.candlepin.subscriptions.cloudigrade.api.model.ConcurrencyReport;
import org.candlepin.subscriptions.cloudigrade.api.resources.ConcurrentApi;
import org.candlepin.subscriptions.cloudigrade.internal.api.model.UserResponse;
import org.candlepin.subscriptions.cloudigrade.internal.api.resources.UsersApi;
import org.springframework.stereotype.Component;

/** Wrapper for cloudigrade APIs (both internal and external) which handles header generation */
@Component
public class CloudigradeService {

  private final ConcurrentApi concurrentApi;

  private final UsersApi usersApi;
  private final CloudigradeServiceProperties internalProperties;
  private final CloudigradeServiceProperties externalProperties;

  public CloudigradeService(
      ConcurrentApi concurrentApiApi,
      UsersApi usersApi,
      CloudigradeServiceProperties internalProperties,
      CloudigradeServiceProperties externalProperties) {
    this.concurrentApi = concurrentApiApi;
    this.usersApi = usersApi;
    this.internalProperties = internalProperties;
    this.externalProperties = externalProperties;
  }

  public ConcurrencyReport listDailyConcurrentUsages(
      String orgId, Integer limit, Integer offset, LocalDate startDate, LocalDate endDate)
      throws ApiException {
    return concurrentApi.listDailyConcurrentUsages(
        externalProperties.getPresharedKey(), orgId, limit, offset, startDate, endDate);
  }

  public UserResponse listCloudigradeUser(String orgId)
      throws org.candlepin.subscriptions.cloudigrade.internal.ApiException {
    return usersApi.listCloudigradeUser(internalProperties.getPresharedKey(), orgId);
  }

  public boolean cloudigradeUserExists(String orgId)
      throws org.candlepin.subscriptions.cloudigrade.internal.ApiException {
    var response = listCloudigradeUser(orgId);
    if (response.getData() == null || response.getData().isEmpty()) {
      return false;
    }

    // See SWATCH-545: an error in the spec definition changed our request from
    // /users?username={name} to /users (which returned all the users). At the time we were
    // merely checking to see if response.getData() had a non-zero result to answer the question
    // of whether the user existed or not.  The entire list of all users then triggered a false
    // positive that the given user existed.
    if (response.getData().size() > 1) {
      throw new IllegalStateException(
          "Cloudigrade return multiple results for one user. Probable"
              + " error in request to Cloudigrade API.");
    }

    var user = response.getData().get(0);
    if (orgId.equals(user.getOrgId())) {
      return true;
    }

    throw new IllegalStateException(
        "Cloudigrade returned a user that does not match the user we " + "requested");
  }
}
