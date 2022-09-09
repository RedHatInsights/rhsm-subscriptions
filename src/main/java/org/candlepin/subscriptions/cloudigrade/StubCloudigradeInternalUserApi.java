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
import org.candlepin.subscriptions.cloudigrade.internal.ApiException;
import org.candlepin.subscriptions.cloudigrade.internal.api.model.CloudigradeUser;
import org.candlepin.subscriptions.cloudigrade.internal.api.model.CloudigradeUserLinksSection;
import org.candlepin.subscriptions.cloudigrade.internal.api.model.UserResponse;
import org.candlepin.subscriptions.cloudigrade.internal.api.resources.UsersApi;

public class StubCloudigradeInternalUserApi extends UsersApi {

  @Override
  public UserResponse listCloudigradeUser(String xRhCloudigradePsk, String orgId)
      throws ApiException {
    return new UserResponse().links(createLinks()).addDataItem(createData());
  }

  private CloudigradeUser createData() {
    return new CloudigradeUser()
        .orgId("12345678")
        .accountNumber("87654321")
        .dateJoined(LocalDate.of(2011, 1, 1))
        .id(4);
  }

  private CloudigradeUserLinksSection createLinks() {
    return new CloudigradeUserLinksSection()
        .first("/internal/api/cloudigrade/v1/users/?limit=10&offset=0&username=12345678")
        .last("/internal/api/cloudigrade/v1/users/?limit=10&offset=0&username=12345678");
  }
}
