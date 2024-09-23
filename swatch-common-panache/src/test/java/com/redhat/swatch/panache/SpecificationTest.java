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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.panache.common.Sort;
import io.quarkus.panache.common.Sort.Direction;
import io.quarkus.panache.common.Sort.NullPrecedence;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.hibernate.query.sqm.PathElementException;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SpecificationTest {

  @Inject ContactRepository repo;

  List<Contact> findContactWithSort(Sort sort) {
    Specification<Contact> spec =
        (root, query, builder) -> {
          root.join("phoneNumbers").alias("phoneNumbers1");
          return builder.equal(root.get("id"), "1");
        };
    return repo.find(Contact.class, spec, sort);
  }

  @Test
  void testOrderByMissingAttribute() {
    var sortByNotExisting = Sort.ascending("does_not_exist");
    assertThrows(PathElementException.class, () -> findContactWithSort(sortByNotExisting));
  }

  @Test
  void testOrderByRootEntityAttribute() {
    assertTrue(findContactWithSort(Sort.ascending("id")).isEmpty());
  }

  @Test
  void testOrderByRootEntityWithDirectionAndNullPrecedence() {
    assertTrue(
        findContactWithSort(Sort.by("id", Direction.Descending, NullPrecedence.NULLS_FIRST))
            .isEmpty());
  }

  @Test
  void testOrderByEmbeddableAttribute() {
    assertTrue(findContactWithSort(Sort.ascending("ranking")).isEmpty());
  }

  @Test
  void testOrderByJoinAlias() {
    assertTrue(findContactWithSort(Sort.ascending("phoneNumbers1")).isEmpty());
  }

  @Test
  void testOrderByJoinAttribute() {
    assertTrue(findContactWithSort(Sort.ascending("phoneNumbers1.value")).isEmpty());
  }
}
