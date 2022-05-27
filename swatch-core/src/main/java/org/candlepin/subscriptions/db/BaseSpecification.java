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
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;

public abstract class BaseSpecification {

  protected Predicate mapCriteriaToPredicate(
      Path<?> expression, SearchCriteria criteria, CriteriaBuilder builder) {

    switch (criteria.getOperation()) {
      case GREATER_THAN_EQUAL:
        return builder.greaterThanOrEqualTo(
            expression.get(criteria.getKey()), criteria.getValue().toString());
      case LESS_THAN_EQUAL:
        return builder.lessThanOrEqualTo(
            expression.get(criteria.getKey()), criteria.getValue().toString());
      case AFTER_OR_ON:
        return builder.greaterThanOrEqualTo(
            expression.get(criteria.getKey()), (OffsetDateTime) criteria.getValue());
      case BEFORE_OR_ON:
        return builder.lessThanOrEqualTo(
            expression.get(criteria.getKey()), (OffsetDateTime) criteria.getValue());
      case IN:
        return builder.in(expression.get(criteria.getKey())).value(criteria.getValue());
      case NOT_IN:
        return builder.in(expression.get(criteria.getKey())).value(criteria.getValue()).not();
      case IS_NOT_NULL:
        return builder.isNotNull(expression.get(criteria.getKey()));
      case NOT_EQUAL:
        return builder.notEqual(expression.get(criteria.getKey()), criteria.getValue());
      case EQUAL:
        return builder.equal(expression.get(criteria.getKey()), criteria.getValue());
      default:
        throw new UnsupportedOperationException(criteria.getOperation() + " not yet supported");
    }
  }
}
