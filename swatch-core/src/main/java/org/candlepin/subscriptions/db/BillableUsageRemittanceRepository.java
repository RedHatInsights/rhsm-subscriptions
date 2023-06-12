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
import java.util.List;
import java.util.Objects;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK_;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity_;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.RemittanceSummaryProjection;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface BillableUsageRemittanceRepository
    extends JpaRepository<BillableUsageRemittanceEntity, BillableUsageRemittanceEntityPK>,
        JpaSpecificationExecutor<BillableUsageRemittanceEntity>,
        EntityManagerLookup {

  @Query
  void deleteByKeyOrgId(String orgId);

  default boolean existsBy(BillableUsageRemittanceFilter filter) {
    return this.exists(buildSearchSpecification(filter));
  }

  default List<BillableUsageRemittanceEntity> filterBy(BillableUsageRemittanceFilter filter) {
    return this.findAll(buildSearchSpecification(filter));
  }

  default List<RemittanceSummaryProjection> getRemittanceSummaries(
      BillableUsageRemittanceFilter filter) {
    var entityManager = getEntityManager();
    var criteriaBuilder = entityManager.getCriteriaBuilder();
    var query = criteriaBuilder.createQuery(RemittanceSummaryProjection.class);
    var root = query.from(BillableUsageRemittanceEntity.class);
    var key = root.get(BillableUsageRemittanceEntity_.key);
    var specification = buildSearchSpecification(filter);
    if (specification != null) {
      var predicate = specification.toPredicate(root, query, criteriaBuilder);
      query.where(predicate);
      query.groupBy(
          key.get(BillableUsageRemittanceEntityPK_.ACCUMULATION_PERIOD),
          root.get(BillableUsageRemittanceEntity_.ACCOUNT_NUMBER),
          key.get(BillableUsageRemittanceEntityPK_.BILLING_PROVIDER),
          key.get(BillableUsageRemittanceEntityPK_.BILLING_ACCOUNT_ID),
          key.get(BillableUsageRemittanceEntityPK_.METRIC_ID),
          key.get(BillableUsageRemittanceEntityPK_.ORG_ID),
          key.get(BillableUsageRemittanceEntityPK_.PRODUCT_ID));
    }
    query.select(
        criteriaBuilder.construct(
            RemittanceSummaryProjection.class,
            criteriaBuilder.sum(root.get(BillableUsageRemittanceEntity_.REMITTED_PENDING_VALUE)),
            key.get(BillableUsageRemittanceEntityPK_.ORG_ID),
            root.get(BillableUsageRemittanceEntity_.ACCOUNT_NUMBER),
            key.get(BillableUsageRemittanceEntityPK_.PRODUCT_ID),
            key.get(BillableUsageRemittanceEntityPK_.ACCUMULATION_PERIOD),
            criteriaBuilder.max(key.get(BillableUsageRemittanceEntityPK_.REMITTANCE_PENDING_DATE)),
            key.get(BillableUsageRemittanceEntityPK_.BILLING_PROVIDER),
            key.get(BillableUsageRemittanceEntityPK_.BILLING_ACCOUNT_ID),
            key.get(BillableUsageRemittanceEntityPK_.METRIC_ID)));
    return entityManager.createQuery(query).getResultList();
  }

  static Specification<BillableUsageRemittanceEntity> matchingBillingProvider(
      String billingProvider) {
    return (root, query, builder) -> {
      var path = root.get(BillableUsageRemittanceEntity_.key);
      return builder.equal(
          path.get(BillableUsageRemittanceEntityPK_.billingProvider), billingProvider);
    };
  }

  static Specification<BillableUsageRemittanceEntity> matchingBillingAccountId(
      String billingAccountId) {
    return (root, query, builder) -> {
      var path = root.get(BillableUsageRemittanceEntity_.key);
      return builder.equal(
          path.get(BillableUsageRemittanceEntityPK_.billingAccountId), billingAccountId);
    };
  }

  static Specification<BillableUsageRemittanceEntity> matchingMetricId(String metricId) {
    return (root, query, builder) -> {
      var path = root.get(BillableUsageRemittanceEntity_.key);
      return builder.equal(path.get(BillableUsageRemittanceEntityPK_.metricId), metricId);
    };
  }

  static Specification<BillableUsageRemittanceEntity> matchingProductId(String productId) {
    return (root, query, builder) -> {
      var path = root.get(BillableUsageRemittanceEntity_.key);
      return builder.equal(path.get(BillableUsageRemittanceEntityPK_.productId), productId);
    };
  }

  static Specification<BillableUsageRemittanceEntity> matchingOrgId(String orgId) {
    return (root, query, builder) -> {
      var path = root.get(BillableUsageRemittanceEntity_.key);
      return builder.equal(path.get(BillableUsageRemittanceEntityPK_.orgId), orgId);
    };
  }

  static Specification<BillableUsageRemittanceEntity> matchingAccountNumber(String account) {
    return (root, query, builder) ->
        builder.equal(root.get(BillableUsageRemittanceEntity_.accountNumber), account);
  }

  static Specification<BillableUsageRemittanceEntity> matchingGranularity(Granularity granularity) {
    return (root, query, builder) -> {
      var path = root.get(BillableUsageRemittanceEntity_.key);
      return builder.equal(path.get(BillableUsageRemittanceEntityPK_.granularity), granularity);
    };
  }

  static Specification<BillableUsageRemittanceEntity> beforeRemittanceDate(OffsetDateTime ending) {
    return (root, query, builder) -> {
      var path = root.get(BillableUsageRemittanceEntity_.key);
      return builder.lessThanOrEqualTo(
          path.get(BillableUsageRemittanceEntityPK_.remittancePendingDate), ending);
    };
  }

  static Specification<BillableUsageRemittanceEntity> afterRemittanceDate(
      OffsetDateTime beginning) {
    return (root, query, builder) -> {
      var path = root.get(BillableUsageRemittanceEntity_.key);
      return builder.greaterThanOrEqualTo(
          path.get(BillableUsageRemittanceEntityPK_.remittancePendingDate), beginning);
    };
  }

  static Specification<BillableUsageRemittanceEntity> matchingAccumulationPeriod(
      String accumulationPeriod) {
    return (root, query, builder) -> {
      var path = root.get(BillableUsageRemittanceEntity_.key);
      return builder.equal(
          path.get(BillableUsageRemittanceEntityPK_.ACCUMULATION_PERIOD), accumulationPeriod);
    };
  }

  default Specification<BillableUsageRemittanceEntity> buildSearchSpecification(
      BillableUsageRemittanceFilter filter) {

    var searchCriteria = Specification.<BillableUsageRemittanceEntity>where(null);
    if (Objects.nonNull(filter.getAccount())) {
      searchCriteria = searchCriteria.and(matchingAccountNumber(filter.getAccount()));
    }
    if (Objects.nonNull(filter.getProductId())) {
      searchCriteria = searchCriteria.and(matchingProductId(filter.getProductId()));
    }
    if (Objects.nonNull(filter.getMetricId())) {
      searchCriteria = searchCriteria.and(matchingMetricId(filter.getMetricId()));
    }
    if (Objects.nonNull(filter.getBillingProvider())) {
      searchCriteria = searchCriteria.and(matchingBillingProvider(filter.getBillingProvider()));
    }
    if (Objects.nonNull(filter.getBillingAccountId())) {
      searchCriteria = searchCriteria.and(matchingBillingAccountId(filter.getBillingAccountId()));
    }
    if (Objects.nonNull(filter.getOrgId())) {
      searchCriteria = searchCriteria.and(matchingOrgId(filter.getOrgId()));
    }
    if (Objects.nonNull(filter.getBeginning())) {
      searchCriteria = searchCriteria.and(afterRemittanceDate(filter.getBeginning()));
    }
    if (Objects.nonNull(filter.getEnding())) {
      searchCriteria = searchCriteria.and(beforeRemittanceDate(filter.getEnding()));
    }
    if (Objects.nonNull(filter.getAccumulationPeriod())) {
      searchCriteria =
          searchCriteria.and(matchingAccumulationPeriod(filter.getAccumulationPeriod()));
    }
    if (Objects.nonNull(filter.getGranularity())) {
      searchCriteria = searchCriteria.and(matchingGranularity(filter.getGranularity()));
    }
    return searchCriteria;
  }
}
