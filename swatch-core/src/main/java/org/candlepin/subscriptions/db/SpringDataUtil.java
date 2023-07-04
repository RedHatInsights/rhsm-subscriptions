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

import java.util.Objects;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Join;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;

public class SpringDataUtil {
  private SpringDataUtil() {
    // Static methods only
  }

  public static <P, S, T> Join<S, T> fetchJoin(
      Join<P, S> primaryJoin, SingularAttribute<S, T> attribute, String alias) {
    // NB: think P for primary, S for secondary, T for tertiary
    var existing =
        primaryJoin.getJoins().stream()
            .filter(join -> Objects.equals(join.getAlias(), alias))
            .findFirst();
    return existing
        .map(join -> (Join<S, T>) join)
        .orElseGet(
            () -> {
              var join = (Join<S, T>) primaryJoin.fetch(attribute);
              join.alias(alias);
              return join;
            });
  }

  public static <P, S> Join<P, S> fetchJoin(
      From<P, P> root, SingularAttribute<P, S> attribute, String alias) {
    // NB: think P for primary, S for secondary
    var existing =
        root.getJoins().stream().filter(join -> Objects.equals(join.getAlias(), alias)).findFirst();
    return existing
        .map(join -> (Join<P, S>) join)
        .orElseGet(
            () -> {
              var join = (Join<P, S>) root.fetch(attribute);
              join.alias(alias);
              return join;
            });
  }

  public static <P, S> Join<P, S> fetchJoin(
      From<?, P> root, SetAttribute<P, S> attribute, String alias) {
    // NB: think P for primary, S for secondary
    var existing =
        root.getJoins().stream().filter(join -> Objects.equals(join.getAlias(), alias)).findFirst();
    return existing
        .map(join -> (Join<P, S>) join)
        .orElseGet(
            () -> {
              var join = (Join<P, S>) root.fetch(attribute);
              join.alias(alias);
              return join;
            });
  }
}
