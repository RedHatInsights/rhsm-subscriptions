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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OfferingRepositoryTest {
  @Inject OfferingRepository repository;

  @Test
  @Transactional
  void testFindSkusForDerivedSkus() {
    var expectedOffering = OfferingEntity.builder().sku("foo").derivedSku("derived").build();
    var extra = OfferingEntity.builder().sku("foo2").build();
    repository.persist(expectedOffering);
    repository.persist(extra);
    var actual = repository.findSkusForDerivedSkus(Set.of("derived")).collect(Collectors.toSet());
    assertEquals(Set.of("foo"), actual);
  }

  @Test
  @Transactional
  void testFindSkusForChildSkus() {
    var expectedOffering = OfferingEntity.builder().sku("foo").childSkus(Set.of("child")).build();
    var extra = OfferingEntity.builder().sku("foo2").build();
    repository.persist(expectedOffering);
    repository.persist(extra);
    var actual = repository.findSkusForChildSku("child").collect(Collectors.toSet());
    assertEquals(Set.of("foo"), actual);
  }

  @Test
  @Transactional
  void testFindAllDistinctSkus() {
    var offering1 = OfferingEntity.builder().sku("foo").build();
    var offering2 = OfferingEntity.builder().sku("foo2").build();
    repository.persist(offering1);
    repository.persist(offering2);
    var actual = repository.findAllDistinctSkus();
    assertTrue(actual.contains(offering1.getSku()));
    assertTrue(actual.contains(offering2.getSku()));
  }
}
