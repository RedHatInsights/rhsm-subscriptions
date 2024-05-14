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
import jakarta.persistence.criteria.JoinType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey_;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyInstanceView;
import org.candlepin.subscriptions.db.model.TallyInstanceViewKey_;
import org.candlepin.subscriptions.db.model.TallyInstanceView_;
import org.candlepin.subscriptions.db.model.TallyInstancesDbReportCriteria;
import org.candlepin.subscriptions.db.model.Usage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/** Provides access to TallyInstanceView database entities. */
@SuppressWarnings({"linelength", "indentation"})
public interface TallyInstanceViewRepository
    extends JpaRepository<TallyInstanceView, UUID>, JpaSpecificationExecutor<TallyInstanceView> {

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
   * @param pageable the current paging info for this query.
   * @return a page of Host entities matching the criteria.
   */
  @SuppressWarnings("java:S107")
  default Page<TallyInstanceView> findAllBy(
      String orgId,
      String productId,
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
      Pageable pageable) {
    return findAll(
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
                .build()),
        pageable);
  }

  default Stream<TallyInstanceView> streamBy(TallyInstancesDbReportCriteria criteria) {
    return findBy(buildSearchSpecification(criteria), FluentQuery.FetchableFluentQuery::stream);
  }

  static Specification<TallyInstanceView> socketsAndCoresGreaterThanOrEqualTo(
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
      } else return null;
    };
  }

  static Specification<TallyInstanceView> productIdEquals(String productId) {
    return (root, query, builder) -> {
      var key = root.get(TallyInstanceView_.key);
      return builder.equal(key.get(TallyInstanceViewKey_.productId), productId);
    };
  }

  static Specification<TallyInstanceView> slaEquals(ServiceLevel sla) {
    return (root, query, builder) -> {
      var key = root.get(TallyInstanceView_.key);
      return builder.equal(key.get(TallyInstanceViewKey_.sla), sla);
    };
  }

  static Specification<TallyInstanceView> usageEquals(Usage usage) {
    return (root, query, builder) -> {
      var key = root.get(TallyInstanceView_.key);
      return builder.equal(key.get(TallyInstanceViewKey_.usage), usage);
    };
  }

  static Specification<TallyInstanceView> billingProviderEquals(BillingProvider billingProvider) {
    return (root, query, builder) -> {
      var key = root.get(TallyInstanceView_.key);
      return builder.equal(key.get(TallyInstanceViewKey_.bucketBillingProvider), billingProvider);
    };
  }

  static Specification<TallyInstanceView> billingAccountIdEquals(String billingAccountId) {
    return (root, query, builder) -> {
      var key = root.get(TallyInstanceView_.key);
      return builder.equal(key.get(TallyInstanceViewKey_.bucketBillingAccountId), billingAccountId);
    };
  }

  static Specification<TallyInstanceView> orgEquals(String orgId) {
    return (root, query, builder) -> builder.equal(root.get(TallyInstanceView_.orgId), orgId);
  }

  static Specification<TallyInstanceView> hardwareMeasurementTypeIn(
      List<HardwareMeasurementType> types) {
    return (root, query, builder) -> {
      var key = root.get(TallyInstanceView_.key);
      return key.get(TallyInstanceViewKey_.measurementType).in(types);
    };
  }

  static Specification<TallyInstanceView> metricIdContains(MetricId effectiveMetricId) {
    return (root, query, builder) ->
        builder.like(
            builder.upper(builder.function("jsonb_pretty", String.class, root.get("metrics"))),
            "%" + effectiveMetricId.toUpperCaseFormatted() + "%");
  }

  static Specification<TallyInstanceView> displayNameContains(String displayNameSubstring) {
    return (root, query, builder) ->
        builder.like(
            builder.lower(root.get(TallyInstanceView_.displayName)),
            "%" + displayNameSubstring.toLowerCase() + "%");
  }

  static Specification<TallyInstanceView> monthlyKeyEquals(String month, MetricId metricId) {
    return (root, query, builder) -> {
      var instanceMonthlyTotalRoot = root.join(TallyInstanceView_.monthlyTotals, JoinType.LEFT);
      var monthPredicate =
          builder.equal(instanceMonthlyTotalRoot.key().get(InstanceMonthlyTotalKey_.MONTH), month);
      if (Objects.nonNull(metricId)) {
        return builder.and(
            monthPredicate,
            builder.equal(
                instanceMonthlyTotalRoot.key().get(InstanceMonthlyTotalKey_.METRIC_ID),
                metricId.toUpperCaseFormatted()));
      }

      return monthPredicate;
    };
  }

  static Specification<TallyInstanceView> distinct() {
    return (root, query, builder) -> {
      query.distinct(true);
      return null;
    };
  }

  @SuppressWarnings("java:S107")
  default Specification<TallyInstanceView> buildSearchSpecification(
      TallyInstancesDbReportCriteria criteria) {
    /* The where call allows us to build a Specification object to operate on even if the
     * first specification method we call returns null which is does because we're using the
     * Specification call to set the query to return distinct results */
    var searchCriteria = Specification.where(distinct());
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
      searchCriteria = searchCriteria.and(metricIdContains(criteria.getMetricId()));
    }
    if (StringUtils.hasText(criteria.getMonth())) {
      searchCriteria =
          searchCriteria.and(monthlyKeyEquals(criteria.getMonth(), criteria.getMetricId()));
    }
    if (!ObjectUtils.isEmpty(criteria.getHardwareMeasurementTypes())) {
      searchCriteria =
          searchCriteria.and(hardwareMeasurementTypeIn(criteria.getHardwareMeasurementTypes()));
    }

    return searchCriteria;
  }
}
