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
import org.candlepin.subscriptions.db.model.ReportCriteria;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Subscription_;
import org.candlepin.subscriptions.db.model.Usage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

  Optional<Subscription> findBySubscriptionNumber(String subscriptionNumber);

  Page<Subscription> findBySku(String sku, Pageable pageable);

  Stream<Subscription> findByOwnerId(String ownerId);

  List<Subscription> findByOwnerIdAndEndDateAfter(String ownerId, OffsetDateTime date);

  void deleteBySubscriptionId(String subscriptionId);

  void deleteByAccountNumber(String accountNumber);

  default List<Subscription> findByCriteria(ReportCriteria reportCriteria, Sort sort) {
    List<SearchCriteria> searchCriteria = new ArrayList<>();
    if (Objects.nonNull(reportCriteria.getOrgId())) {
      searchCriteria.add(searchCriteriaMatchingOrgId(reportCriteria.getOrgId()));
    } else {
      searchCriteria.add(searchCriteriaMatchingAccountNumber(reportCriteria.getAccountNumber()));
    }
    if (reportCriteria.isPayg()) {
      // NOTE: we expect payg subscription records to always populate billingProviderId
      searchCriteria.add(searchCriteriaHavingNonNullBillingProviderId());
      searchCriteria.add(searchCriteriaMatchingNonEmptyBillingProviderId());
    }
    // TODO: ENT-5042 should move away from using product name values here //NOSONAR
    searchCriteria.add(searchCriteriaMatchingProductNames(reportCriteria.getProductNames()));
    if (Objects.nonNull(reportCriteria.getServiceLevel())
        && !reportCriteria.getServiceLevel().equals(ServiceLevel._ANY)) {
      searchCriteria.add(searchCriteriaMatchingSLA(reportCriteria.getServiceLevel()));
    }
    if (Objects.nonNull(reportCriteria.getUsage())
        && !reportCriteria.getUsage().equals(Usage._ANY)) {
      searchCriteria.add(searchCriteriaMatchingUsage(reportCriteria.getUsage()));
    }
    if (Objects.nonNull(reportCriteria.getBillingProvider())
        && !reportCriteria.getBillingProvider().equals(BillingProvider._ANY)) {
      searchCriteria.add(
          searchCriteriaMatchingBillingProvider(reportCriteria.getBillingProvider()));
    }
    if (Objects.nonNull(reportCriteria.getBillingAccountId())
        && !reportCriteria.getBillingAccountId().equals("_ANY")) {
      searchCriteria.add(
          searchCriteriaMatchingBillingAccountId(reportCriteria.getBillingAccountId()));
    }
    searchCriteria.addAll(
        searchCriteriaForReportDuration(reportCriteria.getBeginning(), reportCriteria.getEnding()));
    return findAll(
        OnDemandSubscriptionSpecification.builder().criteria(searchCriteria).build(), sort);
  }

  private SearchCriteria searchCriteriaHavingNonNullBillingProviderId() {
    return SearchCriteria.builder()
        .key(Subscription_.BILLING_PROVIDER_ID)
        .operation(SearchOperation.IS_NOT_NULL)
        .build();
  }

  private SearchCriteria searchCriteriaMatchingNonEmptyBillingProviderId() {
    return SearchCriteria.builder()
        .key(Subscription_.BILLING_PROVIDER_ID)
        .operation(SearchOperation.NOT_EQUAL)
        .value("")
        .build();
  }

  private SearchCriteria searchCriteriaMatchingProductNames(Set<String> productNames) {
    return SearchCriteria.builder()
        .key(Offering_.PRODUCT_NAME)
        .operation(SearchOperation.IN)
        .value(productNames)
        .build();
  }

  private SearchCriteria searchCriteriaMatchingAccountNumber(String accountNumber) {
    return SearchCriteria.builder()
        .key(Subscription_.ACCOUNT_NUMBER)
        .operation(SearchOperation.EQUAL)
        .value(accountNumber)
        .build();
  }

  private SearchCriteria searchCriteriaMatchingOrgId(String orgId) {
    return SearchCriteria.builder()
        .key(Subscription_.OWNER_ID)
        .operation(SearchOperation.EQUAL)
        .value(orgId)
        .build();
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

  default SearchCriteria searchCriteriaMatchingBillingProvider(BillingProvider billingProvider) {
    return SearchCriteria.builder()
        .key(Subscription_.BILLING_PROVIDER)
        .operation(SearchOperation.EQUAL)
        .value(billingProvider)
        .build();
  }

  default SearchCriteria searchCriteriaMatchingBillingAccountId(String billingAccountId) {
    return SearchCriteria.builder()
        .key(Subscription_.BILLING_ACCOUNT_ID)
        .operation(SearchOperation.EQUAL)
        .value(billingAccountId)
        .build();
  }
}
