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
import java.util.stream.Stream;

@ApplicationScoped
public class SubscriptionRepository
    implements PanacheSpecificationSupport<
        SubscriptionEntity, SubscriptionEntity.SubscriptionCompoundId> {

  private static final Sort DEFAULT_SORT =
          Sort.by(SubscriptionEntity_.SUBSCRIPTION_ID)
                  .and(SubscriptionEntity_.START_DATE, Sort.Direction.Descending);

  public List<SubscriptionEntity> findByOfferingSku(String sku, int offset, int limit) {
    PanacheQuery<SubscriptionEntity> query = find("offering.sku = ?1", DEFAULT_SORT, sku);
    query.range(offset, offset + limit - 1);
    return query.list();
  }

  public List<SubscriptionEntity> findBySubscriptionNumber(String subscriptionNumber) {
    PanacheQuery<SubscriptionEntity> query = find("subscriptionNumber = ?1", DEFAULT_SORT, subscriptionNumber);
    return query.list();
  }

  public long countByOfferingSku(String sku) {
    return count("offering.sku", sku);
  }

  public Stream<SubscriptionEntity> streamByOrgId(String orgId) {
    PanacheQuery<SubscriptionEntity> query = find("orgId = ?1", DEFAULT_SORT, orgId);
    return query.stream();
  }
}
