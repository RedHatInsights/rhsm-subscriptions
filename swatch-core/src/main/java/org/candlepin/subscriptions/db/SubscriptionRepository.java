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

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.Offering_;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionProductId_;
import org.candlepin.subscriptions.db.model.Subscription_;
import org.candlepin.subscriptions.db.model.Usage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.util.ObjectUtils;

/** Repository for Subscription Entities */
public interface SubscriptionRepository
    extends JpaRepository<Subscription, Subscription.SubscriptionCompoundId>,
        JpaSpecificationExecutor<Subscription> {

  @Query(
      """
        SELECT s FROM Subscription s
        WHERE (s.endDate IS NULL OR s.endDate > CURRENT_TIMESTAMP)
          AND s.subscriptionId = :subscriptionId
      """)
  @EntityGraph(value = "graph.SubscriptionSync")
  Optional<Subscription> findActiveSubscription(@Param("subscriptionId") String subscriptionId);

  @EntityGraph(value = "graph.SubscriptionSync")
  Optional<Subscription> findBySubscriptionNumber(String subscriptionNumber);

  @EntityGraph(value = "graph.SubscriptionSync")
  Page<Subscription> findByOfferingSku(String sku, Pageable pageable);

  @EntityGraph(value = "graph.SubscriptionSync")
  @Query("SELECT DISTINCT s FROM Subscription s WHERE s.orgId = :orgId")
  Stream<Subscription> findByOrgId(String orgId);

  void deleteBySubscriptionId(String subscriptionId);

  void deleteByOrgId(String orgId);

  private Specification<Subscription> buildSearchSpecification(DbReportCriteria dbReportCriteria) {
    /* The where call allows us to build a Specification object to operate on even if the first
     * specification method we call returns null (which it won't in this case, but it's good
     * practice to handle it. */
    Specification<Subscription> searchCriteria =
        (root, query, builder) -> {
          // fetch offering always, to eliminate n+1 on offering
          root.fetch(Subscription_.offering);
          return null;
        };
    searchCriteria =
        searchCriteria.and(
            Specification.where(
                subscriptionIsActiveBetween(
                    dbReportCriteria.getBeginning(), dbReportCriteria.getEnding())));
    if (Objects.nonNull(dbReportCriteria.getOrgId())) {
      searchCriteria = searchCriteria.and(orgIdEquals(dbReportCriteria.getOrgId()));
    } else {
      searchCriteria = searchCriteria.and(accountNumberEquals(dbReportCriteria.getAccountNumber()));
    }
    if (dbReportCriteria.isPayg()) {
      // NOTE: we expect payg subscription records to always populate billingProviderId
      searchCriteria = searchCriteria.and(hasBillingProviderId());
    }
    // TODO: ENT-5042 should move away from using product name values here //NOSONAR
    if (!ObjectUtils.isEmpty(dbReportCriteria.getProductNames())) {
      searchCriteria = searchCriteria.and(productNameIn(dbReportCriteria.getProductNames()));
    }
    if (Objects.nonNull(dbReportCriteria.getProductId())) {
      searchCriteria = searchCriteria.and(productIdEquals(dbReportCriteria.getProductId()));
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
          searchCriteria.and(billingAccountIdEquals(dbReportCriteria.getBillingAccountId()));
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

  private static Specification<Subscription> productNameIn(Set<String> productNames) {
    return (root, query, builder) -> {
      var offeringRoot = root.get(Subscription_.offering);
      return offeringRoot.get(Offering_.productName).in(productNames);
    };
  }

  private static Specification<Subscription> productIdEquals(String productId) {
    return (root, query, builder) -> {
      var subscriptionProductIdRoot = root.join(Subscription_.subscriptionProductIds);
      return builder.equal(
          subscriptionProductIdRoot.get(SubscriptionProductId_.productId), productId);
    };
  }

  private static Specification<Subscription> accountNumberEquals(String accountNumber) {
    return (root, query, builder) ->
        builder.equal(root.get(Subscription_.accountNumber), accountNumber);
  }

  private static Specification<Subscription> orgIdEquals(String orgId) {
    return (root, query, builder) -> builder.equal(root.get(Subscription_.orgId), orgId);
  }

  /**
   * This method looks for subscriptions that are active between the two dates given. The logic is
   * not intuitive: subscription_begin &lt;= report_end && (subscription_end &gt;= report_begin OR
   * subscription_end IS NULL). See {@link
   * SubscriptionMeasurementRepository#subscriptionIsActiveBetween(OffsetDateTime, OffsetDateTime)}
   * for a detailed explanation of how this predicate is derived.
   *
   * @param reportStart the date the reporting period starts
   * @param reportEnd the date the reporting period ends
   * @return A Specification that determines if a subscription is active during the given period.
   */
  static Specification<Subscription> subscriptionIsActiveBetween(
      OffsetDateTime reportStart, OffsetDateTime reportEnd) {
    return (root, query, builder) ->
        predicateForSubscriptionIsActiveBetween(root, builder, reportStart, reportEnd);
  }

  static Predicate predicateForSubscriptionIsActiveBetween(
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

  private static Specification<Subscription> billingAccountIdEquals(String billingAccountId) {
    return (root, query, builder) ->
        builder.equal(root.get(Subscription_.billingAccountId), billingAccountId);
  }
}
