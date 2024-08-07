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
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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
  default List<Entity> find(Class<Entity> clazz, Specification<Entity> specification, Page page) {
    return query(clazz, specification, page).getResultList();
  }

  default TypedQuery<Entity> query(
      Class<Entity> clazz, Specification<Entity> specification, Page page) {
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
    return query;
  }

  default Stream<Entity> stream(Class<Entity> clazz, Specification<Entity> specification) {
    return query(clazz, specification, null).getResultStream();
  }

  default List<Entity> find(Class<Entity> clazz, Specification<Entity> specification) {
    return find(clazz, specification, null);
  }

  default Optional<Entity> findOne(Class<Entity> clazz, Specification<Entity> specification) {
    return query(clazz, specification, Page.of(0, 1)).getResultStream().findFirst();
  }

  default void saveOrUpdate(Entity entity) {
    JpaOperations.INSTANCE.getEntityManager().merge(entity);
  }
}
