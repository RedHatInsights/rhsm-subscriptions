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

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyInstanceNonPaygView_;
import org.candlepin.subscriptions.db.model.TallyInstancePaygView_;
import org.candlepin.subscriptions.db.model.TallyInstanceView;
import org.candlepin.subscriptions.db.model.TallyInstanceViewKey_;
import org.candlepin.subscriptions.db.model.TallyInstanceView_;
import org.candlepin.subscriptions.db.model.TallyInstancesDbReportCriteria;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.utilization.api.v1.model.SortDirection;
import org.hibernate.query.criteria.JpaOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

@SuppressWarnings({"linelength", "indentation", "unchecked"})
@Repository
@AllArgsConstructor
public class TallyInstanceViewRepository {

  public static final Map<String, String> FIELD_SORT_PARAM_MAPPING =
      Map.of(
          "display_name",
          TallyInstanceView_.DISPLAY_NAME,
          "last_seen",
          TallyInstanceView_.LAST_SEEN,
          "billing_provider",
          TallyInstanceView_.HOST_BILLING_PROVIDER,
          "number_of_guests",
          TallyInstanceView_.NUM_OF_GUESTS,
          "category",
          TallyInstanceViewKey_.MEASUREMENT_TYPE);

  public static final Map<String, String> FIELD_SORT_PARAM_MAPPING_FOR_NON_PAYG =
      Map.of("sockets", TallyInstanceView_.SOCKETS, "cores", TallyInstanceView_.CORES);

  private final TallyInstancePaygViewRepository paygViewRepository;
  private final TallyInstanceNonPaygViewRepository nonPaygViewRepository;

  /**
   * Find all Hosts by bucket criteria and return a page of TallyInstanceView objects. A
   * TallyInstanceView is a Host representation detailing what 'bucket' was applied to the current
   * daily snapshots.
   *
   * @param orgId The organization ID of the hosts to query (required).
   * @param productId The bucket product ID to filter Host by (pass null to ignore).
   * @param sla The bucket service level to filter Hosts by (pass null to ignore).
   * @param usage The bucket usage to filter Hosts by (pass null to ignore).
   * @param displayNameSubstring Case-insensitive string to filter Hosts' display name by (pass null
   *     or empty string to ignore)
   * @param minCores Filter to Hosts with at least this number of cores.
   * @param minSockets Filter to Hosts with at least this number of sockets.
   * @param month Filter to Hosts with with monthly instance totals in provided month
   * @param referenceMetricId Metric ID used when filtering to a specific month.
   * @return a page of Host entities matching the criteria.
   */
  @SuppressWarnings("java:S107")
  public Page<TallyInstanceView> findAllBy(
      String orgId,
      ProductId productId,
      ServiceLevel sla,
      Usage usage,
      String displayNameSubstring,
      Integer minCores,
      Integer minSockets,
      String month,
      MetricId referenceMetricId,
      BillingProvider billingProvider,
      String billingAccountId,
      List<HardwareMeasurementType> hardwareMeasurementTypes,
      Integer offset,
      Integer limit,
      String sort,
      SortDirection dir) {
    var repository = productId.isPayg() ? paygViewRepository : nonPaygViewRepository;
    return (Page<TallyInstanceView>)
        repository.findAll(
            buildSearchSpecification(
                TallyInstancesDbReportCriteria.builder()
                    .orgId(orgId)
                    .productId(productId)
                    .sla(sla)
                    .usage(usage)
                    .displayNameSubstring(displayNameSubstring)
                    .minCores(minCores)
                    .minSockets(minSockets)
                    .month(month)
                    .metricId(referenceMetricId)
                    .billingProvider(billingProvider)
                    .billingAccountId(billingAccountId)
                    .hardwareMeasurementTypes(hardwareMeasurementTypes)
                    .sort(sort)
                    .sortDirection(dir)
                    .build()),
            ResourceUtils.getPageable(offset, limit));
  }

