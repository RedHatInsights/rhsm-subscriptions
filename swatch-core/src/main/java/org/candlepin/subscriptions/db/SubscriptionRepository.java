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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.persistence.criteria.Root;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.Offering_;
import org.candlepin.subscriptions.db.model.ReportCriteria;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Subscription_;
import org.candlepin.subscriptions.db.model.Usage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
      "SELECT s FROM Subscription s where s.endDate > CURRENT_TIMESTAMP "
          + "AND s.subscriptionId = :subscriptionId")
  Optional<Subscription> findActiveSubscription(@Param("subscriptionId") String subscriptionId);

  Optional<Subscription> findBySubscriptionNumber(String subscriptionNumber);

  Page<Subscription> findBySku(String sku, Pageable pageable);

  Stream<Subscription> findByOrgId(String orgId);

  List<Subscription> findByOrgIdAndEndDateAfter(String orgId, OffsetDateTime date);

  void deleteBySubscriptionId(String subscriptionId);

  void deleteByOrgId(String orgId);

  private Specification<Subscription> buildSearchSpecification(ReportCriteria reportCriteria) {
    /* The where call allows us to build a Specification object to operate on even if the first
     * specification method we call returns null (which it won't in this case, but it's good
     * practice to handle it. */
    Specification<Subscription> searchCriteria =
        Specification.where(
            subscriptionIsActiveBetween(reportCriteria.getBeginning(), reportCriteria.getEnding()));
    if (Objects.nonNull(reportCriteria.getOrgId())) {
      searchCriteria = searchCriteria.and(orgIdEquals(reportCriteria.getOrgId()));
    } else {
      searchCriteria = searchCriteria.and(accountNumberEquals(reportCriteria.getAccountNumber()));
    }
    if (reportCriteria.isPayg()) {
      // NOTE: we expect payg subscription records to always populate billingProviderId
      searchCriteria = searchCriteria.and(hasBillingProviderId());
    }
    // TODO: ENT-5042 should move away from using product name values here //NOSONAR
    if (!ObjectUtils.isEmpty(reportCriteria.getProductNames())) {
      searchCriteria = searchCriteria.and(productNameIn(reportCriteria.getProductNames()));
    }
    if (Objects.nonNull(reportCriteria.getServiceLevel())
        && !reportCriteria.getServiceLevel().equals(ServiceLevel._ANY)) {
      searchCriteria = searchCriteria.and(slaEquals(reportCriteria.getServiceLevel()));
    }
    if (Objects.nonNull(reportCriteria.getUsage())
        && !reportCriteria.getUsage().equals(Usage._ANY)) {
      searchCriteria = searchCriteria.and(usageEquals(reportCriteria.getUsage()));
    }
    if (Objects.nonNull(reportCriteria.getBillingProvider())
        && !reportCriteria.getBillingProvider().equals(BillingProvider._ANY)) {
      searchCriteria =
          searchCriteria.and(billingProviderEquals(reportCriteria.getBillingProvider()));
    }
    if (Objects.nonNull(reportCriteria.getBillingAccountId())
        && !reportCriteria.getBillingAccountId().equals("_ANY")) {
      searchCriteria =
          searchCriteria.and(billingAccountIdEquals(reportCriteria.getBillingAccountId()));
    }
    return searchCriteria;
  }

  default List<Subscription> findByCriteria(ReportCriteria reportCriteria, Sort sort) {
    return findAll(buildSearchSpecification(reportCriteria), sort);
  }

  private static Specification<Subscription> hasBillingProviderId() {
    return (root, query, builder) ->
        builder.and(
            builder.isNotNull(root.get(Subscription_.billingProviderId)),
            builder.notEqual(root.get(Subscription_.billingProviderId), ""));
  }

  private static Specification<Subscription> productNameIn(Set<String> productNames) {
    return (root, query, builder) -> {
      Root<Offering> offeringRoot = query.from(Offering.class);
      return builder.and(
          builder.equal(root.get(Subscription_.sku), offeringRoot.get(Offering_.sku)),
          offeringRoot.get(Offering_.productName).in(productNames));
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
   * not intuitive: subscription_begin &lt;= report_end && subscription_end &gt;= report_begin. See
   * {@link SubscriptionCapacityViewRepository#subscriptionIsActiveBetween(OffsetDateTime,
   * OffsetDateTime)} for a detailed explanation of how this predicate is derived.
   *
   * @param reportStart the date the reporting period starts
   * @param reportEnd the date the reporting period ends
   * @return A Specification that determines if a subscription is active during the given period.
   */
  private Specification<Subscription> subscriptionIsActiveBetween(
      OffsetDateTime reportStart, OffsetDateTime reportEnd) {
    return (root, query, builder) -> {
      var p = builder.conjunction();
      if (Objects.nonNull(reportEnd)) {
        p.getExpressions()
            .add(builder.lessThanOrEqualTo(root.get(Subscription_.startDate), reportEnd));
      }
      if (Objects.nonNull(reportStart)) {
        p.getExpressions()
            .add(builder.greaterThanOrEqualTo(root.get(Subscription_.endDate), reportStart));
      }
      return p;
    };
  }

  private static Specification<Subscription> slaEquals(ServiceLevel sla) {
    return (root, query, builder) -> {
      Root<Offering> offeringRoot = query.from(Offering.class);
      return builder.and(
          builder.equal(root.get(Subscription_.sku), offeringRoot.get(Offering_.sku)),
          builder.equal(offeringRoot.get(Offering_.serviceLevel), sla));
    };
  }

  private static Specification<Subscription> usageEquals(Usage usage) {
    return (root, query, builder) -> {
      Root<Offering> offeringRoot = query.from(Offering.class);
      return builder.and(
          builder.equal(root.get(Subscription_.sku), offeringRoot.get(Offering_.sku)),
          builder.equal(offeringRoot.get(Offering_.usage), usage));
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
