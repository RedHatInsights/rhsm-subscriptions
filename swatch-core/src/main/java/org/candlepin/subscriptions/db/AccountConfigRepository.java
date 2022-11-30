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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.config.AccountConfig;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.util.StringUtils;

/** Defines all operations for storing account config entries. */
public interface AccountConfigRepository extends JpaRepository<AccountConfig, String> {

  @Query("select distinct c.accountNumber from AccountConfig c")
  Stream<String> findSyncEnabledAccounts();

  @Query("select distinct c.orgId from AccountConfig c")
  Stream<String> findSyncEnabledOrgs();

  @Query("select distinct c.orgId from AccountConfig c where c.accountNumber = :account")
  String findOrgByAccountNumber(@Param("account") String accountNumber);

  @Query("select distinct c.accountNumber from AccountConfig c where c.orgId = :orgId")
  String findAccountNumberByOrgId(@Param("orgId") String orgId);

  @Query(
      "select count(c) from AccountConfig c "
          + "where c.optInType='API' and c.created between :startOfWeek and :endOfWeek")
  int getCountOfOptInsForDateRange(OffsetDateTime startOfWeek, OffsetDateTime endOfWeek);

  Optional<AccountConfig> findByOrgId(String orgId);

  boolean existsByOrgId(String orgId);

  boolean existsByAccountNumber(String accountNumber);

  void deleteByOrgId(String accountNumber);

  default Optional<AccountConfig> createOrUpdateAccountConfig(
      String account, String orgId, OffsetDateTime current, OptInType optInType) {
    Optional<AccountConfig> found = findByOrgId(orgId);
    AccountConfig accountConfig = found.orElse(new AccountConfig());
    if (!found.isPresent()) {
      accountConfig.setOptInType(optInType);
      accountConfig.setCreated(current);
    }
    accountConfig.setOrgId(orgId);
    accountConfig.setUpdated(current);

    // Only set the account number if it is known.
    if (StringUtils.hasText(account)) {
      accountConfig.setAccountNumber(account);
    }

    return Optional.of(save(accountConfig));
  }
}
