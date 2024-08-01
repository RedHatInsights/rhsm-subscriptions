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

import jakarta.persistence.QueryHint;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.Offering_;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurementKey_;
import org.candlepin.subscriptions.db.model.Subscription_;
import org.candlepin.subscriptions.db.model.Usage;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.util.ObjectUtils;

/** Repository for Subscription Entities */
public interface SubscriptionRepository
    extends JpaRepository<Subscription, Subscription.SubscriptionCompoundId>,
        JpaSpecificationExecutor<Subscription> {

  // Added an order by clause to avoid Hibernate issue HHH-17040
  @Query(
      """
        SELECT s FROM Subscription s
        WHERE (s.endDate IS NULL OR s.endDate > CURRENT_TIMESTAMP)
          AND s.subscriptionId = :subscriptionId
        ORDER BY s.subscriptionId, s.startDate DESC
      """)
  @EntityGraph(value = "graph.SubscriptionSync")
  List<Subscription> findActiveSubscription(@Param("subscriptionId") String subscriptionId);

  @EntityGraph(value = "graph.SubscriptionSync")
  // Added an order by clause to avoid Hibernate issue HHH-17040
  @Query(
      "SELECT s FROM Subscription s WHERE s.subscriptionNumber = :subscriptionNumber"
          + " ORDER BY s.subscriptionId, s.startDate DESC")
  List<Subscription> findBySubscriptionNumber(String subscriptionNumber);

  @QueryHints(value = {@QueryHint(name = HINT_FETCH_SIZE, value = "1024")})
  @EntityGraph(value = "graph.SubscriptionSync")
  // Added an order by clause to avoid Hibernate issue HHH-17040
  @Query(
      "SELECT s FROM Subscription s WHERE s.orgId = :orgId ORDER BY s.subscriptionId, s.startDate DESC")
  Stream<Subscription> findByOrgId(String orgId);

  default Stream<Subscription> streamBy(DbReportCriteria dbReportCriteria) {
    return findBy(
        buildSearchSpecification(dbReportCriteria), FluentQuery.FetchableFluentQuery::stream);
  }

  void deleteBySubscriptionId(String subscriptionId);

  void deleteByOrgId(String orgId);

  long countByOfferingSku(String sku);

  static Specification<Subscription> buildSearchSpecification(DbReportCriteria dbReportCriteria) {
    /* The where call allows us to build a Specification object to operate on even if the first
     * specification method we call returns null (which it won't in this case, but it's good
     * practice to handle it). */
    Specification<Subscription> searchCriteria =
        (root, query, builder) -> {
          // fetch offering always, to eliminate n+1 on offering
          root.fetch(Subscription_.offering);
          // Added an order by clause to avoid Hibernate issue HHH-17040
          query.orderBy(
              builder.asc(root.get(Subscription_.subscriptionId)),
              builder.asc(root.get(Subscription_.startDate)));
          return null;
        };
    searchCriteria =
        searchCriteria.and(
            Specification.where(
                subscriptionIsActiveBetween(
                    dbReportCriteria.getBeginning(), dbReportCriteria.getEnding())));
    if (Objects.nonNull(dbReportCriteria.getOrgId())) {
      searchCriteria = searchCriteria.and(orgIdEquals(dbReportCriteria.getOrgId()));
    }
    if (dbReportCriteria.isPayg()) {
      // NOTE: we expect payg subscription records to always populate billingProviderId
      searchCriteria = searchCriteria.and(hasBillingProviderId());
    }
    if (!ObjectUtils.isEmpty(dbReportCriteria.getProductTag())) {
      searchCriteria = searchCriteria.and(productTagEquals(dbReportCriteria.getProductTag()));
    }
    if (Objects.nonNull(dbReportCriteria.getServiceLevel())
        && !dbReportCriteria.getServiceLevel().equals(ServiceLevel._ANY)) {
      searchCriteria = searchCriteria.and(slaEquals(dbReportCriteria.getServiceLevel()));
    }
    if (Objects.nonNull(dbReportCriteria.getUsage())
        && !dbReportCriteria.getUsage().equals(Usage._ANY)) {
      searchCriteria = searchCriteria.and(usageEquals(dbReportCriteria.getUsage()));
    }
    if (Objects.nonNull(dbReportCriteria.getBillingProvider())
        && !dbReportCriteria.getBillingProvider().equals(BillingProvider._ANY)) {
      searchCriteria =
          searchCriteria.and(billingProviderEquals(dbReportCriteria.getBillingProvider()));
    }
    if (Objects.nonNull(dbReportCriteria.getBillingAccountId())
        && !dbReportCriteria.getBillingAccountId().equals("_ANY")) {
      searchCriteria =
          searchCriteria.and(billingAccountIdLike(dbReportCriteria.getBillingAccountId()));
    }
    if (Objects.nonNull(dbReportCriteria.getMetricId())
        || Objects.nonNull(dbReportCriteria.getHypervisorReportCategory())) {
      searchCriteria =
          searchCriteria.and(
              metricsCriteria(
                  dbReportCriteria.getHypervisorReportCategory(), dbReportCriteria.getMetricId()));
    }

    return searchCriteria;
  }

  default List<Subscription> findUnlimited(DbReportCriteria dbReportCriteria) {
    Specification<Subscription> searchCriteria = buildSearchSpecification(dbReportCriteria);
    searchCriteria = searchCriteria.and(hasUnlimitedUsage());
    return findAll(searchCriteria);
  }

  default List<Subscription> findByCriteria(DbReportCriteria dbReportCriteria, Sort sort) {
    return findAll(buildSearchSpecification(dbReportCriteria), sort);
  }

  @Override
  @EntityGraph(attributePaths = {"subscriptionMeasurements"})
  List<Subscription> findAll(Specification<Subscription> spec, Sort sort);

  private static Specification<Subscription> hasUnlimitedUsage() {
    return (root, query, builder) -> {
      var offeringRoot = root.get(Subscription_.offering);
      return builder.equal(offeringRoot.get(Offering_.hasUnlimitedUsage), true);
    };
  }

  private static Specification<Subscription> hasBillingProviderId() {
    return (root, query, builder) ->
        builder.and(
            builder.isNotNull(root.get(Subscription_.billingProviderId)),
            builder.notEqual(root.get(Subscription_.billingProviderId), ""));
  }

  private static Specification<Subscription> productTagEquals(String productTag) {
    return (root, query, builder) -> {
      var offeringRoot = root.get(Subscription_.offering);
      return builder.isMember(productTag, offeringRoot.get(Offering_.productTags));
    };
  }

  private static Specification<Subscription> orgIdEquals(String orgId) {
    return (root, query, builder) -> builder.equal(root.get(Subscription_.orgId), orgId);
  }

  /**
   * This method looks for subscriptions that are active between the two dates given. The logic is
   * not intuitive: subscription_begin &lt;= report_end && (subscription_end &gt;= report_begin OR
   * subscription_end IS NULL).
   *
   * <p>There are four points that need to be considered: the subscription begin date (Sb), the
   * subscription end date (Se), the report begin date (Rb), and the report end date (Re). Those
   * dates can be in five different relationships.
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
   * @param reportStart the date the reporting period starts
   * @param reportEnd the date the reporting period ends
   * @return A Specification that determines if a subscription is active during the given period.
   */
  private static Specification<Subscription> subscriptionIsActiveBetween(
      OffsetDateTime reportStart, OffsetDateTime reportEnd) {
    return (root, query, builder) ->
        predicateForSubscriptionIsActiveBetween(root, builder, reportStart, reportEnd);
  }

  private static Predicate predicateForSubscriptionIsActiveBetween(
      Path<Subscription> path,
      CriteriaBuilder builder,
      OffsetDateTime reportStart,
      OffsetDateTime reportEnd) {
    var predicates = new ArrayList<Predicate>();
    if (Objects.nonNull(reportEnd)) {
      predicates.add(builder.lessThanOrEqualTo(path.get(Subscription_.startDate), reportEnd));
    }
    if (Objects.nonNull(reportStart)) {
      predicates.add(
          builder.or(
              builder.isNull(path.get(Subscription_.endDate)),
              builder.greaterThanOrEqualTo(path.get(Subscription_.endDate), reportStart)));
    }
    return builder.and(predicates.toArray(Predicate[]::new));
  }

  private static Specification<Subscription> slaEquals(ServiceLevel sla) {
    return (root, query, builder) -> {
      var offeringRoot = root.get(Subscription_.offering);
      return builder.equal(offeringRoot.get(Offering_.serviceLevel), sla);
    };
  }

  private static Specification<Subscription> usageEquals(Usage usage) {
    return (root, query, builder) -> {
      var offeringRoot = root.get(Subscription_.offering);
      return builder.equal(offeringRoot.get(Offering_.usage), usage);
    };
  }

  private static Specification<Subscription> billingProviderEquals(
      BillingProvider billingProvider) {
    return (root, query, builder) ->
        builder.equal(root.get(Subscription_.billingProvider), billingProvider);
  }

  private static Specification<Subscription> billingAccountIdLike(String billingAccountId) {
    return (root, query, builder) ->
        // If multiple ID's exist, match on firstID or firstID;secondID (azureTenantId or
        // azureTenantId;azureSubscriptionId)
        builder.like(root.get(Subscription_.billingAccountId), billingAccountId + "%");
  }

  private static Specification<Subscription> metricsCriteria(
      HypervisorReportCategory hypervisorReportCategory, String metricId) {
    return (root, query, builder) -> {
      var predicates = new ArrayList<Predicate>();
      var measurementKeyPath = root.join(Subscription_.subscriptionMeasurements).key();
      if (hypervisorReportCategory != null) {
        var measurementType =
            switch (hypervisorReportCategory) {
              case NON_HYPERVISOR -> "PHYSICAL";
              case HYPERVISOR -> "HYPERVISOR";
            };
        predicates.add(
            builder.equal(
                measurementKeyPath.get(SubscriptionMeasurementKey_.measurementType),
                measurementType));
      }
      if (metricId != null) {
        predicates.add(
            builder.equal(measurementKeyPath.get(SubscriptionMeasurementKey_.metricId), metricId));
      }
      return builder.and(predicates.toArray(Predicate[]::new));
    };
  }
}
