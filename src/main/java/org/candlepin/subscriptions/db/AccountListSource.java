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
package org.candlepin.subscriptions.db;

import java.util.stream.Stream;
import org.candlepin.subscriptions.tally.AccountListSourceException;

/** Provides account lists to various components of Tally. */
public interface AccountListSource {
  /**
   * Get a stream of accounts that should have their data synced.
   *
   * @return Stream of type String of accounts that should have their data synced.
   * @throws AccountListSourceException if there is an error processing the data.
   */
  Stream<String> syncableAccounts() throws AccountListSourceException;

  /**
   * Determines if the specified account number is in the reporting list.
   *
   * @param accountNumber the account number to check.
   * @return true if the account is in the list, false otherwise.
   */
  boolean containsReportingAccount(String accountNumber) throws AccountListSourceException;

  /**
   * Get a stream of accounts that should have their data purged according to the retention policy.
   * Any account not in this list will not have their data purged.
   *
   * @return a stream of accounts that should have its report data purged.
   * @throws AccountListSourceException
   */
  Stream<String> purgeReportAccounts() throws AccountListSourceException;
}
