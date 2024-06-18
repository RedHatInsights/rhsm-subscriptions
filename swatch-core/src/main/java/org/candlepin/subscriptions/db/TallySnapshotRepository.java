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

import static org.hibernate.jpa.AvailableHints.HINT_FETCH_SIZE;
import static org.hibernate.jpa.AvailableHints.HINT_READ_ONLY;

import jakarta.persistence.QueryHint;
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
import org.springframework.data.jpa.repository.QueryHints;
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
          "delete from TallySnapshot where orgId in (select distinct c.orgId from OrgConfig c) and granularity=:granularity and snapshotDate < :cutoffDate")
  void deleteAllByGranularityAndSnapshotDateBefore(
      Granularity granularity, OffsetDateTime cutoffDate);

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
      value =
          "select coalesce(sum(m.value), 0.0) from tally_snapshots s "
              + "left join tally_measurements m on s.id = m.snapshot_id "
              + "where s.org_id = :orgId and "
              + "s.product_id = :productId and "
              + "s.granularity = :granularity and "
              + "s.sla = :serviceLevel and "
              + "s.usage = :usage and "
              + "s.billing_provider = :billingProvider and "
              + "s.billing_account_id = :billingAcctId and "
              + "m.measurement_type != 'TOTAL' and "
              + "m.metric_id = :metricId and "
              + "s.snapshot_date >= :beginning and s.snapshot_date <= :ending",
      nativeQuery = true)
  Double sumMeasurementValueForPeriod(
      @Param("orgId") String orgId,
      @Param("productId") String productId,
      @Param("granularity") String granularity,
      @Param("serviceLevel") String serviceLevel,
      @Param("usage") String usage,
      @Param("billingProvider") String billingProvider,
      @Param("billingAcctId") String billingAccountId,
      @Param("beginning") OffsetDateTime beginning,
      @Param("ending") OffsetDateTime ending,
      @Param("metricId") String metricId);

  @SuppressWarnings("java:S107")
  default Double sumMeasurementValueForPeriod(
      String orgId,
      String productId,
      Granularity granularity,
      ServiceLevel serviceLevel,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      TallyMeasurementKey measurementKey) {
    // NOTE(khowell) it's a mess with enums, some use .getValue() and some use .name()
    // Correct combination found by trial/error
    return sumMeasurementValueForPeriod(
        orgId,
        productId,
        granularity.name(),
        serviceLevel.getValue(),
        usage.getValue(),
        billingProvider.name(),
        billingAccountId,
        beginning,
        ending,
        measurementKey.getMetricId());
  }

  @SuppressWarnings("java:S107")
  @QueryHints(
      value = {
        @QueryHint(name = HINT_FETCH_SIZE, value = "1024"),
        @QueryHint(name = HINT_READ_ONLY, value = "true")
      })
  @Query(
      """
        select s from TallySnapshot s
        left join s.tallyMeasurements m on key(m) = :measurementKey
        where s.orgId = :orgId and
          s.productId = :productId and
          s.granularity = 'HOURLY' and
          s.serviceLevel = :serviceLevel and
          s.usage = :usage and
          s.billingProvider = :billingProvider and
          s.billingAccountId = :billingAcctId and
          s.snapshotDate >= :beginning and s.snapshotDate <= :ending order by s.snapshotDate
      """)
  Stream<TallySnapshot> getBillableSnapshots(
      @Param("orgId") String orgId,
      @Param("productId") String productId,
      @Param("serviceLevel") ServiceLevel serviceLevel,
      @Param("usage") Usage usage,
      @Param("billingProvider") BillingProvider billingProvider,
      @Param("billingAcctId") String billingAccountId,
      @Param("beginning") OffsetDateTime beginning,
      @Param("ending") OffsetDateTime ending,
      @Param("measurementKey") TallyMeasurementKey measurementKey);

  @SuppressWarnings("java:S107")
  @Query(
      value =
          """
    SELECT count(m) > 0 FROM TallySnapshot t
    left join t.tallyMeasurements m on key(m) = :measurementKey
    where
      t.orgId = :orgId and
      t.productId = :productId and
      t.granularity = 'HOURLY' and
      t.serviceLevel = :serviceLevel and
      t.usage = :usage and
      t.billingProvider = :billingProvider and
      t.billingAccountId = :billingAcctId and
      t.snapshotDate >= :beginning and t.snapshotDate <= :ending
  """)
  boolean hasLatestBillables(
      @Param("orgId") String orgId,
      @Param("productId") String productId,
      @Param("serviceLevel") ServiceLevel serviceLevel,
      @Param("usage") Usage usage,
      @Param("billingProvider") BillingProvider billingProvider,
      @Param("billingAcctId") String billingAccountId,
      @Param("beginning") OffsetDateTime beginning,
      @Param("ending") OffsetDateTime ending,
      @Param("measurementKey") TallyMeasurementKey measurementKey);
}
