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
package com.redhat.swatch.contract.repository;

import com.redhat.swatch.panache.PanacheSpecificationSupport;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class SubscriptionRepository
    implements PanacheSpecificationSupport<
        SubscriptionEntity, SubscriptionEntity.SubscriptionCompoundId> {

  private static final Sort DEFAULT_SORT =
      Sort.by(SubscriptionEntity_.SUBSCRIPTION_ID)
          .and(SubscriptionEntity_.START_DATE, Sort.Direction.Descending);

  private static final Sort BILLING_ACCCOUNT_SORT =
      Sort.by(SubscriptionEntity_.BILLING_PROVIDER)
          .and(SubscriptionEntity_.BILLING_ACCOUNT_ID, Sort.Direction.Ascending);

  public List<SubscriptionEntity> findByOfferingSku(String sku, int offset, int limit) {
    PanacheQuery<SubscriptionEntity> query = find("offering.sku = ?1", DEFAULT_SORT, sku);
    query.range(offset, offset + limit - 1);
    return query.list();
  }

  public List<SubscriptionEntity> findBySubscriptionNumber(String subscriptionNumber) {
    PanacheQuery<SubscriptionEntity> query =
        find("subscriptionNumber = ?1", DEFAULT_SORT, subscriptionNumber);
    return query.list();
  }

  public List<SubscriptionEntity> findByCriteria(DbReportCriteria criteria) {
    return find(SubscriptionEntity.class, SubscriptionEntity.buildSearchSpecification(criteria));
  }

  public List<SubscriptionEntity> findByCriteria(DbReportCriteria criteria, Sort sort) {
    return find(
        SubscriptionEntity.class, SubscriptionEntity.buildSearchSpecification(criteria), sort);
  }

  public long countByOfferingSku(String sku) {
    return count("offering.sku", sku);
  }

  public Stream<SubscriptionEntity> streamByOrgId(String orgId) {
    PanacheQuery<SubscriptionEntity> query = find("orgId = ?1", DEFAULT_SORT, orgId);
    return query.stream();
  }

  public List<SubscriptionEntity> findActiveSubscription(String subscriptionId) {
    // Added an order by clause to avoid Hibernate issue HHH-17040
    return find(
            "(endDate IS NULL OR endDate > CURRENT_TIMESTAMP) AND subscriptionId = ?1",
            DEFAULT_SORT,
            subscriptionId)
        .list();
  }

  public List<SubscriptionEntity> findUnlimited(DbReportCriteria dbReportCriteria) {
    var searchCriteria = SubscriptionEntity.buildSearchSpecification(dbReportCriteria);
    searchCriteria = searchCriteria.and(SubscriptionEntity.hasUnlimitedUsage());
    return find(SubscriptionEntity.class, searchCriteria);
  }

  public List<BillingAccountInfoDTO> findBillingAccountInfo(
      String orgId, Optional<String> productTag) {
    StringBuilder query =
        new StringBuilder(
            "select distinct subscription.orgId, subscription.billingAccountId, subscription.billingProvider, productTag from SubscriptionEntity subscription "
                + "join subscription.offering offering "
                + "join offering.productTags productTag "
                + "where orgId = ?1 "
                + "and subscription.startDate <= CURRENT_TIMESTAMP "
                + "and subscription.endDate >= CURRENT_TIMESTAMP");
    Object[] queryParams;
    if (productTag.isPresent()) {
      query.append(" and productTag = ?2");
      queryParams = new Object[] {orgId, productTag.get()};
    } else {
      queryParams = new Object[] {orgId};
    }
    return find(query.toString(), BILLING_ACCCOUNT_SORT, queryParams)
        .project(BillingAccountInfoDTO.class)
        .list();
  }
}
