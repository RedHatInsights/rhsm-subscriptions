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
import javax.persistence.criteria.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
import org.springframework.data.jpa.domain.Specification;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SubscriptionCapacityViewSpecification
    implements Specification<SubscriptionCapacityView> {

  private final transient List<SearchCriteria> criteria;

  @Override
  public Predicate toPredicate(
      Root<SubscriptionCapacityView> root, CriteriaQuery<?> query, CriteriaBuilder builder) {

    return builder.and(
        criteria.stream()
            .map(c -> mapSingleSearchCriteriaToPredicate(root, c, builder))
            .filter(Objects::nonNull)
            .peek(c -> System.out.println("Predicate returned: " + c.toString()))
            .toArray(Predicate[]::new));
  }

  private Predicate mapSingleSearchCriteriaToPredicate(
      Root<SubscriptionCapacityView> root, SearchCriteria criteria, CriteriaBuilder builder) {

    Path expression = null;
    if ("ownerId".equals(criteria.getKey())) {
      expression = root.get("key").get("ownerId");
    } else if ("subscriptionId".equals(criteria.getKey())) {
      expression = root.get("key").get("subscriptionId");
    } else if ("productId".equals(criteria.getKey())) {
      expression = root.get("key").get("productId");
    } else {
      expression = root.get(criteria.getKey());
    }

    if (criteria.getOperation().equals(SearchOperation.GREATER_THAN_EQUAL)) {
      return builder.greaterThanOrEqualTo(expression, criteria.getValue().toString());
    } else if (criteria.getOperation().equals(SearchOperation.LESS_THAN_EQUAL)) {
      return builder.lessThanOrEqualTo(expression, criteria.getValue().toString());
    } else if (criteria.getOperation().equals(SearchOperation.AFTER_OR_ON)) {
      return builder.greaterThanOrEqualTo(expression, (OffsetDateTime) criteria.getValue());
    } else if (criteria.getOperation().equals(SearchOperation.BEFORE_OR_ON)) {
      return builder.lessThanOrEqualTo(expression, (OffsetDateTime) criteria.getValue());
    } else if (criteria.getOperation().equals(SearchOperation.IN)) {
      return builder.in(expression).value(criteria.getValue());
    } else if (criteria.getOperation().equals(SearchOperation.NOT_IN)) {
      return builder.in(expression).value(criteria.getValue()).not();
    } else {
      return builder.equal(expression, criteria.getValue());
    }
  }
}
