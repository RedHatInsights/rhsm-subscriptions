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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Base64;
import org.candlepin.subscriptions.cloudigrade.api.model.ConcurrencyReport;
import org.candlepin.subscriptions.cloudigrade.api.model.IdentityHeader;
import org.candlepin.subscriptions.cloudigrade.api.model.IdentityHeaderIdentity;
import org.candlepin.subscriptions.cloudigrade.api.model.IdentityHeaderIdentityUser;
import org.candlepin.subscriptions.cloudigrade.api.resources.ConcurrentApi;
import org.candlepin.subscriptions.cloudigrade.internal.api.model.UserResponse;
import org.candlepin.subscriptions.cloudigrade.internal.api.resources.UsersApi;
import org.springframework.stereotype.Component;

/** Wrapper for cloudigrade APIs (both internal and external) which handles header generation */
@Component
public class CloudigradeService {

  private final ConcurrentApi concurrentApi;

  private final ObjectMapper objectMapper;
  private final UsersApi usersApi;
  private final CloudigradeServiceProperties internalProperties;
  private final Base64.Encoder b64Encoder;

  public CloudigradeService(
      ConcurrentApi concurrentApiApi,
      UsersApi usersApi,
      CloudigradeServiceProperties internalProperties,
      ObjectMapper objectMapper) {
    this.concurrentApi = concurrentApiApi;
    this.usersApi = usersApi;
    this.objectMapper = objectMapper;
    this.internalProperties = internalProperties;
    this.b64Encoder = Base64.getEncoder();
  }

  public ConcurrencyReport listDailyConcurrentUsages(
      String accountNumber, Integer limit, Integer offset, LocalDate startDate, LocalDate endDate)
      throws ApiException {
    IdentityHeaderIdentityUser user = new IdentityHeaderIdentityUser().isOrgAdmin(true);
    IdentityHeaderIdentity identity =
        new IdentityHeaderIdentity().accountNumber(accountNumber).user(user);
    IdentityHeader identityHeader = new IdentityHeader().identity(identity);

    try {
      String headerString =
          b64Encoder.encodeToString(objectMapper.writeValueAsBytes(identityHeader));
      return concurrentApi.listDailyConcurrentUsages(
          headerString, limit, offset, startDate, endDate);
    } catch (JsonProcessingException e) {
      throw new ApiException(e);
    }
  }

  public UserResponse listCloudigradeUser(String accountNumber)
      throws org.candlepin.subscriptions.cloudigrade.internal.ApiException {
    /* The Cloudigrade "username" is actually the same as the account number that we need to send
     * in over the x-rh-cloudigrade-account-number header */
    return usersApi.listCloudigradeUser(
        internalProperties.getPresharedKey(), accountNumber, accountNumber);
  }

  public boolean cloudigradeUserExists(String accountNumber)
      throws org.candlepin.subscriptions.cloudigrade.internal.ApiException {
    var response = listCloudigradeUser(accountNumber);
    return response.getData() != null && !response.getData().isEmpty();
  }
}
