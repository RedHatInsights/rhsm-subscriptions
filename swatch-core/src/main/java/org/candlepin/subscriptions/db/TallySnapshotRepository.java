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

import com.redhat.swatch.configuration.registry.MetricId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.QueryHint;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Root;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyMeasurementAggregate;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Interface that Spring Data will turn into a DAO for us. */
public interface TallySnapshotRepository
    extends JpaRepository<TallySnapshot, UUID>,
        JpaSpecificationExecutor<TallySnapshot>,
        EntityManagerLookup {

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
  @SuppressWarnings("java:S107")
  Page<TallySnapshot> findSnapshot(
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
      nativeQuery = true,
      value =
          "delete from tally_snapshots where id in (select id from tally_snapshots where org_id=:orgId and granularity=:granularity and snapshot_date < :cutoffDate limit :limit)")
  int deleteAllByGranularityAndSnapshotDateBefore(
      @Param("orgId") String orgId,
      @Param("granularity") String granularity,
      @Param("cutoffDate") OffsetDateTime cutoffDate,
      @Param("limit") long limit);

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

  @SuppressWarnings("java:S107")
  @QueryHints(
      value = {
        @QueryHint(name = HINT_FETCH_SIZE, value = "1024"),
        @QueryHint(name = HINT_READ_ONLY, value = "true")
      })
  @Query(
      value =
          """
      select coalesce(sum(m.value), 0.0) from tally_snapshots s
      left join tally_measurements m on m.snapshot_id=s.id
      where s.org_id = :orgId and
        s.product_id = :productId and
        s.granularity = :granularity and
        s.sla = :serviceLevel and
        s.usage = :usage and
        s.billing_provider = :billingProvider and
        s.billing_account_id = :billingAcctId and
        s.snapshot_date >= :beginning and s.snapshot_date <= :ending and
        m.measurement_type != 'TOTAL' and m.metric_id = :metricId
      """,
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

  // Provided to allow passing enums instead of strings. Native queries need the String values
  // of enums, so this is a common place to do the required name vs value conversion.
  @SuppressWarnings("java:S107")
  default Double sumMeasurementValueForPeriod(
      @Param("orgId") String orgId,
      @Param("productId") String productId,
      @Param("granularity") Granularity granularity,
      @Param("serviceLevel") ServiceLevel serviceLevel,
      @Param("usage") Usage usage,
      @Param("billingProvider") BillingProvider billingProvider,
      @Param("billingAcctId") String billingAccountId,
      @Param("beginning") OffsetDateTime beginning,
      @Param("ending") OffsetDateTime ending,
      @Param("measurementKey") TallyMeasurementKey measurementKey) {
    return this.sumMeasurementValueForPeriod(
        orgId,
        productId,
        granularity.name(),
        serviceLevel.getValue(),
        usage.getValue(),
        billingProvider.getValue(),
        billingAccountId,
        beginning,
        ending,
        measurementKey.getMetricId());
  }

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

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Modifying
  @Query(
      """
    UPDATE TallySnapshot t
    SET t.isPrimary = true
    WHERE t.productId = :productId
      AND (COALESCE(:orgId, t.orgId) = t.orgId)
      AND t.snapshotDate >= :startDate
      AND t.snapshotDate < :endDate
      AND t.serviceLevel <> :anyServiceLevel
      AND t.usage <> :anyUsage
      AND t.billingProvider <> :anyBillingProvider
      AND t.billingAccountId <> '_ANY'
      AND t.isPrimary = false
  """)
  int setIsPrimaryForPayg(
      @Param("orgId") String orgId,
      @Param("productId") String productId,
      @Param("startDate") OffsetDateTime startDate,
      @Param("endDate") OffsetDateTime endDate,
      @Param("anyServiceLevel") ServiceLevel anyServiceLevel,
      @Param("anyUsage") Usage anyUsage,
      @Param("anyBillingProvider") BillingProvider anyBillingProvider);

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Modifying
  @Query(
      """
    UPDATE TallySnapshot t
    SET t.isPrimary = true
    WHERE t.productId = :productId
      AND (COALESCE(:orgId, t.orgId) = t.orgId)
      AND t.snapshotDate >= :startDate
      AND t.snapshotDate < :endDate
      AND t.serviceLevel <> :anyServiceLevel
      AND t.usage <> :anyUsage
      AND t.billingProvider = :anyBillingProvider
      AND t.billingAccountId = '_ANY'
      AND t.isPrimary = false
  """)
  int setIsPrimaryForNonPayg(
      @Param("orgId") String orgId,
      @Param("productId") String productId,
      @Param("startDate") OffsetDateTime startDate,
      @Param("endDate") OffsetDateTime endDate,
      @Param("anyServiceLevel") ServiceLevel anyServiceLevel,
      @Param("anyUsage") Usage anyUsage,
      @Param("anyBillingProvider") BillingProvider anyBillingProvider);

  /**
   * Finds aggregated measurements grouped by snapshot date, measurement type, and metric ID.
   *
   * <p>This method executes a query that: 1. Filters snapshots using the provided criteria 2.
   * Groups by (snapshotDate, measurementType, metricId) 3. Sums the measurement values
   *
   * <p>Method created with assistance from Claude Code
   *
   * @param isPrimary filter by isPrimary flag
   * @param orgId organization ID
   * @param productId product ID
   * @param granularity granularity
   * @param serviceLevel service level
   * @param usage usage type
   * @param billingProvider billing provider
   * @param billingAccountId billing account ID
   * @param beginning start date
   * @param ending end date
   * @param metricId metric ID to filter by
   * @param pageable pagination and sorting information (can be null for unpaged)
   * @return Page of aggregated measurements
   */
  @SuppressWarnings("java:S107")
  default Page<TallyMeasurementAggregate> findAggregatedMeasurements(
      Boolean isPrimary,
      String orgId,
      String productId,
      MetricId metricId,
      Granularity granularity,
      ServiceLevel serviceLevel,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      HardwareMeasurementType hardwareMeasurementType,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      Pageable pageable) {

    // Get EntityManager from JpaSpecificationExecutor
    EntityManager em = getEntityManager();
    CriteriaBuilder cb = em.getCriteriaBuilder();

    // Create CriteriaQuery for TallyMeasurementAggregate
    CriteriaQuery<TallyMeasurementAggregate> query =
        cb.createQuery(TallyMeasurementAggregate.class);
    Root<TallySnapshot> root = query.from(TallySnapshot.class);

    // Join to tallyMeasurements map
    MapJoin<TallySnapshot, TallyMeasurementKey, Double> measurementJoin =
        root.joinMap("tallyMeasurements", JoinType.LEFT);

    // Build and apply the specification
    Specification<TallySnapshot> spec =
        TallySnapshotSpecifications.buildAggregatedMeasurementSpec(
            measurementJoin,
            isPrimary,
            orgId,
            productId,
            metricId,
            granularity,
            serviceLevel,
            usage,
            billingProvider,
            billingAccountId,
            hardwareMeasurementType,
            beginning,
            ending);

    // Apply specification predicates and modifications to the query
    var predicate = spec.toPredicate(root, query, cb);
    if (predicate != null) {
      query.where(predicate);
    }

    // SELECT with aggregation
    query
        .multiselect(
            root.get("snapshotDate"),
            measurementJoin.key().get("measurementType"),
            measurementJoin.key().get("metricId"),
            cb.sum(measurementJoin.value()))
        .distinct(true);

    // GROUP BY
    query.groupBy(
        root.get("snapshotDate"),
        measurementJoin.key().get("measurementType"),
        measurementJoin.key().get("metricId"));

    // Create typed query
    var typedQuery = em.createQuery(query);

    // Apply pagination if provided
    if (pageable != null && pageable.isPaged()) {
      typedQuery.setFirstResult((int) pageable.getOffset());
      typedQuery.setMaxResults(pageable.getPageSize());
    }

    // Execute query to get results
    List<TallyMeasurementAggregate> results = typedQuery.getResultList();

    // Get accurate total count if pagination is requested
    long total = results.size();
    if (pageable != null && pageable.isPaged()) {
      total =
          countAggregatedMeasurements(
              isPrimary,
              orgId,
              productId,
              metricId,
              granularity,
              serviceLevel,
              usage,
              billingProvider,
              billingAccountId,
              hardwareMeasurementType,
              beginning,
              ending);
    }

    return new PageImpl<>(results, pageable != null ? pageable : Pageable.unpaged(), total);
  }

  /**
   * Counts the total number of aggregated measurement groups.
   *
   * <p>This executes a count query that accounts for GROUP BY, returning the number of distinct
   * groups that match the criteria.
   *
   * <p>Method created with assistance from Claude Code
   *
   * @param isPrimary filter by isPrimary flag
   * @param orgId organization ID
   * @param productId product ID
   * @param granularity granularity
   * @param serviceLevel service level
   * @param usage usage type
   * @param billingProvider billing provider
   * @param billingAccountId billing account ID
   * @param beginning start date
   * @param ending end date
   * @param metricId metric ID to filter by
   * @return Total count of aggregated measurement groups
   */
  @SuppressWarnings("java:S107")
  default long countAggregatedMeasurements(
      Boolean isPrimary,
      String orgId,
      String productId,
      MetricId metricId,
      Granularity granularity,
      ServiceLevel serviceLevel,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      HardwareMeasurementType hardwareMeasurementType,
      OffsetDateTime beginning,
      OffsetDateTime ending) {

    // Get EntityManager
    EntityManager em = getEntityManager();
    CriteriaBuilder cb = em.getCriteriaBuilder();

    // For GROUP BY queries, we need to count distinct groups
    // Create a query that selects the grouped tuples and count the results
    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<TallySnapshot> root = countQuery.from(TallySnapshot.class);
    countQuery.select(cb.countDistinct(root.get("snapshotDate")));

    // Join to tallyMeasurements map
    MapJoin<TallySnapshot, TallyMeasurementKey, Double> measurementJoin =
        root.joinMap("tallyMeasurements", JoinType.LEFT);

    // Build the specification for filtering
    Specification<TallySnapshot> spec =
        TallySnapshotSpecifications.buildAggregatedMeasurementSpec(
            measurementJoin,
            isPrimary,
            orgId,
            productId,
            metricId,
            granularity,
            serviceLevel,
            usage,
            billingProvider,
            billingAccountId,
            hardwareMeasurementType,
            beginning,
            ending);

    // Apply WHERE clause predicates
    var predicate = spec.toPredicate(root, countQuery, cb);
    if (predicate != null) {
      countQuery.where(predicate);
    }

    // Execute and count the results
    return em.createQuery(countQuery).getSingleResult();
  }
}
