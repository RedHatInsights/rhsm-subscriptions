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
package com.redhat.swatch.billable.usage.data;

import com.redhat.swatch.panache.PanacheSpecificationSupport;
import com.redhat.swatch.panache.Specification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class BillableUsageRemittanceRepository
    implements PanacheSpecificationSupport<BillableUsageRemittanceEntity, UUID> {

  public Optional<BillableUsageRemittanceEntity> findOne(BillableUsageRemittanceFilter filter) {
    return findOne(BillableUsageRemittanceEntity.class, buildSearchSpecification(filter));
  }

  public List<BillableUsageRemittanceEntity> find(BillableUsageRemittanceFilter filter) {
    return find(BillableUsageRemittanceEntity.class, buildSearchSpecification(filter));
  }

  public List<RemittanceSummaryProjection> getRemittanceSummaries(
      BillableUsageRemittanceFilter filter) {
    var entityManager = getEntityManager();
    var criteriaBuilder = entityManager.getCriteriaBuilder();
    var query = criteriaBuilder.createQuery(RemittanceSummaryProjection.class);
    var root = query.from(BillableUsageRemittanceEntity.class);
    var specification = buildSearchSpecification(filter);
    if (specification != null) {
      var predicate = specification.toPredicate(root, query, criteriaBuilder);
      query.where(predicate);
      query.groupBy(
          root.get(BillableUsageRemittanceEntity_.ACCUMULATION_PERIOD),
          root.get(BillableUsageRemittanceEntity_.SLA),
          root.get(BillableUsageRemittanceEntity_.USAGE),
          root.get(BillableUsageRemittanceEntity_.BILLING_PROVIDER),
          root.get(BillableUsageRemittanceEntity_.BILLING_ACCOUNT_ID),
          root.get(BillableUsageRemittanceEntity_.METRIC_ID),
          root.get(BillableUsageRemittanceEntity_.ORG_ID),
          root.get(BillableUsageRemittanceEntity_.PRODUCT_ID),
          root.get(BillableUsageRemittanceEntity_.HARDWARE_MEASUREMENT_TYPE),
          root.get(BillableUsageRemittanceEntity_.STATUS));
    }
    query.select(
        criteriaBuilder.construct(
            RemittanceSummaryProjection.class,
            criteriaBuilder.sum(root.get(BillableUsageRemittanceEntity_.REMITTED_PENDING_VALUE)),
            root.get(BillableUsageRemittanceEntity_.ORG_ID),
            root.get(BillableUsageRemittanceEntity_.PRODUCT_ID),
            root.get(BillableUsageRemittanceEntity_.ACCUMULATION_PERIOD),
            root.get(BillableUsageRemittanceEntity_.SLA),
            root.get(BillableUsageRemittanceEntity_.USAGE),
            criteriaBuilder.max(root.get(BillableUsageRemittanceEntity_.REMITTANCE_PENDING_DATE)),
            root.get(BillableUsageRemittanceEntity_.BILLING_PROVIDER),
            root.get(BillableUsageRemittanceEntity_.BILLING_ACCOUNT_ID),
            root.get(BillableUsageRemittanceEntity_.METRIC_ID),
            root.get(BillableUsageRemittanceEntity_.HARDWARE_MEASUREMENT_TYPE),
            root.get(BillableUsageRemittanceEntity_.STATUS)));
    return entityManager.createQuery(query).getResultList();
  }

  public List<BillableUsageRemittanceEntity> findByRetryAfterLessThan(OffsetDateTime asOf) {
    return find("retryAfter < ?1", asOf).list();
  }

  public void deleteAllByOrgIdAndRemittancePendingDateBefore(
      String orgId, OffsetDateTime cutoffDate) {
    delete("orgId = ?1 AND remittancePendingDate < ?2", orgId, cutoffDate);
  }

  @Transactional
  public void deleteByOrgId(String orgId) {
    delete("orgId = ?1", orgId);
  }

  @Transactional
  public void updateStatusByIdIn(
      List<String> uuids,
      RemittanceStatus status,
      OffsetDateTime billedOn,
      RemittanceErrorCode errorCode) {
    update(
        "status = ?1, billedOn=?2, errorCode=?3 where uuid in (?4)",
        status,
        billedOn,
        errorCode,
        uuids);
  }

  public int resetBillableUsageRemittance(
      String productId, OffsetDateTime start, OffsetDateTime end, Set<String> orgIds) {
    return update(
        "update BillableUsageRemittanceEntity bu set bu.remittedPendingValue=0.0 where bu.productId = :productId and bu.orgId in :orgIds and bu.remittancePendingDate between :start and :end",
        Map.of("productId", productId, "orgIds", orgIds, "start", start, "end", end));
  }

  private Specification<BillableUsageRemittanceEntity> buildSearchSpecification(
      BillableUsageRemittanceFilter filter) {

    var searchCriteria = Specification.<BillableUsageRemittanceEntity>where();
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
    if (Objects.nonNull(filter.getUsage())) {
      searchCriteria = searchCriteria.and(matchingUsage(filter.getUsage()));
    }
    if (Objects.nonNull(filter.getSla())) {
      searchCriteria = searchCriteria.and(matchingSla(filter.getSla()));
    }
    if (Objects.nonNull(filter.getHardwareMeasurementType())) {
      searchCriteria =
          searchCriteria.and(matchingHardwareMeasurementType(filter.getHardwareMeasurementType()));
    }
    if (filter.isExcludeFailures()) {
      searchCriteria = searchCriteria.and(excludesFailures());
    }

    return searchCriteria;
  }

  private Specification<BillableUsageRemittanceEntity> excludesFailures() {
    return (root, query, builder) ->
        builder.or(
            builder.isNull(root.get(BillableUsageRemittanceEntity_.status)),
            builder.notEqual(
                root.get(BillableUsageRemittanceEntity_.status), RemittanceStatus.FAILED),
            builder.isNotNull(root.get(BillableUsageRemittanceEntity_.retryAfter)));
  }

  private Specification<BillableUsageRemittanceEntity> matchingHardwareMeasurementType(
      String hardwareMeasurementType) {
    return (root, query, builder) ->
        builder.equal(
            root.get(BillableUsageRemittanceEntity_.hardwareMeasurementType),
            hardwareMeasurementType);
  }

  private static Specification<BillableUsageRemittanceEntity> matchingProductId(String productId) {
    return (root, query, builder) ->
        builder.equal(root.get(BillableUsageRemittanceEntity_.productId), productId);
  }

  private static Specification<BillableUsageRemittanceEntity> matchingMetricId(String metricId) {
    return (root, query, builder) ->
        builder.equal(root.get(BillableUsageRemittanceEntity_.metricId), metricId);
  }

  private static Specification<BillableUsageRemittanceEntity> matchingBillingProvider(
      String billingProvider) {
    return (root, query, builder) ->
        builder.equal(root.get(BillableUsageRemittanceEntity_.billingProvider), billingProvider);
  }

  private static Specification<BillableUsageRemittanceEntity> matchingBillingAccountId(
      String billingAccountId) {
    return (root, query, builder) ->
        builder.equal(root.get(BillableUsageRemittanceEntity_.billingAccountId), billingAccountId);
  }

  private static Specification<BillableUsageRemittanceEntity> matchingOrgId(String orgId) {
    return (root, query, builder) ->
        builder.equal(root.get(BillableUsageRemittanceEntity_.orgId), orgId);
  }

  private static Specification<BillableUsageRemittanceEntity> beforeRemittanceDate(
      OffsetDateTime ending) {
    return (root, query, builder) ->
        builder.lessThanOrEqualTo(
            root.get(BillableUsageRemittanceEntity_.remittancePendingDate), ending);
  }

  private static Specification<BillableUsageRemittanceEntity> afterRemittanceDate(
      OffsetDateTime beginning) {
    return (root, query, builder) ->
        builder.greaterThanOrEqualTo(
            root.get(BillableUsageRemittanceEntity_.remittancePendingDate), beginning);
  }

  private static Specification<BillableUsageRemittanceEntity> matchingAccumulationPeriod(
      String accumulationPeriod) {
    return (root, query, builder) ->
        builder.equal(
            root.get(BillableUsageRemittanceEntity_.ACCUMULATION_PERIOD), accumulationPeriod);
  }

  private static Specification<BillableUsageRemittanceEntity> matchingUsage(String usage) {
    return (root, query, builder) ->
        builder.equal(root.get(BillableUsageRemittanceEntity_.usage), usage);
  }

  private static Specification<BillableUsageRemittanceEntity> matchingSla(String sla) {
    return (root, query, builder) ->
        builder.equal(root.get(BillableUsageRemittanceEntity_.sla), sla);
  }
}