  static <T extends TallyInstanceView> Specification<T> socketsAndCoresGreaterThanOrEqualTo(
      Integer minCores, Integer minSockets) {
    return (root, query, builder) -> {
      if (Objects.nonNull(minCores) && Objects.nonNull(minSockets)) {
        return builder.and(
            builder.greaterThanOrEqualTo(root.get(TallyInstanceView_.cores), minCores),
            builder.greaterThanOrEqualTo(root.get(TallyInstanceView_.sockets), minSockets));
      } else if (Objects.nonNull(minCores)) {
        return builder.greaterThanOrEqualTo(root.get(TallyInstanceView_.cores), minCores);
      } else if (Objects.nonNull(minSockets)) {
        return builder.greaterThanOrEqualTo(root.get(TallyInstanceView_.sockets), minSockets);
      } else {
        return null;
      }
    };
  }

  static <T extends TallyInstanceView> Specification<T> productIdEquals(ProductId productId) {
    return (root, query, builder) -> {
      var key = root.get(TallyInstanceView_.key);
      return builder.equal(key.get(TallyInstanceViewKey_.productId), productId.toString());
    };
  }

  static <T extends TallyInstanceView> Specification<T> slaEquals(ServiceLevel sla) {
    return (root, query, builder) -> {
      var key = root.get(TallyInstanceView_.key);
      return builder.equal(key.get(TallyInstanceViewKey_.sla), sla);
    };
  }

  static <T extends TallyInstanceView> Specification<T> usageEquals(Usage usage) {
    return (root, query, builder) -> {
      var key = root.get(TallyInstanceView_.key);
      return builder.equal(key.get(TallyInstanceViewKey_.usage), usage);
    };
  }

  static <T extends TallyInstanceView> Specification<T> billingProviderEquals(
      BillingProvider billingProvider) {
    return (root, query, builder) -> {
      var key = root.get(TallyInstanceView_.key);
      return builder.equal(key.get(TallyInstanceViewKey_.bucketBillingProvider), billingProvider);
    };
  }

  static <T extends TallyInstanceView> Specification<T> billingAccountIdEquals(
      String billingAccountId) {
    return (root, query, builder) -> {
      var key = root.get(TallyInstanceView_.key);
      return builder.equal(key.get(TallyInstanceViewKey_.bucketBillingAccountId), billingAccountId);
    };
  }

  static <T extends TallyInstanceView> Specification<T> orgEquals(String orgId) {
    return (root, query, builder) -> builder.equal(root.get(TallyInstanceView_.orgId), orgId);
  }

  static <T extends TallyInstanceView> Specification<T> hardwareMeasurementTypeIn(
      List<HardwareMeasurementType> types) {
    return (root, query, builder) -> {
      var key = root.get(TallyInstanceView_.key);
      return key.get(TallyInstanceViewKey_.measurementType).in(types);
    };
  }

  static <T extends TallyInstanceView> Specification<T> metricIdContains(
      MetricId effectiveMetricId, boolean isPayg) {
    return (root, query, builder) -> {
      var measurements = leftJoinMetricsBy(effectiveMetricId, isPayg, root, builder);
      return builder.isNotNull(measurements.value());
    };
  }

  static <T extends TallyInstanceView> Specification<T> displayNameContains(
      String displayNameSubstring) {
    return (root, query, builder) ->
        builder.like(
            builder.lower(root.get(TallyInstanceView_.displayName)),
            "%" + displayNameSubstring.toLowerCase() + "%");
  }

  static <T extends TallyInstanceView> Specification<T> monthEquals(String month) {
    return (root, query, builder) -> builder.equal(root.get(TallyInstancePaygView_.MONTH), month);
  }

  static <T extends TallyInstanceView> Specification<T> orderBy(
      String sort, SortDirection sortDirection, boolean isPayg) {
    return (root, query, builder) -> {
      boolean isAscending = sortDirection == null || SortDirection.ASC.equals(sortDirection);
      List<Order> orders = new ArrayList<>();
      String column = findColumnToSort(sort.toLowerCase(), isPayg);
      Path<T> path = root;
      if (TallyInstanceViewKey_.MEASUREMENT_TYPE.equals(column)) {
        path = root.get(TallyInstanceView_.KEY);
      }

      if (column != null) {
        orders.add(orderByColumn(builder, path.get(column), isAscending));
      } else {
        // try the metrics
        getMetricIdToSort(sort)
            .ifPresent(
                metricId -> {
                  var metrics = leftJoinMetricsBy(metricId, isPayg, root, builder);
                  orders.add(orderByColumn(builder, metrics.value(), isAscending));
                });
      }

      orders.add(builder.asc(root.get(TallyInstanceView_.ID)));
      query.orderBy(orders);
      return query.getRestriction();
    };
  }

