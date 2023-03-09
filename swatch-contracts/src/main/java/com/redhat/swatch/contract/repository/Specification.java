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

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import java.util.List;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

/**
 * Functional interface to allow use of specifications as seen in Spring Data JPA.
 *
 * @param <T>
 */
public interface Specification<T> {
  static <T> Specification<T> not(Specification<T> specification) {
    return (root, query, builder) -> builder.not(specification.toPredicate(root, query, builder));
  }

  default Specification<T> or(Specification<T> other) {
    return (root, query, builder) ->
        builder.or(toPredicate(root, query, builder), other.toPredicate(root, query, builder));
  }

  default Specification<T> and(Specification<T> other) {
    return (root, query, builder) ->
        builder.and(toPredicate(root, query, builder), other.toPredicate(root, query, builder));
  }

  Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder);

  interface PanacheImpl<Entity, Id> extends PanacheRepositoryBase<Entity, Id> {
    default List<Entity> find(Class<Entity> clazz, Specification<Entity> specification, Page page) {
      var entityManager = getEntityManager();
      var criteriaBuilder = entityManager.getCriteriaBuilder();
      var criteriaQuery = criteriaBuilder.createQuery(clazz);
      var root = criteriaQuery.from(clazz);
      criteriaQuery.where(specification.toPredicate(root, criteriaQuery, criteriaBuilder));
      var query = entityManager.createQuery(criteriaQuery);
      if (page != null) {
        query.setMaxResults(page.size);
        query.setFirstResult(page.index * page.size);
      }
      return query.getResultList();
    }
  }
}
