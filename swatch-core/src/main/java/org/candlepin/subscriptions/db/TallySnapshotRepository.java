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
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Interface that Spring Data will turn into a DAO for us. */
public interface TallySnapshotRepository extends JpaRepository<TallySnapshot, UUID> {

  // suppress line length and params arguments, can't help either easily b/c this is a spring data
  // method

  @Query(
      "SELECT t FROM TallySnapshot t where "
          + "t.accountNumber = :accountNumber and "
          + "t.productId = :productId and "
          + "t.granularity = :granularity  and "
          + "t.serviceLevel = :serviceLevel and "
          + "t.usage = :usage and "
          + "t.billingProvider = :billingProvider and "
          + "t.billingAccountId = :billingAcctId and "
          + "t.snapshotDate between :beginning and :ending")
  Page<TallySnapshot> findSnapshot( // NOSONAR
      @Param("accountNumber") String accountNumber,
      @Param("productId") String productId,
      @Param("granularity") Granularity granularity,
      @Param("serviceLevel") ServiceLevel serviceLevel,
      @Param("usage") Usage usage,
      @Param("billingProvider") BillingProvider billingProvider,
      @Param("billingAcctId") String billingAccountId,
      @Param("beginning") OffsetDateTime beginning,
      @Param("ending") OffsetDateTime ending,
      @Param("pageable") Pageable pageable);

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void deleteAllByAccountNumberAndGranularityAndSnapshotDateBefore(
      String accountNumber, Granularity granularity, OffsetDateTime cutoffDate);

  Stream<TallySnapshot> findByAccountNumberAndProductIdInAndGranularityAndSnapshotDateBetween(
      String accountNumber,
      Collection<String> productIds,
      Granularity granularity,
      OffsetDateTime beginning,
      OffsetDateTime ending);

  void deleteByAccountNumber(String accountNumber);

  Stream<TallySnapshot> findByProductIdInAndSnapshotDateBetween(
      Set<String> productIds, OffsetDateTime start, OffsetDateTime end);
}
