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
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityKey;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityKey_;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView_;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.utilization.api.model.Uom;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SubscriptionCapacityViewRepository
    extends JpaRepository<SubscriptionCapacityView, SubscriptionCapacityKey>,
        JpaSpecificationExecutor<SubscriptionCapacityView> {

  @SuppressWarnings("java:S107")
  default List<SubscriptionCapacityView> findAllBy(
      String orgId,
      HypervisorReportCategory hypervisorReportCategory,
      String productId,
      ServiceLevel serviceLevel,
      Usage usage,
      OffsetDateTime reportStart,
      OffsetDateTime reportEnd,
      Uom uom) {

    return findAll(
        buildSearchSpecification(
            orgId,
            hypervisorReportCategory,
            productId,
            serviceLevel,
            usage,
            reportStart,
            reportEnd,
            uom));
  }

  static Specification<SubscriptionCapacityView> orgAndProductEquals(
      String orgId, String productId) {
    return (root, query, builder) -> {
      var key = root.get(SubscriptionCapacityView_.key);
      return builder.and(
          builder.equal(key.get(SubscriptionCapacityKey_.orgId), orgId),
          builder.equal(key.get(SubscriptionCapacityKey_.productId), productId));
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
   * @param reportStart the date the reporting period starts
   * @param reportEnd the date the reporting period ends
   * @return A specification that determines if a subscription is active during the given period
   */
  static Specification<SubscriptionCapacityView> subscriptionIsActiveBetween(
      OffsetDateTime reportStart, OffsetDateTime reportEnd) {
    return (root, query, builder) -> {
      var p = builder.conjunction();
      if (Objects.nonNull(reportEnd)) {
        p.getExpressions()
            .add(
                builder.lessThanOrEqualTo(
                    root.get(SubscriptionCapacityView_.beginDate), reportEnd));
      }
      if (Objects.nonNull(reportStart)) {
        p.getExpressions()
            .add(
                builder.greaterThanOrEqualTo(
                    root.get(SubscriptionCapacityView_.endDate), reportStart));
      }
      return p;
    };
  }

  static Specification<SubscriptionCapacityView> slaEquals(ServiceLevel sla) {
    return (root, query, builder) ->
        builder.equal(root.get(SubscriptionCapacityView_.serviceLevel), sla);
  }

  static Specification<SubscriptionCapacityView> usageEquals(Usage usage) {
    return (root, query, builder) ->
        builder.equal(root.get(SubscriptionCapacityView_.usage), usage);
  }

  static Specification<SubscriptionCapacityView> matchingUomForCores() {
    return (root, query, builder) ->
        builder.or(
            builder.isNotNull(root.get(SubscriptionCapacityView_.cores)),
            builder.isNotNull(root.get(SubscriptionCapacityView_.hypervisorCores)),
            builder.isTrue(root.get(SubscriptionCapacityView_.hasUnlimitedUsage)));
  }

  static Specification<SubscriptionCapacityView> matchingUomForSockets() {
    return (root, query, builder) ->
        builder.or(
            builder.isNotNull(root.get(SubscriptionCapacityView_.sockets)),
            builder.isNotNull(root.get(SubscriptionCapacityView_.hypervisorSockets)),
            builder.isTrue(root.get(SubscriptionCapacityView_.hasUnlimitedUsage)));
  }

  static Specification<SubscriptionCapacityView> matchesCategories(
      HypervisorReportCategory hypervisorReportCategory) {
    return (root, query, builder) -> {
      switch (hypervisorReportCategory) {
        case NON_HYPERVISOR:
          // Has no virt capacity
          var zeroCoresAndSockets =
              builder.and(
                  builder.equal(root.get(SubscriptionCapacityView_.hypervisorSockets), 0),
                  builder.equal(root.get(SubscriptionCapacityView_.hypervisorCores), 0));
          var nullCoresAndSockets =
              builder.and(
                  builder.isNull(root.get(SubscriptionCapacityView_.hypervisorSockets)),
                  builder.isNull(root.get(SubscriptionCapacityView_.hypervisorCores)));
          // In practices, subscriptions with non-hypervisor SKUs have null for hypervisor_cores
          // and hypervisor_sockets, but I am checking for zero here also just to cover the bases.
          return builder.or(zeroCoresAndSockets, nullCoresAndSockets);
        case HYPERVISOR:
          // Has some virt capacity
          return builder.or(
              builder.greaterThan(root.get(SubscriptionCapacityView_.hypervisorSockets), 0),
              builder.greaterThan(root.get(SubscriptionCapacityView_.hypervisorCores), 0));
        default:
          throw new IllegalStateException("Unhandled HypervisorReportCategory value");
      }
    };
  }

  @SuppressWarnings("java:S107")
  default Specification<SubscriptionCapacityView> buildSearchSpecification(
      String orgId,
      HypervisorReportCategory hypervisorReportCategory,
      String productId,
      ServiceLevel serviceLevel,
      Usage usage,
      OffsetDateTime reportStart,
      OffsetDateTime reportEnd,
      Uom uom) {

    /* The where call allows us to build a Specification object to operate on even if the
     * first specification method we call returns null (it won't be null in this case, but it's
     * good practice to handle it) */
    var searchCriteria = Specification.where(subscriptionIsActiveBetween(reportStart, reportEnd));

    if ((orgId == null && productId != null) || (orgId != null && productId == null)) {
      throw new IllegalStateException(
          "Either both orgId and productId must be supplied or neither value at all.");
    }
    // If orgId is nonNull, then productId must be nonNull too based on the previous if statement
    // but for clarity's sake, I'm leaving both tests instead of leaving a logic puzzle for a
    // developer to work through
    if (Objects.nonNull(orgId) && Objects.nonNull(productId)) { // NOSONAR
      searchCriteria = searchCriteria.and(orgAndProductEquals(orgId, productId));
    }
    if (Uom.CORES.equals(uom)) {
      searchCriteria = searchCriteria.and(matchingUomForCores());
    }
    if (Uom.SOCKETS.equals(uom)) {
      searchCriteria = searchCriteria.and(matchingUomForSockets());
    }
    if (Objects.nonNull(serviceLevel) && !serviceLevel.equals(ServiceLevel._ANY)) {
      searchCriteria = searchCriteria.and(slaEquals(serviceLevel));
    }
    if (Objects.nonNull(usage) && !usage.equals(Usage._ANY)) {
      searchCriteria = searchCriteria.and(usageEquals(usage));
    }
    if (Objects.nonNull(hypervisorReportCategory)) {
      searchCriteria = searchCriteria.and(matchesCategories(hypervisorReportCategory));
    }
    return searchCriteria;
  }
}