  private static String findColumnToSort(String sort, boolean isPayg) {
    String column = FIELD_SORT_PARAM_MAPPING.get(sort);
    if (column == null && !isPayg) {
      return FIELD_SORT_PARAM_MAPPING_FOR_NON_PAYG.get(sort);
    }

    return column;
  }

  private static JpaOrder orderByColumn(
      CriteriaBuilder builder, Path<Object> column, boolean isAscending) {
    if (isAscending) {
      return ((JpaOrder) builder.asc(column)).nullPrecedence(Nulls.FIRST);
    }

    return ((JpaOrder) builder.desc(column)).nullPrecedence(Nulls.LAST);
  }

  private static MapJoin<Object, Object, Object> leftJoinMetricsBy(
      MetricId effectiveMetricId, boolean isPayg, Root<?> root, CriteriaBuilder builder) {
    var metrics =
        root.joinMap(
            isPayg
                ? TallyInstancePaygView_.filteredMetrics.getName()
                : TallyInstanceNonPaygView_.filteredMetrics.getName(),
            JoinType.LEFT);
    metrics.on(builder.equal(metrics.key(), effectiveMetricId.toUpperCaseFormatted()));
    return metrics;
  }

  @SuppressWarnings("java:S107")
  public static <T extends TallyInstanceView> Specification<T> buildSearchSpecification(
      TallyInstancesDbReportCriteria criteria) {
    var searchCriteria = Specification.<T>where((root, query, builder) -> null);
    searchCriteria =
        searchCriteria.and(
            socketsAndCoresGreaterThanOrEqualTo(criteria.getMinCores(), criteria.getMinSockets()));

    if (Objects.nonNull(criteria.getOrgId())) {
      searchCriteria = searchCriteria.and(orgEquals(criteria.getOrgId()));
    }
    if (Objects.nonNull(criteria.getProductId())) {
      searchCriteria = searchCriteria.and(productIdEquals(criteria.getProductId()));
    }
    if (Objects.nonNull(criteria.getSla())) {
      searchCriteria = searchCriteria.and(slaEquals(criteria.getSla()));
    }
    if (Objects.nonNull(criteria.getUsage())) {
      searchCriteria = searchCriteria.and(usageEquals(criteria.getUsage()));
    }
    if (Objects.nonNull(criteria.getBillingProvider())) {
      searchCriteria = searchCriteria.and(billingProviderEquals(criteria.getBillingProvider()));
    }
    if (Objects.nonNull(criteria.getBillingAccountId())) {
      searchCriteria = searchCriteria.and(billingAccountIdEquals(criteria.getBillingAccountId()));
    }
    if (StringUtils.hasText(criteria.getDisplayNameSubstring())) {
      searchCriteria = searchCriteria.and(displayNameContains(criteria.getDisplayNameSubstring()));
    }
    if (Objects.nonNull(criteria.getMetricId())) {
      searchCriteria =
          searchCriteria.and(
              metricIdContains(criteria.getMetricId(), criteria.getProductId().isPayg()));
    }
    if (criteria.getProductId().isPayg() && StringUtils.hasText(criteria.getMonth())) {
      searchCriteria = searchCriteria.and(monthEquals(criteria.getMonth()));
    }
    if (!ObjectUtils.isEmpty(criteria.getHardwareMeasurementTypes())) {
      searchCriteria =
          searchCriteria.and(hardwareMeasurementTypeIn(criteria.getHardwareMeasurementTypes()));
    }
    if (criteria.getSort() != null) {
      searchCriteria =
          searchCriteria.and(
              orderBy(
                  criteria.getSort(),
                  criteria.getSortDirection(),
                  criteria.getProductId().isPayg()));
    }

    return searchCriteria;
  }

  private static Optional<MetricId> getMetricIdToSort(String sort) {
    try {
      return Optional.of(MetricId.fromString(sort));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
