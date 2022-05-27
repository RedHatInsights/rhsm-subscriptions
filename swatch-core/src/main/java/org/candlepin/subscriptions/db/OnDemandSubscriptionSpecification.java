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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.Offering_;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Subscription_;
import org.springframework.data.jpa.domain.Specification;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class OnDemandSubscriptionSpecification extends BaseSpecification
    implements Specification<Subscription> {

  private final transient List<SearchCriteria> criteria;

  @Override
  public Predicate toPredicate(
      Root<Subscription> root, CriteriaQuery<?> query, CriteriaBuilder builder) {

    List<Predicate> predicates = new ArrayList<>();

    Root<Offering> offeringRoot = query.from(Offering.class);

    predicates.add(builder.equal(offeringRoot.get(Offering_.sku), root.get(Subscription_.sku)));

    criteria.stream()
        .map(c -> mapSingleSearchCriteriaToPredicate(root, offeringRoot, c, builder))
        .filter(Objects::nonNull)
        .forEach(predicates::add);

    return builder.and(predicates.toArray(Predicate[]::new));
  }

  private Predicate mapSingleSearchCriteriaToPredicate(
      Root<Subscription> root,
      Root<Offering> offeringRoot,
      SearchCriteria criteria,
      CriteriaBuilder builder) {

    Path<?> expression = root;

    if (Offering_.USAGE.equals(criteria.getKey())) {
      return builder.equal(offeringRoot.get(Offering_.USAGE), criteria.getValue());
    } else if (Offering_.SERVICE_LEVEL.equals(criteria.getKey())) {
      return builder.equal(offeringRoot.get(Offering_.SERVICE_LEVEL), criteria.getValue());
    } else if (Offering_.PRODUCT_NAME.equals(criteria.getKey())) {
      return builder.in(offeringRoot.get(Offering_.PRODUCT_NAME)).value(criteria.getValue());
    } else {
      return super.mapCriteriaToPredicate(expression, criteria, builder);
    }
  }
}
