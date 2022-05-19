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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Offering_;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Subscription_;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for Subscription Entities */
public interface SubscriptionRepository
    extends JpaRepository<Subscription, Subscription.SubscriptionCompoundId>,
        JpaSpecificationExecutor<Subscription> {

  @Query(
      "SELECT s FROM Subscription s where s.endDate > CURRENT_TIMESTAMP "
          + "AND s.subscriptionId = :subscriptionId")
  Optional<Subscription> findActiveSubscription(@Param("subscriptionId") String subscriptionId);

  Page<Subscription> findBySku(String sku, Pageable pageable);

  @Query(
      "SELECT s FROM Subscription s WHERE s.accountNumber = :accountNumber AND "
          + "s.sku in (SELECT DISTINCT o.sku FROM Offering o WHERE "
          + ":#{#key.sla} = o.serviceLevel AND "
          + "o.productName IN :#{#productNames}) AND s.startDate <= :rangeStart AND s.endDate >= :rangeEnd AND "
          + "s.billingProvider = :billingProvider AND "
          + "s.billingProviderId IS NOT NULL AND s.billingProviderId <> '' "
          + "ORDER BY s.startDate DESC")
  List<Subscription> findByAccountAndProductNameAndServiceLevel(
      @Param("accountNumber") String accountNumber,
      @Param("key") UsageCalculation.Key usageKey,
      @Param("productNames") Set<String> productNames,
      @Param("rangeStart") OffsetDateTime rangeStart,
      @Param("rangeEnd") OffsetDateTime rangeEnd,
      @Param("billingProvider") BillingProvider billingProvider);

  Stream<Subscription> findByOwnerId(String ownerId);

  List<Subscription> findByOwnerIdAndEndDateAfter(String ownerId, OffsetDateTime date);

  void deleteBySubscriptionId(String subscriptionId);

  void deleteByAccountNumber(String accountNumber);

  default List<Subscription> findOnDemandBy(
      String ownerId,
      Set<String> skus,
      ServiceLevel serviceLevel,
      Usage usage,
      OffsetDateTime reportStart,
      OffsetDateTime reportEnd) {

    return findAll(
        OnDemandSubscriptionSpecification.builder()
            .criteria(
                buildOnDemandSearchCriteria(
                    ownerId, skus, serviceLevel, usage, reportStart, reportEnd))
            .build());
  }

  private List<SearchCriteria> defaultOnDemandSearchCriteria(String ownerId, Set<String> skus) {
    return new ArrayList<>(
        List.of(
            SearchCriteria.builder()
                .key(Subscription_.ownerId.getName())
                .operation(SearchOperation.EQUAL)
                .value(ownerId)
                .build(),
            SearchCriteria.builder()
                .key(Subscription_.sku.getName())
                .operation(SearchOperation.IN)
                .value(skus)
                .build()));
  }

  private List<SearchCriteria> searchCriteriaForReportDuration(
      OffsetDateTime reportStart, OffsetDateTime reportEnd) {
    return List.of(
        SearchCriteria.builder()
            .key(Subscription_.startDate.getName())
            .operation(SearchOperation.BEFORE_OR_ON)
            .value(reportEnd)
            .build(),
        SearchCriteria.builder()
            .key(Subscription_.endDate.getName())
            .operation(SearchOperation.AFTER_OR_ON)
            .value(reportStart)
            .build());
  }

  private SearchCriteria searchCriteriaMatchingSLA(ServiceLevel serviceLevel) {
    return SearchCriteria.builder()
        .key(Offering_.serviceLevel.getName())
        .operation(SearchOperation.EQUAL)
        .value(serviceLevel)
        .build();
  }

  private SearchCriteria searchCriteriaMatchingUsage(Usage usage) {
    return SearchCriteria.builder()
        .key(Offering_.usage.getName())
        .operation(SearchOperation.EQUAL)
        .value(usage)
        .build();
  }

  private List<SearchCriteria> buildOnDemandSearchCriteria(
      String ownerId,
      Set<String> skus,
      ServiceLevel serviceLevel,
      Usage usage,
      OffsetDateTime reportStart,
      OffsetDateTime reportEnd) {

    List<SearchCriteria> searchCriteria = defaultOnDemandSearchCriteria(ownerId, skus);
    if (Objects.nonNull(serviceLevel) && !serviceLevel.equals(ServiceLevel._ANY))
      searchCriteria.add(searchCriteriaMatchingSLA(serviceLevel));
    if (Objects.nonNull(usage) && !usage.equals(Usage._ANY))
      searchCriteria.add(searchCriteriaMatchingUsage(usage));
    searchCriteria.addAll(searchCriteriaForReportDuration(reportStart, reportEnd));
    return searchCriteria;
  }
}
