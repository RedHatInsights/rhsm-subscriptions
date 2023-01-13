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
import java.util.Set;
import java.util.stream.Stream;
import javax.persistence.metamodel.SingularAttribute;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityKey;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityKey_;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity_;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.utilization.api.model.MetricId;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository for subscription-provided product capacities. */
public interface SubscriptionCapacityRepository
    extends JpaRepository<SubscriptionCapacity, SubscriptionCapacityKey>,
        JpaSpecificationExecutor<SubscriptionCapacity> {

  List<SubscriptionCapacity> findByKeyOrgIdAndKeySubscriptionIdIn(
      String orgId, List<String> subscriptionIds);

  Stream<SubscriptionCapacity> findByKeyOrgId(String orgId);

  void deleteByKeyOrgId(String orgId);

  @SuppressWarnings("java:S107")
  default List<SubscriptionCapacity> findAllBy(
      String orgId,
      String productId,
      MetricId metricId,
      HypervisorReportCategory hypervisorReportCategory,
      ServiceLevel serviceLevel,
      Usage usage,
      OffsetDateTime reportBegin,
      OffsetDateTime reportEnd) {
    return findAll(
        buildSearchSpecification(
            orgId,
            productId,
            metricId,
            hypervisorReportCategory,
            serviceLevel,
            usage,
            reportBegin,
            reportEnd));
  }

  default List<SubscriptionCapacity> findAllBy(
      String orgId,
      String productId,
      ServiceLevel serviceLevel,
      Usage usage,
      OffsetDateTime reportBegin,
      OffsetDateTime reportEnd) {
    return findAll(
        buildSearchSpecification(
            orgId, productId, null, null, serviceLevel, usage, reportBegin, reportEnd));
  }

  static Specification<SubscriptionCapacity> orgAndProductEquals(String orgId, String productId) {
    return (root, query, builder) -> {
      var key = root.get(SubscriptionCapacity_.key);
      return builder.and(
          builder.equal(key.get(SubscriptionCapacityKey_.orgId), orgId),
          builder.equal(key.get(SubscriptionCapacityKey_.productId), productId));
    };
  }

  /**
   * This method looks for subscriptions that are active between the two dates given. See {@link
   * SubscriptionCapacityViewRepository#subscriptionIsActiveBetween(OffsetDateTime, OffsetDateTime)}
   * for a detailed discussion of how the logic for this method is derived.
   *
   * @param reportStart the date the reporting period begins
   * @param reportEnd the date the reporting period ends
   * @return A specification that determines if a subscription is active during the given period
   */
  static Specification<SubscriptionCapacity> subscriptionIsActiveBetween(
      OffsetDateTime reportStart, OffsetDateTime reportEnd) {
    return (root, query, builder) -> {
      var p = builder.conjunction();
      if (Objects.nonNull(reportEnd)) {
        p.getExpressions()
            .add(builder.lessThanOrEqualTo(root.get(SubscriptionCapacity_.beginDate), reportEnd));
      }
      if (Objects.nonNull(reportStart)) {
        p.getExpressions()
            .add(
                builder.greaterThanOrEqualTo(root.get(SubscriptionCapacity_.endDate), reportStart));
      }
      return p;
    };
  }

  static Specification<SubscriptionCapacity> slaEquals(ServiceLevel sla) {
    return (root, query, builder) ->
        builder.equal(root.get(SubscriptionCapacity_.serviceLevel), sla);
  }

  static Specification<SubscriptionCapacity> usageEquals(Usage usage) {
    return (root, query, builder) -> builder.equal(root.get(SubscriptionCapacity_.usage), usage);
  }

  static Specification<SubscriptionCapacity> coresCriteria(
      HypervisorReportCategory hypervisorReportCategory) {
    return metricsCriteria(
        hypervisorReportCategory,
        SubscriptionCapacity_.cores,
        SubscriptionCapacity_.hypervisorCores);
  }

  static Specification<SubscriptionCapacity> socketsCriteria(
      HypervisorReportCategory hypervisorReportCategory) {
    return metricsCriteria(
        hypervisorReportCategory,
        SubscriptionCapacity_.sockets,
        SubscriptionCapacity_.hypervisorSockets);
  }

  static Specification<SubscriptionCapacity> metricsCriteria(
      HypervisorReportCategory hypervisorReportCategory,
      SingularAttribute<SubscriptionCapacity, Integer> standardAttribute,
      SingularAttribute<SubscriptionCapacity, Integer> hypervisorAttribute) {
    return (root, query, builder) -> {
      // Note this is a *disjunction* (i.e. an "or" operation)
      var metricPredicate = builder.disjunction();
      metricPredicate
          .getExpressions()
          .add(builder.isTrue(root.get(SubscriptionCapacity_.hasUnlimitedUsage)));

      var standardPredicate = builder.greaterThan(root.get(standardAttribute), 0);
      var hypervisorPredicate = builder.greaterThan(root.get(hypervisorAttribute), 0);
      // Has no hypervisor capacity at all.  In practices, subscriptions with non-hypervisor SKUs
      // have null for hypervisor_cores and hypervisor_sockets, but I am checking for zero here also
      // just to cover the bases.
      var nonHypervisorPredicate =
          builder.or(
              builder.isNull(root.get(hypervisorAttribute)),
              builder.equal(root.get(hypervisorAttribute), 0));

      if (Objects.nonNull(hypervisorReportCategory)) {
        switch (hypervisorReportCategory) {
          case NON_HYPERVISOR:
            metricPredicate.getExpressions().add(nonHypervisorPredicate);
            break;
          case HYPERVISOR:
            metricPredicate.getExpressions().add(hypervisorPredicate);
            break;
          default:
            throw new IllegalStateException("Unhandled HypervisorReportCategory value");
        }
      } else {
        metricPredicate.getExpressions().addAll(Set.of(standardPredicate, hypervisorPredicate));
      }

      return metricPredicate;
    };
  }

  @SuppressWarnings("java:S107")
  default Specification<SubscriptionCapacity> buildSearchSpecification(
      String orgId,
      String productId,
      MetricId metricId,
      HypervisorReportCategory hypervisorReportCategory,
      ServiceLevel serviceLevel,
      Usage usage,
      OffsetDateTime reportBegin,
      OffsetDateTime reportEnd) {
    if ((orgId == null && productId != null) || (orgId != null && productId == null)) {
      throw new IllegalStateException(
          "Either both orgId and productId must be supplied or neither value at all.");
    }

    if (metricId == null && hypervisorReportCategory != null) {
      throw new IllegalStateException(
          "Hypervisor report category requires the presence of a metricId");
    }

    /* The where call allows us to build a Specification object to operate on even if the
     * first specification method we call returns null (it won't be null in this case, but it's
     * good practice to handle it) */
    var searchCriteria = Specification.where(subscriptionIsActiveBetween(reportBegin, reportEnd));

    // If orgId is nonNull, then productId must be nonNull too based on the previous non-null
    // checks but for clarity's sake, I'm leaving both tests instead of leaving a logic puzzle for a
    // developer to work through
    if (Objects.nonNull(orgId) && Objects.nonNull(productId)) { // NOSONAR
      searchCriteria = searchCriteria.and(orgAndProductEquals(orgId, productId));
    }
    if (Objects.nonNull(serviceLevel) && !serviceLevel.equals(ServiceLevel._ANY)) {
      searchCriteria = searchCriteria.and(slaEquals(serviceLevel));
    }
    if (Objects.nonNull(usage) && !usage.equals(Usage._ANY)) {
      searchCriteria = searchCriteria.and(usageEquals(usage));
    }

    if (Objects.nonNull(metricId)) {
      switch (metricId) {
        case CORES:
          searchCriteria = searchCriteria.and(coresCriteria(hypervisorReportCategory));
          break;
        case SOCKETS:
          searchCriteria = searchCriteria.and(socketsCriteria(hypervisorReportCategory));
          break;
        default:
          throw new IllegalStateException(metricId + " is not a support metricId for this query");
      }
    }
    return searchCriteria;
  }
}
