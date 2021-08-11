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
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.Usage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/** Class implementing custom queries not handled well by Spring JPA's query methods DSL. */
@Repository
public class CustomizedSubscriptionCapacityRepositoryImpl
    implements CustomizedSubscriptionCapacityRepository {

  private EntityManager em;

  @Autowired
  public CustomizedSubscriptionCapacityRepositoryImpl(
      @Qualifier("rhsmSubscriptionsEntityManagerFactory") EntityManager em) {
    this.em = em;
  }

  @Override
  public List<SubscriptionCapacity> findByOwnerAndProductId(
      String ownerId,
      String productId,
      ServiceLevel serviceLevel,
      Usage usage,
      OffsetDateTime reportBegin,
      OffsetDateTime reportEnd) {

    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<SubscriptionCapacity> cq = cb.createQuery(SubscriptionCapacity.class);
    Root<SubscriptionCapacity> capacity = cq.from(SubscriptionCapacity.class);

    List<Predicate> predicates = new ArrayList<>();
    predicates.add(cb.equal(capacity.get("key").get("ownerId"), ownerId));
    predicates.add(cb.equal(capacity.get("key").get("productId"), productId));

    if (serviceLevel != null && serviceLevel != ServiceLevel._ANY) {
      predicates.add(cb.equal(capacity.get("serviceLevel"), serviceLevel));
    }

    if (usage != null && usage != Usage._ANY) {
      predicates.add(cb.equal(capacity.get("usage"), usage));
    }

    /* This bears a little bit of clarification: we are verifying that the subscription's
     * begin date is less than API request's _end_ date and the subscription's end date
     * is greater than request's _start_ date.
     */
    if (reportEnd != null) {
      predicates.add(cb.greaterThan(capacity.get("endDate"), reportBegin));
    }

    if (reportBegin != null) {
      predicates.add(cb.lessThan(capacity.get("beginDate"), reportEnd));
    }

    cq.where(predicates.toArray(new Predicate[0]));

    return em.createQuery(cq).getResultList();
  }
}
