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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.stream.Stream;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * This implementation is to support the interface to allow fetching data using Java Stream API,
 * this is needed as JPA does not support streamAll() by default <a
 * href="https://stackoverflow.com/questions/41261051/how-to-use-declare-stream-as-return-type-when-dealing-with-jpa-specification-and/54375652#54375652">...</a>
 *
 * @param <T>
 */
@Component
public class StreamSpecificationExecutorImpl<T> implements StreamSpecificationExecutor<T> {
  @PersistenceContext(unitName = "rhsmSubscriptionsEntityManagerFactory")
  private EntityManager entityManager;

  @Override
  public Stream<T> streamAll(Class<T> tClass, Specification<T> spec) {
    var criteriaBuilder = entityManager.getCriteriaBuilder();
    var criteriaQuery = criteriaBuilder.createQuery(tClass);
    var root = criteriaQuery.from(tClass);

    criteriaQuery.where(spec.toPredicate(root, criteriaQuery, criteriaBuilder));
    return entityManager.createQuery(criteriaQuery).getResultStream();
  }
}
