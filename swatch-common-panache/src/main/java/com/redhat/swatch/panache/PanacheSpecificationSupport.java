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

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.hibernate.orm.panache.runtime.JpaOperations;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.panache.common.Sort.Column;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.hibernate.query.NullPrecedence;
import org.hibernate.query.criteria.JpaOrder;

/**
 * Mixin providing specification support to Panache-based repositories.
 *
 * <p>Use by implementing this interface, and then calling find
 *
 * @param <Entity> The type of entity to operate on
 * @param <Id> The ID type of the entity
 */
@SuppressWarnings("java:S119") // Sonar doesn't like the generic type names
public interface PanacheSpecificationSupport<Entity, Id> extends PanacheRepositoryBase<Entity, Id> {
  default TypedQuery<Entity> query(
      Class<Entity> clazz, Specification<Entity> specification, Sort sort, Page page) {
    var entityManager = getEntityManager();
    var criteriaBuilder = entityManager.getCriteriaBuilder();
    var criteriaQuery = criteriaBuilder.createQuery(clazz);
    var root = criteriaQuery.from(clazz);
    criteriaQuery.where(specification.toPredicate(root, criteriaQuery, criteriaBuilder));
    if (sort != null) {
      criteriaQuery.orderBy(
          sort.getColumns().stream()
              .map(
                  column -> {
                    var path = resolvePath(root, column.getName());
                    return createOrder(criteriaBuilder, path, column);
                  })
              .toList());
    }
    var query = entityManager.createQuery(criteriaQuery);
    if (page != null) {
      query.setMaxResults(page.size);
      query.setFirstResult(page.index * page.size);
    }
    return query;
  }

  private static <Entity> Path<?> resolvePath(Root<Entity> root, String name) {
    var components = name.split("\\.");
    Path<?> path = root;
    var matchingJoin =
        root.getJoins().stream()
            .filter(join -> Objects.equals(join.getAlias(), components[0]))
            .findFirst();
    var index = 0;
    if (matchingJoin.isPresent()) {
      path = matchingJoin.get();
      index = 1;
    }
    for (; index < components.length; index++) {
      path = path.get(components[index]);
    }
    return path;
  }

  private static Order createOrder(CriteriaBuilder criteriaBuilder, Path<?> path, Column column) {
    var order =
        switch (column.getDirection()) {
          case Ascending -> criteriaBuilder.asc(path);
          case Descending -> criteriaBuilder.desc(path);
        };
    if (column.getNullPrecedence() != null) {
      var jpaNullPrecedence =
          switch (column.getNullPrecedence()) {
            case NULLS_FIRST -> NullPrecedence.FIRST;
            case NULLS_LAST -> NullPrecedence.LAST;
          };
      ((JpaOrder) order).nullPrecedence(jpaNullPrecedence);
    }
    return order;
  }

  default Stream<Entity> stream(Class<Entity> clazz, Specification<Entity> specification) {
    return query(clazz, specification, null, null).getResultStream();
  }

  default Stream<Entity> stream(
      Class<Entity> clazz, Specification<Entity> specification, Sort sort) {
    return query(clazz, specification, sort, null).getResultStream();
  }

  default List<Entity> find(Class<Entity> clazz, Specification<Entity> specification, Page page) {
    return query(clazz, specification, null, page).getResultList();
  }

  default List<Entity> find(Class<Entity> clazz, Specification<Entity> specification, Sort sort) {
    return query(clazz, specification, sort, null).getResultList();
  }

  default List<Entity> find(
      Class<Entity> clazz, Specification<Entity> specification, Sort sort, Page page) {
    return query(clazz, specification, sort, page).getResultList();
  }

  default List<Entity> find(Class<Entity> clazz, Specification<Entity> specification) {
    return query(clazz, specification, null, null).getResultList();
  }

  default Optional<Entity> findOne(Class<Entity> clazz, Specification<Entity> specification) {
    return query(clazz, specification, null, Page.of(0, 1)).getResultStream().findFirst();
  }

  default Entity merge(Entity entity) {
    return JpaOperations.INSTANCE.getSession().merge(entity);
  }
}
