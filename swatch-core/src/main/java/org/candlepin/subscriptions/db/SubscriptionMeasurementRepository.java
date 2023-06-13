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
import javax.persistence.criteria.From;
import javax.persistence.criteria.Join;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.Offering_;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurement;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurement.SubscriptionMeasurementKey;
import org.candlepin.subscriptions.utilization.api.model.MetricId;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository for subscription-provided product capacities. */
public interface SubscriptionMeasurementRepository
    extends JpaRepository<SubscriptionMeasurement, SubscriptionMeasurementKey>,
        JpaSpecificationExecutor<SubscriptionMeasurement> {

  String SUBSCRIPTION_ALIAS = "subscription";

  @SuppressWarnings("java:S107")
  default List<SubscriptionMeasurement> findAllBy(
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

  default List<SubscriptionMeasurement> findAllBy(
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

  static Specification<SubscriptionMeasurement> orgAndProductEquals(
      String orgId, String productId) {
    return (root, query, builder) -> {
      var subscriptionRoot =
          fetchJoin(root, SubscriptionMeasurement_.subscription, SUBSCRIPTION_ALIAS);
      var productIdRoot =
          fetchJoin(
              subscriptionRoot, Subscription_.subscriptionProductIds, "subscriptionProductIds");
      return builder.and(
          builder.equal(subscriptionRoot.get(Subscription_.orgId), orgId),
          builder.equal(productIdRoot.get(SubscriptionProductId_.productId), productId));
    };
  }

  /**
   * This method looks for subscriptions that are active between the two dates given. The logic is
   * not intuitive: subscription_begin &lt;= report_end && subscription_end &gt;= report_begin. Here
   * is how this predicate is derived. There are four points that need to be considered: the
   * subscription begin date (Sb), the subscription end date (Se), the report begin date (Rb), and
   * the report end date (Re). Those dates can be in five different relationships.
   *
   * <ol>
   *   <li>Sb Se Rb Re (a subscription that expires before the report period even starts
   *   <li>Sb Rb Se Re (a subscription that has started before the report period and ends during it.
   *   <li>Rb Sb Se Re (a subscription that falls entirely within the report period)
   *   <li>Rb Sb Re Se (a subscription that starts inside the report period but continues past the
   *       end of the period)
   *   <li>Rb Re Sb Se (a subscription that does not start until after the period has already ended)
   * </ol>
   *
   * <p>We want this method to return subscriptions that are active within the report period. That
   * means cases 2, 3, and 4. Here are the relationships for those cases:
   *
   * <ol>
   *   <li>Sb &lt; Rb, Sb &lt; Re, Se &gt; Rb, Se &lt; Re
   *   <li>Sb &gt; Rb, Sb &lt; Re, Se &gt; Rb, Se &lt; Re
   *   <li>Sb &gt; Rb, Sb &lt; Re, Se &gt; Rb, Se &gt; Re
   * </ol>
   *
   * <p>Looking at those inequalities, we can see that the two invariant relationships are Sb &lt;
   * Re and Se &gt; Rb. Then we add the "or equal to" to the inequalities to capture edge cases.
   *
   * @param reportStart the date the reporting period begins
   * @param reportEnd the date the reporting period ends
   * @return A specification that determines if a subscription is active during the given period
   */
  static Specification<SubscriptionMeasurement> subscriptionIsActiveBetween(
      OffsetDateTime reportStart, OffsetDateTime reportEnd) {
    return (root, query, builder) -> {
      var p = builder.conjunction();
      var subscriptionRoot =
          fetchJoin(root, SubscriptionMeasurement_.subscription, SUBSCRIPTION_ALIAS);
      if (Objects.nonNull(reportEnd)) {
        p.getExpressions()
            .add(
                builder.lessThanOrEqualTo(
                    subscriptionRoot.get(Subscription_.startDate), reportEnd));
      }
      if (Objects.nonNull(reportStart)) {
        p.getExpressions()
            .add(
                builder.greaterThanOrEqualTo(
                    subscriptionRoot.get(Subscription_.endDate), reportStart));
      }
      return p;
    };
  }

  static Specification<SubscriptionMeasurement> slaEquals(ServiceLevel sla) {
    return (root, query, builder) -> {
      var offeringRoot = query.from(Offering.class);
      var subscriptionRoot =
          fetchJoin(root, SubscriptionMeasurement_.subscription, SUBSCRIPTION_ALIAS);
      return builder.and(
          builder.equal(subscriptionRoot.get(Subscription_.sku), offeringRoot.get(Offering_.sku)),
          builder.equal(offeringRoot.get(Offering_.serviceLevel), sla));
    };
  }

  static Specification<SubscriptionMeasurement> usageEquals(Usage usage) {
    return (root, query, builder) -> {
      var offeringRoot = query.from(Offering.class);
      var subscriptionRoot =
          fetchJoin(root, SubscriptionMeasurement_.subscription, SUBSCRIPTION_ALIAS);
      return builder.and(
          builder.equal(subscriptionRoot.get(Subscription_.sku), offeringRoot.get(Offering_.sku)),
          builder.equal(offeringRoot.get(Offering_.usage), usage));
    };
  }

  static Specification<SubscriptionMeasurement> metricsCriteria(
      HypervisorReportCategory hypervisorReportCategory, String attribute) {
    return (root, query, builder) -> {
      var physicalPredicate =
          builder.and(
              builder.equal(root.get(SubscriptionMeasurement_.measurementType), "PHYSICAL"),
              builder.equal(root.get(SubscriptionMeasurement_.metricId), attribute));

      var hypervisorPredicate =
          builder.and(
              builder.equal(root.get(SubscriptionMeasurement_.measurementType), "HYPERVISOR"),
              builder.equal(root.get(SubscriptionMeasurement_.metricId), attribute));

      if (Objects.nonNull(hypervisorReportCategory)) {
        return switch (hypervisorReportCategory) {
          case NON_HYPERVISOR -> physicalPredicate;
          case HYPERVISOR -> hypervisorPredicate;
        };
      } else {
        return builder.equal(root.get(SubscriptionMeasurement_.metricId), attribute);
      }
    };
  }

  private static <F, T> Join<F, T> fetchJoin(
      From<F, F> root, SingularAttribute<F, T> attribute, String alias) {
    var existing =
        root.getJoins().stream().filter(join -> Objects.equals(join.getAlias(), alias)).findFirst();
    return existing
        .map(join -> (Join<F, T>) join)
        .orElseGet(
            () -> {
              var join = (Join<F, T>) root.fetch(attribute);
              join.alias(alias);
              return join;
            });
  }

  private static <F, T> Join<F, T> fetchJoin(
      From<?, F> root, SetAttribute<F, T> attribute, String alias) {
    var existing =
        root.getJoins().stream().filter(join -> Objects.equals(join.getAlias(), alias)).findFirst();
    return existing
        .map(join -> (Join<F, T>) join)
        .orElseGet(
            () -> {
              var join = (Join<F, T>) root.fetch(attribute);
              join.alias(alias);
              return join;
            });
  }

  @SuppressWarnings("java:S107")
  default Specification<SubscriptionMeasurement> buildSearchSpecification(
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
      searchCriteria =
          searchCriteria.and(
              metricsCriteria(hypervisorReportCategory, metricId.toString().toUpperCase()));
    }
    return searchCriteria;
  }
}
