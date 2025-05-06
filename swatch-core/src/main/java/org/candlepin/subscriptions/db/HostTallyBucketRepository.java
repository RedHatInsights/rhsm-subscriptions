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
import static org.hibernate.jpa.AvailableHints.HINT_READ_ONLY;

import jakarta.persistence.QueryHint;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.AccountBucketTally;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.HostBucketKey;
import org.candlepin.subscriptions.db.model.HostBucketKey_;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.HostTallyBucket_;
import org.candlepin.subscriptions.db.model.Host_;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.CrudRepository;
import org.springframework.util.ObjectUtils;

public interface HostTallyBucketRepository
    extends CrudRepository<HostTallyBucket, HostBucketKey>, EntityManagerLookup {

  @Query(
      """
          select
            b.key.productId as productId, b.measurementType as measurementType, b.key.usage as usage,
            b.key.sla as sla, b.key.billingProvider as billingProvider, b.key.billingAccountId as billingAccountId,
            sum(b.cores) as cores, sum(b.sockets) as sockets, count(h.id) as instances
          from Host h, HostTallyBucket b
            where h = b.host and h.orgId=:orgId and h.instanceType=:instanceType
          group by b.key.productId, b.measurementType, b.key.usage, b.key.sla, b.key.billingProvider, b.key.billingAccountId
  """)
  @QueryHints(
      value = {
        @QueryHint(name = HINT_FETCH_SIZE, value = "1024"),
        @QueryHint(name = HINT_READ_ONLY, value = "true")
      })
  Stream<AccountBucketTally> tallyHostBuckets(String orgId, String instanceType);

  record BillingAccountIdRecord(
      String productId, BillingProvider billingProvider, String billingAccountId) {}

  default List<BillingAccountIdRecord> billingAccountIds(DbReportCriteria criteria) {
    CriteriaBuilder criteriaBuilder = getEntityManager().getCriteriaBuilder();
    CriteriaQuery<BillingAccountIdRecord> query =
        criteriaBuilder.createQuery(BillingAccountIdRecord.class);
    var root = query.from(HostTallyBucket.class);
    var key = root.get(HostTallyBucket_.key);
    var hostPath = root.join(HostTallyBucket_.host);

    List<Predicate> predicates = new ArrayList<>();
    // Criteria: b.org_id = ?
    predicates.add(criteriaBuilder.equal(hostPath.get(Host_.ORG_ID), criteria.getOrgId()));
    // And Criteria: b.billing_provider != _ANY
    predicates.add(
        criteriaBuilder.notEqual(key.get(HostBucketKey_.BILLING_PROVIDER), BillingProvider._ANY));
    // And Criteria: b.billing_account_id != _ANY
    predicates.add(
        criteriaBuilder.notEqual(key.get(HostBucketKey_.BILLING_ACCOUNT_ID), ResourceUtils.ANY));
    // if billing provider is set, then: and Criteria: b.billing_provider = ?
    if (Objects.nonNull(criteria.getBillingProvider())
        && !criteria.getBillingProvider().equals(BillingProvider._ANY)
        && !criteria.getBillingProvider().equals(BillingProvider.EMPTY)) {
      predicates.add(
          criteriaBuilder.equal(
              key.get(HostBucketKey_.BILLING_PROVIDER), criteria.getBillingProvider().getValue()));
    }
    if (!ObjectUtils.isEmpty(criteria.getProductTag())) {
      predicates.add(
          criteriaBuilder.equal(key.get(HostBucketKey_.PRODUCT_ID), criteria.getProductTag()));
    }
    List<Order> orderList =
        List.of(
            criteriaBuilder.asc(key.get(HostBucketKey_.BILLING_PROVIDER)),
            criteriaBuilder.asc(key.get(HostBucketKey_.BILLING_ACCOUNT_ID)));

    query =
        query
            .select(
                criteriaBuilder.construct(
                    BillingAccountIdRecord.class,
                    key.get(HostBucketKey_.PRODUCT_ID),
                    key.get(HostBucketKey_.BILLING_PROVIDER),
                    key.get(HostBucketKey_.BILLING_ACCOUNT_ID)))
            .where(predicates.toArray(new Predicate[] {}))
            .orderBy(orderList)
            .distinct(true);

    return getEntityManager().createQuery(query).getResultList();
  }
}
