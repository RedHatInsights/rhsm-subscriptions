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
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Interface that Spring Data will turn into a DAO for us. */
public interface TallySnapshotRepository extends JpaRepository<TallySnapshot, UUID> {

  // suppress line length and params arguments, can't help either easily b/c this is a spring data
  // method

  @Query(
      value =
          "SELECT distinct t FROM TallySnapshot t left join fetch t.tallyMeasurements where "
              + "t.orgId = :orgId and "
              + "t.productId = :productId and "
              + "t.granularity = :granularity  and "
              + "t.serviceLevel = :serviceLevel and "
              + "t.usage = :usage and "
              + "t.billingProvider = :billingProvider and "
              + "t.billingAccountId = :billingAcctId and "
              + "t.snapshotDate between :beginning and :ending "
              + "order by t.snapshotDate",
      countQuery =
          "SELECT count(t) FROM TallySnapshot t where "
              + "t.orgId = :orgId and "
              + "t.productId = :productId and "
              + "t.granularity = :granularity  and "
              + "t.serviceLevel = :serviceLevel and "
              + "t.usage = :usage and "
              + "t.billingProvider = :billingProvider and "
              + "t.billingAccountId = :billingAcctId and "
              + "t.snapshotDate between :beginning and :ending ")
  Page<TallySnapshot> findSnapshot( // NOSONAR
      @Param("orgId") String orgId,
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
  @Modifying
  @Query(
      value =
          "delete from TallySnapshot where orgId=:orgId and granularity=:granularity and snapshotDate < :cutoffDate")
  void deleteAllByOrgIdAndGranularityAndSnapshotDateBefore(
      String orgId, Granularity granularity, OffsetDateTime cutoffDate);

  @Query(
      value =
          "SELECT distinct t FROM TallySnapshot t left join fetch t.tallyMeasurements where "
              + "t.orgId = :orgId and "
              + "t.productId in (:productIds) and "
              + "t.granularity = :granularity  and "
              + "t.snapshotDate between :beginning and :ending "
              + "order by t.snapshotDate",
      countQuery =
          "SELECT count(t) FROM TallySnapshot t where "
              + "t.orgId = :orgId and "
              + "t.productId in (:productIds) and "
              + "t.granularity = :granularity  and "
              + "t.snapshotDate between :beginning and :ending ")
  Stream<TallySnapshot> findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
      String orgId,
      Collection<String> productIds,
      Granularity granularity,
      OffsetDateTime beginning,
      OffsetDateTime ending);

  void deleteByOrgId(String orgId);

  @SuppressWarnings("java:S107") // repository method has a lot of params, deal with it
  @Query(
      "select coalesce(sum(VALUE(m)), 0.0) from TallySnapshot s "
          + "left join s.tallyMeasurements m on key(m) = :measurementKey "
          + "where s.orgId = :orgId and "
          + "s.productId = :productId and "
          + "s.granularity = :granularity and "
          + "s.serviceLevel = :serviceLevel and "
          + "s.usage = :usage and "
          + "s.billingProvider = :billingProvider and "
          + "s.billingAccountId = :billingAcctId and "
          + "s.snapshotDate >= :beginning and s.snapshotDate <= :ending")
  Double sumMeasurementValueForPeriod(
      @Param("orgId") String orgId,
      @Param("productId") String productId,
      @Param("granularity") Granularity granularity,
      @Param("serviceLevel") ServiceLevel serviceLevel,
      @Param("usage") Usage usage,
      @Param("billingProvider") BillingProvider billingProvider,
      @Param("billingAcctId") String billingAccountId,
      @Param("beginning") OffsetDateTime beginning,
      @Param("ending") OffsetDateTime ending,
      @Param("measurementKey") TallyMeasurementKey measurementKey);

  @Query(
      nativeQuery = true,
      value =
          "select s.* from tally_snapshots s where id in "
              + "(select distinct first_value(s.id) over "
              + "(partition by s.account_number, s.sla, s.usage, s.billing_provider, s.billing_account_id, m.uom "
              + "order by s.snapshot_date desc) from tally_snapshots s "
              + "inner join tally_measurements m on s.id = m.snapshot_id where s.granularity='HOURLY' "
              + "and extract(month from s.snapshot_date) = :month and s.sla != '_ANY' and "
              + "s.usage != '_ANY' and s.billing_provider != '_ANY' and s.billing_account_id != '_ANY');")
  Stream<TallySnapshot> findLatestBillablesForMonth(@Param("month") int month);
}
