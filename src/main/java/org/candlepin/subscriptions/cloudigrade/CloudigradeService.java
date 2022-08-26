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
      String orgId,
      String accountNumber,
      Integer limit,
      Integer offset,
      LocalDate startDate,
      LocalDate endDate)
      throws ApiException {
    return concurrentApi.listDailyConcurrentUsages(
        externalProperties.getPresharedKey(),
        orgId,
        accountNumber,
        limit,
        offset,
        startDate,
        endDate);
  }

  public UserResponse listCloudigradeUser(String orgId, String accountNumber)
      throws org.candlepin.subscriptions.cloudigrade.internal.ApiException {
    /* The Cloudigrade "username" is actually the same as the org id that we need to send
     * in over the x-rh-cloudigrade-org-id header */
    return usersApi.listCloudigradeUser(internalProperties.getPresharedKey(), orgId, accountNumber);
  }

  public boolean cloudigradeUserExists(String orgId, String accountNumber)
      throws org.candlepin.subscriptions.cloudigrade.internal.ApiException {
    var response = listCloudigradeUser(orgId, accountNumber);
    return response.getData() != null && !response.getData().isEmpty();
  }
}
