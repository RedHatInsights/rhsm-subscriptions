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

import static org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE;

import jakarta.persistence.QueryHint;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Root;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.Measurement.Uom;
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
    extends JpaRepository<Host, UUID>,
        JpaSpecificationExecutor<Host>,
        TagProfileLookup,
        EntityManagerLookup {

  String BUCKET_JOIN = "bucket";
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
      Pageable pageable,
      Measurement.Uom referenceUom,
      String productId) {
    var entityManager = getEntityManager();
    var criteriaBuilder = entityManager.getCriteriaBuilder();
    var query = criteriaBuilder.createQuery(HostApiProjection.class);
    var countQuery = criteriaBuilder.createQuery(Long.class);
    var root = query.from(Host.class);
    var countRoot = countQuery.from(Host.class);
    createJoins(root);
    createJoins(countRoot);
    var bucketJoin = findJoin(root, BUCKET_JOIN);
    var coresMeasurementJoin = findMapJoin(root, MEASUREMENT_JOIN_CORES);
    var socketsMeasurementJoin = findMapJoin(root, MEASUREMENT_JOIN_SOCKETS);
    var coresMonthlyTotalsJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_CORES);
    var instanceHoursMonthlyTotalsJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_INSTANCE_HOURS);

    Uom effectiveUom = Optional.ofNullable(referenceUom).orElse(getDefaultUomForProduct(productId));

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
            bucketJoin.get(HostTallyBucket_.MEASUREMENT_TYPE),
            root.get(Host_.NUM_OF_GUESTS),
            root.get(Host_.LAST_SEEN),
            root.get(Host_.IS_UNMAPPED_GUEST),
            root.get(Host_.IS_HYPERVISOR),
            root.get(Host_.CLOUD_PROVIDER),
            root.get(Host_.BILLING_PROVIDER),
            root.get(Host_.BILLING_ACCOUNT_ID)));

    countQuery.select(criteriaBuilder.countDistinct(countRoot));

    if (specification != null) {
      var predicate = specification.toPredicate(root, query, criteriaBuilder);
      query.where(predicate);
      countQuery.where(predicate);
    }

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

  private void addSort(
      Root<Host> root,
      CriteriaQuery<HostApiProjection> query,
      CriteriaBuilder criteriaBuilder,
      Measurement.Uom effectiveUom,
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
      Measurement.Uom effectiveUom) {
    if (order.getProperty().equals(MONTHLY_TOTALS)) {
      var coresMonthlyTotalsJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_CORES);
      var instanceHoursMonthlyTotalsJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_INSTANCE_HOURS);
      if (Uom.CORES.equals(effectiveUom)) {
        query.orderBy(criteriaBuilder.asc(coresMonthlyTotalsJoin.value()));
      } else if (Uom.INSTANCE_HOURS.equals(effectiveUom)) {
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
      Measurement.Uom effectiveUom) {
    if (order.getProperty().equals(MONTHLY_TOTALS)) {
      var coresMonthlyTotalsJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_CORES);
      var instanceHoursMonthlyTotalsJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_INSTANCE_HOURS);
      if (Uom.CORES.equals(effectiveUom)) {
        query.orderBy(criteriaBuilder.asc(coresMonthlyTotalsJoin.value()));
      } else if (Uom.INSTANCE_HOURS.equals(effectiveUom)) {
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
   * @param referenceUom Uom used when filtering to a specific month.
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
      Uom referenceUom,
      BillingProvider billingProvider,
      String billingAccountId,
      List<HardwareMeasurementType> hardwareMeasurementTypes,
      Pageable pageable) {

    return findAllHostApiProjections(
        buildSearchSpecification(
            orgId,
            productId,
            sla,
            usage,
            displayNameSubstring,
            minCores,
            minSockets,
            month,
            referenceUom,
            billingProvider,
            billingAccountId,
            hardwareMeasurementTypes),
        pageable,
        referenceUom,
        productId);
  }

  static Specification<Host> productIdEquals(String productId) {
    return (root, query, builder) -> {
      Join<Host, HostTallyBucket> bucketJoin = findJoin(root, BUCKET_JOIN);
      var key = bucketJoin.get(HostTallyBucket_.key);
      return builder.equal(key.get(HostBucketKey_.productId), productId);
    };
  }

  static Specification<Host> slaEquals(ServiceLevel sla) {
    return (root, query, builder) -> {
      Join<Host, HostTallyBucket> bucketJoin = findJoin(root, BUCKET_JOIN);
      var key = bucketJoin.get(HostTallyBucket_.key);
      return builder.equal(key.get(HostBucketKey_.sla), sla);
    };
  }

  static Specification<Host> usageEquals(Usage usage) {
    return (root, query, builder) -> {
      Join<Host, HostTallyBucket> bucketJoin = findJoin(root, BUCKET_JOIN);
      var key = bucketJoin.get(HostTallyBucket_.key);
      return builder.equal(key.get(HostBucketKey_.usage), usage);
    };
  }

  static Specification<Host> billingProviderEquals(BillingProvider billingProvider) {
    return (root, query, builder) -> {
      Join<Host, HostTallyBucket> bucketJoin = findJoin(root, BUCKET_JOIN);
      var key = bucketJoin.get(HostTallyBucket_.key);
      return builder.equal(key.get(HostBucketKey_.billingProvider), billingProvider);
    };
  }

  static Specification<Host> billingAccountIdEquals(String billingAccountId) {
    return (root, query, builder) -> {
      Join<Host, HostTallyBucket> bucketJoin = findJoin(root, BUCKET_JOIN);
      var key = bucketJoin.get(HostTallyBucket_.key);
      return builder.equal(key.get(HostBucketKey_.billingAccountId), billingAccountId);
    };
  }

  static Specification<Host> orgEquals(String orgId) {
    return (root, query, builder) -> builder.equal(root.get(Host_.orgId), orgId);
  }

  static Specification<Host> socketsAndCoresGreaterThanOrEqualTo(int minCores, int minSockets) {
    return (root, query, builder) -> {
      Join<Host, HostTallyBucket> bucketJoin = findJoin(root, BUCKET_JOIN);
      return builder.and(
          builder.greaterThanOrEqualTo(bucketJoin.get(HostTallyBucket_.cores), minCores),
          builder.greaterThanOrEqualTo(bucketJoin.get(HostTallyBucket_.sockets), minSockets));
    };
  }

  static Specification<Host> hardwareMeasurementTypeIn(List<HardwareMeasurementType> types) {
    return (root, query, builder) -> {
      var bucketJoin = findJoin(root, BUCKET_JOIN);
      return bucketJoin.get(HostTallyBucket_.MEASUREMENT_TYPE).in(types);
    };
  }

  static Specification<Host> monthlyKeyEquals(InstanceMonthlyTotalKey totalKey) {
    return (root, query, builder) -> {
      MapJoin<Object, Object, Object> monthlyTotalJoin;
      if (totalKey.getUom().equals(Uom.CORES)) {
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
    root.join(Host_.buckets, JoinType.INNER).alias(BUCKET_JOIN);
    root.joinMap(Host_.MEASUREMENTS, JoinType.LEFT).alias(MEASUREMENT_JOIN_CORES);
    root.joinMap(Host_.MEASUREMENTS, JoinType.LEFT).alias(MEASUREMENT_JOIN_SOCKETS);
    root.joinMap(Host_.MONTHLY_TOTALS, JoinType.LEFT).alias(MONTHLY_TOTAL_JOIN_CORES);
    root.joinMap(Host_.MONTHLY_TOTALS, JoinType.LEFT).alias(MONTHLY_TOTAL_JOIN_INSTANCE_HOURS);
  }

  static Specification<Host> setJoinCriteria(String month) {
    return (root, query, builder) -> {
      var measurementCoresJoin = findMapJoin(root, MEASUREMENT_JOIN_CORES);
      measurementCoresJoin.on(builder.equal(measurementCoresJoin.key(), Uom.CORES));
      var measurementSocketsJoin = findMapJoin(root, MEASUREMENT_JOIN_SOCKETS);
      measurementSocketsJoin.on(builder.equal(measurementSocketsJoin.key(), Uom.SOCKETS));
      if (StringUtils.hasText(month)) {
        var monthlyTotalCoresJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_CORES);
        monthlyTotalCoresJoin.on(
            builder.equal(
                monthlyTotalCoresJoin.key(), new InstanceMonthlyTotalKey(month, Uom.CORES)));
        var monthlyTotalInstanceHoursJoin = findMapJoin(root, MONTHLY_TOTAL_JOIN_INSTANCE_HOURS);
        monthlyTotalInstanceHoursJoin.on(
            builder.equal(
                monthlyTotalInstanceHoursJoin.key(),
                new InstanceMonthlyTotalKey(month, Uom.INSTANCE_HOURS)));
      }
      return null;
    };
  }

  @SuppressWarnings("java:S107")
  default Specification<Host> buildSearchSpecification(
      String orgId,
      String productId,
      ServiceLevel sla,
      Usage usage,
      String displayNameSubstring,
      int minCores,
      int minSockets,
      String month,
      Uom referenceUom,
      BillingProvider billingProvider,
      String billingAccountId,
      List<HardwareMeasurementType> hardwareMeasurementTypes) {

    /* The where call allows us to build a Specification object to operate on even if the
     * first specification method we call returns null (it won't be null in this case, but it's
     * good practice to handle it) */
    var searchCriteria = Specification.where(setJoinCriteria(month));
    searchCriteria = searchCriteria.and(socketsAndCoresGreaterThanOrEqualTo(minCores, minSockets));

    if (Objects.nonNull(orgId)) {
      searchCriteria = searchCriteria.and(orgEquals(orgId));
    }
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
    if (Objects.nonNull(displayNameSubstring)) {
      searchCriteria = searchCriteria.and(displayNameContains(displayNameSubstring));
    }
    if (StringUtils.hasText(month)) {
      // Defaulting if null, since we need a UOM in order to properly filter against a given month
      Uom effectiveUom =
          Optional.ofNullable(referenceUom).orElse(getDefaultUomForProduct(productId));
      if (Objects.nonNull(effectiveUom)) {
        searchCriteria =
            searchCriteria.and(monthlyKeyEquals(new InstanceMonthlyTotalKey(month, effectiveUom)));
      }
    }
    if (!ObjectUtils.isEmpty(hardwareMeasurementTypes)) {
      searchCriteria = searchCriteria.and(hardwareMeasurementTypeIn(hardwareMeasurementTypes));
    }

    return searchCriteria;
  }

  default Uom getDefaultUomForProduct(String productId) {
    return Optional.ofNullable(getTagProfile().uomsForTag(productId)).orElse(List.of()).stream()
        .findFirst()
        .orElse(null);
  }

  @Query(
      "select distinct h from Host h where "
          + "h.orgId = :orgId and "
          + "h.hypervisorUuid = :hypervisor_id")
  Page<Host> getHostsByHypervisor(
      @Param("orgId") String orgId, @Param("hypervisor_id") String hypervisorId, Pageable pageable);

  @Query(
      "select distinct h1 from Host h1 where "
          + "h1.orgId = :orgId and "
          + "h1.hypervisorUuid in (select h2.subscriptionManagerId from Host h2 where "
          + "h2.instanceId = :instanceId)")
  Page<Host> getGuestHostsByHypervisorInstanceId(
      @Param("orgId") String orgId, @Param("instanceId") String instanceId, Pageable pageable);

  List<Host> findByAccountNumber(String accountNumber);

  Optional<Host> findById(UUID id);

  void deleteByOrgId(String orgId);
}
