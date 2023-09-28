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

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import jakarta.persistence.QueryHint;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/** Provides access to Host database entities. */
@SuppressWarnings({"linelength", "indentation"})
public interface HostRepository
    extends JpaRepository<Host, UUID>, JpaSpecificationExecutor<Host>, EntityManagerLookup {
  String MEASUREMENT_JOIN_CORES = "coresMeasurements";
  String MEASUREMENT_JOIN_SOCKETS = "socketsMeasurements";
  String MONTHLY_TOTAL_JOIN_CORES = "coresMonthlyTotal";
  String MONTHLY_TOTAL_JOIN_INSTANCE_HOURS = "instanceHoursMonthlyTotal";
  String MONTHLY_TOTALS = "monthlyTotals";

  /* NOTE: in below query, ordering is crucial for correct streaming reconciliation of HBI data */
  @Query(
      value =
          """
      select
      h from Host h
      left join fetch h.measurements
      left join fetch h.buckets
      left join fetch h.monthlyTotals
      where h.orgId=:orgId
        and h.instanceType='HBI_HOST'
      order by coalesce(h.hypervisorUuid, h.subscriptionManagerId), h.hypervisorUuid, h.inventoryId, h.id
          """)
  @QueryHints(value = {@QueryHint(name = HINT_FETCH_SIZE, value = "1024")})
  Stream<Host> streamHbiHostsByOrgId(@Param("orgId") String orgId);

  /**
   * Find all Hosts by bucket criteria and return a page of TallyHostView objects. A TallyHostView
   * is a Host representation detailing what 'bucket' was applied to the current daily snapshots.
   *
   * @param orgId The orgId of the hosts to query (required).
   * @param productId The bucket product ID to filter Host by (pass null to ignore).
   * @param sla The bucket service level to filter Hosts by (pass null to ignore).
   * @param usage The bucket usage to filter Hosts by (pass null to ignore).
   * @param billingProvider The bucket billingProvider to filter Hosts by (pass null to ignore).
   * @param billingAccountId The bucket billingAccountId to filter Hosts by (pass null to ignore).
   * @param displayNameSubstring Case-insensitive string to filter Hosts' display name by (pass null
   *     or empty string to ignore)
   * @param minCores Filter to Hosts with at least this number of cores.
   * @param minSockets Filter to Hosts with at least this number of sockets.
   * @param pageable the current paging info for this query.
   * @return a page of Host entities matching the criteria.
   */
  @SuppressWarnings("java:S107")
  @Query(
      value =
          "select b from HostTallyBucket b join fetch b.host h where "
              + "h.orgId = :orgId and "
              + "b.key.productId = :product and "
              + "b.key.sla = :sla and b.key.usage = :usage and "
              + "b.key.billingProvider = :billingProvider and "
              + "b.key.billingAccountId = :billingAccountId and "
              +
              // Have to do the null check first, otherwise the lower in the LIKE clause has issues
              // with datatypes
              "((lower(h.displayName) LIKE lower(concat('%', :displayNameSubstring,'%')))) and "
              + "b.cores >= :minCores and b.sockets >= :minSockets",
      // Because we are using a 'fetch join' to avoid having to lazy load each bucket host,
      // we need to specify how the Page should get its count when the 'limit' parameter
      // is used.
      countQuery =
          "select count(b) from HostTallyBucket b join b.host h where "
              + "h.orgId = :orgId and "
              + "b.key.productId = :product and "
              + "b.key.sla = :sla and b.key.usage = :usage and "
              + "b.key.sla = :sla and b.key.usage = :usage and "
              + "b.key.billingProvider = :billingProvider and "
              + "b.key.billingAccountId = :billingAccountId and "
              + "((lower(h.displayName) LIKE lower(concat('%', :displayNameSubstring,'%')))) and "
              + "b.cores >= :minCores and b.sockets >= :minSockets")
  Page<TallyHostView> getTallyHostViews(
      @Param("orgId") String orgId,
      @Param("product") String productId,
      @Param("sla") ServiceLevel sla,
      @Param("usage") Usage usage,
      @Param("billingProvider") BillingProvider billingProvider,
      @Param("billingAccountId") String billingAccountId,
      @NotNull @Param("displayNameSubstring") String displayNameSubstring,
      @Param("minCores") int minCores,
      @Param("minSockets") int minSockets,
      Pageable pageable);

  default Page<HostApiProjection> findAllHostApiProjections(
      Specification<Host> specification,
      Specification<HostTallyBucket> hostTallyBucketSpecification,
      Pageable pageable,
      MetricId referenceUom,
      String productId) {
    var entityManager = getEntityManager();
    var criteriaBuilder = entityManager.getCriteriaBuilder();
    var query = criteriaBuilder.createQuery(HostApiProjection.class);
    var countQuery = criteriaBuilder.createQuery(Long.class);
    var root = query.from(Host.class);
    var bucketRoot = query.from(HostTallyBucket.class);
    var countRoot = countQuery.from(Host.class);
    var countBucketRoot = countQuery.from(HostTallyBucket.class);
    createJoins(root);
    createJoins(countRoot);
    var coresMeasurementJoin = findMapJoin(root, MEASUREMENT_JOIN_CORES);
    var socketsMeasurementJoin = findMapJoin(root, MEASUREMENT_JOIN_SOCKETS);
    var coresMonthlyTotalsJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_CORES);
    var instanceHoursMonthlyTotalsJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_INSTANCE_HOURS);

    MetricId effectiveUom =
        Optional.ofNullable(referenceUom).orElse(getDefaultMetricIdForProduct(productId));

    query.select(
        criteriaBuilder.construct(
            HostApiProjection.class,
            root.get(Host_.INVENTORY_ID),
            root.get(Host_.INSIGHTS_ID),
            root.get(Host_.DISPLAY_NAME),
            root.get(Host_.SUBSCRIPTION_MANAGER_ID),
            socketsMeasurementJoin.value(),
            coresMeasurementJoin.value(),
            coresMonthlyTotalsJoin.value(),
            instanceHoursMonthlyTotalsJoin.value(),
            root.get(Host_.HARDWARE_TYPE),
            bucketRoot.get(HostTallyBucket_.MEASUREMENT_TYPE),
            root.get(Host_.NUM_OF_GUESTS),
            root.get(Host_.LAST_SEEN),
            root.get(Host_.IS_UNMAPPED_GUEST),
            root.get(Host_.IS_HYPERVISOR),
            root.get(Host_.CLOUD_PROVIDER),
            root.get(Host_.BILLING_PROVIDER),
            root.get(Host_.BILLING_ACCOUNT_ID)));

    countQuery.select(criteriaBuilder.countDistinct(countRoot));

    query.where(
        buildPredicate(
            specification, hostTallyBucketSpecification, criteriaBuilder, query, root, bucketRoot));
    countQuery.where(
        buildPredicate(
            specification,
            hostTallyBucketSpecification,
            criteriaBuilder,
            query,
            countRoot,
            countBucketRoot));

    addSort(root, query, criteriaBuilder, effectiveUom, pageable);

    List<HostApiProjection> resultList;
    Long countTotal;
    if (pageable.isPaged()) {
      resultList =
          entityManager
              .createQuery(query)
              .setFirstResult(pageable.getPageNumber() * pageable.getPageSize())
              .setMaxResults(pageable.getPageSize())
              .getResultList();
      countTotal = entityManager.createQuery(countQuery).getSingleResult();
    } else {
      resultList = entityManager.createQuery(query).getResultList();
      countTotal = (long) resultList.size();
    }

    return new PageImpl<>(resultList, pageable, countTotal);
  }

  private static Predicate buildPredicate(
      Specification<Host> specification,
      Specification<HostTallyBucket> hostTallyBucketSpecification,
      CriteriaBuilder criteriaBuilder,
      CriteriaQuery<HostApiProjection> query,
      Root<Host> root,
      Root<HostTallyBucket> bucketRoot) {
    var hostBucketPredicate = criteriaBuilder.equal(root, bucketRoot.get(HostTallyBucket_.host));
    if (specification != null) {
      return criteriaBuilder.and(
          hostBucketPredicate,
          specification.toPredicate(root, query, criteriaBuilder),
          hostTallyBucketSpecification.toPredicate(bucketRoot, query, criteriaBuilder));
    } else {
      return hostBucketPredicate;
    }
  }

  private void addSort(
      Root<Host> root,
      CriteriaQuery<HostApiProjection> query,
      CriteriaBuilder criteriaBuilder,
      MetricId effectiveUom,
      Pageable pageable) {
    var sort = pageable.getSort();
    var orderOptional = sort.get().findFirst();
    if (orderOptional.isPresent()) {
      var order = orderOptional.get();
      if (order.isAscending()) {
        setOrderByAsc(root, criteriaBuilder, query, order, effectiveUom);
      } else if (order.isDescending()) {
        setOrderByDesc(root, criteriaBuilder, query, order, effectiveUom);
      }
    }
  }

  private static void setOrderByAsc(
      Root<Host> root,
      CriteriaBuilder criteriaBuilder,
      CriteriaQuery<HostApiProjection> query,
      Sort.Order order,
      MetricId effectiveUom) {
    if (order.getProperty().equals(MONTHLY_TOTALS)) {
      var coresMonthlyTotalsJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_CORES);
      var instanceHoursMonthlyTotalsJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_INSTANCE_HOURS);
      if (MetricIdUtils.getCores().equals(effectiveUom)) {
        query.orderBy(criteriaBuilder.asc(coresMonthlyTotalsJoin.value()));
      } else if (MetricIdUtils.getInstanceHours().equals(effectiveUom)) {
        query.orderBy(criteriaBuilder.asc(instanceHoursMonthlyTotalsJoin.value()));
      }
    } else {
      query.orderBy(criteriaBuilder.asc(root.get(order.getProperty())));
    }
  }

  private static void setOrderByDesc(
      Root<Host> root,
      CriteriaBuilder criteriaBuilder,
      CriteriaQuery<HostApiProjection> query,
      Sort.Order order,
      MetricId effectiveUom) {
    if (order.getProperty().equals(MONTHLY_TOTALS)) {
      var coresMonthlyTotalsJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_CORES);
      var instanceHoursMonthlyTotalsJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_INSTANCE_HOURS);
      if (MetricIdUtils.getCores().equals(effectiveUom)) {
        query.orderBy(criteriaBuilder.asc(coresMonthlyTotalsJoin.value()));
      } else if (MetricIdUtils.getInstanceHours().equals(effectiveUom)) {
        query.orderBy(criteriaBuilder.asc(instanceHoursMonthlyTotalsJoin.value()));
      }
    } else {
      query.orderBy(criteriaBuilder.desc(root.get(order.getProperty())));
    }
  }

  static <T> Join<Host, T> findJoin(Root<Host> root, String alias) {
    for (Join<Host, ?> join : root.getJoins()) {
      if (join.getAlias().equals(alias)) {
        return (Join<Host, T>) join;
      }
    }
    throw new IllegalArgumentException("Cannot find join w/ alias: " + alias);
  }

  static <T, S, U> MapJoin<T, S, U> findMapJoin(Root<Host> root, String alias) {
    for (Join<Host, ?> join : root.getJoins()) {
      if (join.getAlias().equals(alias)) {
        return (MapJoin<T, S, U>) join;
      }
    }
    throw new IllegalArgumentException("Cannot find join w/ alias: " + alias);
  }

  /**
   * Find all Hosts by bucket criteria and return a page of TallyHostView objects. A TallyHostView
   * is a Host representation detailing what 'bucket' was applied to the current daily snapshots.
   *
   * @param orgId The organization ID of the hosts to query (required).
   * @param productId The bucket product ID to filter Host by (pass null to ignore).
   * @param sla The bucket service level to filter Hosts by (pass null to ignore).
   * @param usage The bucket usage to filter Hosts by (pass null to ignore).
   * @param displayNameSubstring Case-insensitive string to filter Hosts' display name by (pass null
   *     or empty string to ignore)
   * @param minCores Filter to Hosts with at least this number of cores.
   * @param minSockets Filter to Hosts with at least this number of sockets.
   * @param month Filter to Hosts with monthly instance totals in provided month
   * @param referenceUom MetricId used when filtering to a specific month.
   * @param pageable the current paging info for this query.
   * @return a page of Host entities matching the criteria.
   */
  @SuppressWarnings("java:S107")
  default Page<HostApiProjection> findAllBy(
      String orgId,
      String productId,
      ServiceLevel sla,
      Usage usage,
      @NotNull String displayNameSubstring,
      int minCores,
      int minSockets,
      String month,
      MetricId referenceUom,
      BillingProvider billingProvider,
      String billingAccountId,
      List<HardwareMeasurementType> hardwareMeasurementTypes,
      Pageable pageable) {

    return findAllHostApiProjections(
        buildSearchSpecification(orgId, productId, displayNameSubstring, month, referenceUom),
        buildBucketSpecification(
            productId,
            sla,
            usage,
            minCores,
            minSockets,
            billingProvider,
            billingAccountId,
            hardwareMeasurementTypes),
        pageable,
        referenceUom,
        productId);
  }

  static Specification<HostTallyBucket> productIdEquals(String productId) {
    return (root, query, builder) -> {
      var key = root.get(HostTallyBucket_.key);
      return builder.equal(key.get(HostBucketKey_.productId), productId);
    };
  }

  static Specification<HostTallyBucket> slaEquals(ServiceLevel sla) {
    return (root, query, builder) -> {
      var key = root.get(HostTallyBucket_.key);
      return builder.equal(key.get(HostBucketKey_.sla), sla);
    };
  }

  static Specification<HostTallyBucket> usageEquals(Usage usage) {
    return (root, query, builder) -> {
      var key = root.get(HostTallyBucket_.key);
      return builder.equal(key.get(HostBucketKey_.usage), usage);
    };
  }

  static Specification<HostTallyBucket> billingProviderEquals(BillingProvider billingProvider) {
    return (root, query, builder) -> {
      var key = root.get(HostTallyBucket_.key);
      return builder.equal(key.get(HostBucketKey_.billingProvider), billingProvider);
    };
  }

  static Specification<HostTallyBucket> billingAccountIdEquals(String billingAccountId) {
    return (root, query, builder) -> {
      var key = root.get(HostTallyBucket_.key);
      return builder.equal(key.get(HostBucketKey_.billingAccountId), billingAccountId);
    };
  }

  static Specification<Host> orgEquals(String orgId) {
    return (root, query, builder) -> builder.equal(root.get(Host_.orgId), orgId);
  }

  static Specification<HostTallyBucket> socketsAndCoresGreaterThanOrEqualTo(
      int minCores, int minSockets) {
    return (root, query, builder) ->
        builder.and(
            builder.greaterThanOrEqualTo(root.get(HostTallyBucket_.cores), minCores),
            builder.greaterThanOrEqualTo(root.get(HostTallyBucket_.sockets), minSockets));
  }

  static Specification<HostTallyBucket> hardwareMeasurementTypeIn(
      List<HardwareMeasurementType> types) {
    return (root, query, builder) -> root.get(HostTallyBucket_.MEASUREMENT_TYPE).in(types);
  }

  static Specification<Host> monthlyKeyEquals(InstanceMonthlyTotalKey totalKey) {
    return (root, query, builder) -> {
      MapJoin<Object, Object, Object> monthlyTotalJoin;
      if (totalKey.getMetricId().equals(MetricIdUtils.getCores().toString())) {
        monthlyTotalJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_CORES);
      } else {
        monthlyTotalJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_INSTANCE_HOURS);
      }
      return builder.equal(monthlyTotalJoin.key(), totalKey);
    };
  }

  static Specification<Host> displayNameContains(String displayNameSubstring) {
    return (root, query, builder) ->
        builder.like(
            builder.lower(root.get(Host_.displayName)),
            "%" + displayNameSubstring.toLowerCase() + "%");
  }

  private static void createJoins(Root<Host> root) {
    root.joinMap(Host_.MEASUREMENTS, JoinType.LEFT).alias(MEASUREMENT_JOIN_CORES);
    root.joinMap(Host_.MEASUREMENTS, JoinType.LEFT).alias(MEASUREMENT_JOIN_SOCKETS);
    root.joinMap(Host_.MONTHLY_TOTALS, JoinType.LEFT).alias(MONTHLY_TOTAL_JOIN_CORES);
    root.joinMap(Host_.MONTHLY_TOTALS, JoinType.LEFT).alias(MONTHLY_TOTAL_JOIN_INSTANCE_HOURS);
  }

  static Specification<Host> setJoinCriteria(String month) {
    return (root, query, builder) -> {
      var measurementCoresJoin = findMapJoin(root, MEASUREMENT_JOIN_CORES);
      measurementCoresJoin.on(
          builder.equal(measurementCoresJoin.key(), MetricIdUtils.getCores().toString()));
      var measurementSocketsJoin = findMapJoin(root, MEASUREMENT_JOIN_SOCKETS);
      measurementSocketsJoin.on(
          builder.equal(measurementSocketsJoin.key(), MetricIdUtils.getSockets().toString()));
      if (StringUtils.hasText(month)) {
        var monthlyTotalCoresJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_CORES);
        monthlyTotalCoresJoin.on(
            builder.equal(
                monthlyTotalCoresJoin.key(),
                new InstanceMonthlyTotalKey(month, MetricIdUtils.getCores().toString())));
        var monthlyTotalInstanceHoursJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_INSTANCE_HOURS);
        monthlyTotalInstanceHoursJoin.on(
            builder.equal(
                monthlyTotalInstanceHoursJoin.key(),
                new InstanceMonthlyTotalKey(month, MetricIdUtils.getInstanceHours().toString())));
      }
      return null;
    };
  }

  @SuppressWarnings("java:S107")
  default Specification<Host> buildSearchSpecification(
      String orgId,
      String productId,
      String displayNameSubstring,
      String month,
      MetricId referenceUom) {

    /* The where call allows us to build a Specification object to operate on even if the
     * first specification method we call returns null (it won't be null in this case, but it's
     * good practice to handle it) */
    var searchCriteria = Specification.where(setJoinCriteria(month));

    if (Objects.nonNull(orgId)) {
      searchCriteria = searchCriteria.and(orgEquals(orgId));
    }
    if (Objects.nonNull(displayNameSubstring)) {
      searchCriteria = searchCriteria.and(displayNameContains(displayNameSubstring));
    }
    if (StringUtils.hasText(month)) {
      // Defaulting if null, since we need a MetricId in order to properly filter against a given
      // month
      MetricId effectiveUom =
          Optional.ofNullable(referenceUom).orElse(getDefaultMetricIdForProduct(productId));
      if (Objects.nonNull(effectiveUom)) {
        searchCriteria =
            searchCriteria.and(
                monthlyKeyEquals(new InstanceMonthlyTotalKey(month, effectiveUom.toString())));
      }
    }

    return searchCriteria;
  }

  @SuppressWarnings("java:S107")
  default Specification<HostTallyBucket> buildBucketSpecification(
      String productId,
      ServiceLevel sla,
      Usage usage,
      int minCores,
      int minSockets,
      BillingProvider billingProvider,
      String billingAccountId,
      List<HardwareMeasurementType> hardwareMeasurementTypes) {
    var searchCriteria = socketsAndCoresGreaterThanOrEqualTo(minCores, minSockets);
    if (Objects.nonNull(productId)) {
      searchCriteria = searchCriteria.and(productIdEquals(productId));
    }
    if (Objects.nonNull(sla)) {
      searchCriteria = searchCriteria.and(slaEquals(sla));
    }
    if (Objects.nonNull(usage)) {
      searchCriteria = searchCriteria.and(usageEquals(usage));
    }
    if (Objects.nonNull(billingProvider)) {
      searchCriteria = searchCriteria.and(billingProviderEquals(billingProvider));
    }
    if (Objects.nonNull(billingAccountId)) {
      searchCriteria = searchCriteria.and(billingAccountIdEquals(billingAccountId));
    }
    if (!ObjectUtils.isEmpty(hardwareMeasurementTypes)) {
      searchCriteria = searchCriteria.and(hardwareMeasurementTypeIn(hardwareMeasurementTypes));
    }
    return searchCriteria;
  }

  default MetricId getDefaultMetricIdForProduct(String productId) {
    return MetricIdUtils.getMetricIdsFromConfigForTag(productId).findFirst().orElse(null);
  }

  @Query(
      "select distinct h1 from Host h1 where "
          + "h1.orgId = :orgId and "
          + "h1.hypervisorUuid in (select h2.subscriptionManagerId from Host h2 where "
          + "h2.instanceId = :instanceId)")
  Page<Host> getGuestHostsByHypervisorInstanceId(
      @Param("orgId") String orgId, @Param("instanceId") String instanceId, Pageable pageable);

  /**
   * We want to obtain the max last seen host record for the hourly tally. This helps in determining
   * whether we need to reevaluate the earlier event measurements.
   *
   * @param orgId
   * @param serviceType
   * @return
   */
  @Query(
      value =
          "select max(h.lastSeen) from Host h where h.orgId=:orgId and h.instanceType=:serviceType")
  Optional<OffsetDateTime> findMaxLastSeenDate(
      @Param("orgId") String orgId, @Param("serviceType") String serviceType);

  Optional<Host> findById(UUID id);

  void deleteByOrgId(String orgId);

  @Query
  Stream<Host> findAllByOrgIdAndInstanceIdIn(String orgId, Set<String> instanceIds);
}
