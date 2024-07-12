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

import com.redhat.swatch.panache.PanacheSpecificationSupport;
import io.quarkus.hibernate.orm.panache.runtime.JpaOperations;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class OfferingRepository implements PanacheSpecificationSupport<OfferingEntity, String> {
  public Stream<String> findSkusForChildSku(String sku) {
    return find("select sku from OfferingEntity where ?1 member of childSkus", sku)
        .project(String.class)
        .stream();
  }

  public Stream<String> findSkusForDerivedSkus(Set<String> derivedSkus) {
    return find("select sku from OfferingEntity where derivedSku in ?1", derivedSkus)
        .project(String.class)
        .stream();
  }

  public Set<String> findAllDistinctSkus() {
    return find("select distinct sku from OfferingEntity").project(String.class).stream()
        .collect(Collectors.toSet());
  }

  public void saveOrUpdate(OfferingEntity offeringEntity) {
    JpaOperations.INSTANCE.getEntityManager().merge(offeringEntity);
  }
}
