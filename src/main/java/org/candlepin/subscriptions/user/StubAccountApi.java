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
package org.candlepin.subscriptions.user;

import org.candlepin.subscriptions.user.api.model.Account;
import org.candlepin.subscriptions.user.api.model.AccountSearch;
import org.candlepin.subscriptions.user.api.resources.AccountApi;

/** Stub implementation of the Account API that returns a canned response. */
public class StubAccountApi extends AccountApi {

  /**
   * Generate mock lookups for accountNumber <-> orgId translations.
   *
   * <p>Translates any accountNumber (stripping "account" prefix) to org$accountNumber. Translates
   * any orgId (stripped "org" prefix) to account$orgId.
   *
   * <p>As a special exception, it translates orgId 123 to accountNumber 123.
   *
   * @param accountSearch (required)
   * @return mocked API response
   */
  @Override
  public Account findAccount(AccountSearch accountSearch) {
    String orgId = accountSearch.getBy().getId();
    String accountNumber = accountSearch.getBy().getEbsAccountNumber();
    if ("123".equals(orgId)) {
      accountNumber = "123";
    } else if (accountNumber != null) {
      orgId = "org" + accountNumber.replace("account", "");
    } else if (orgId != null) {
      accountNumber = "account" + orgId.replace("org", "");
    }

    return new Account().ebsAccountNumber(accountNumber).id(orgId);
  }
}
