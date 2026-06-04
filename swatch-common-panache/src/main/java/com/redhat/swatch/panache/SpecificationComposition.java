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
package com.redhat.swatch.panache;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.io.Serializable;

public class SpecificationComposition {
  private SpecificationComposition() {}

  static <T> Specification<T> composed(
      @Nullable Specification<T> lhs, @Nullable Specification<T> rhs, Combiner combiner) {
    return (root, query, builder) -> {
      Predicate thisPredicate = toPredicate(lhs, root, query, builder);
      Predicate otherPredicate = toPredicate(rhs, root, query, builder);
      if (thisPredicate == null) {
        return otherPredicate;
      } else {
        return otherPredicate == null
            ? thisPredicate
            : combiner.combine(builder, thisPredicate, otherPredicate);
      }
    };
  }

  @Nullable
  private static <T> Predicate toPredicate(
      @Nullable Specification<T> specification,
      Root<T> root,
      CriteriaQuery<?> query,
      CriteriaBuilder builder) {
    return specification == null ? null : specification.toPredicate(root, query, builder);
  }

  interface Combiner extends Serializable {
    Predicate combine(CriteriaBuilder builder, @Nullable Predicate lhs, @Nullable Predicate rhs);
  }
}
