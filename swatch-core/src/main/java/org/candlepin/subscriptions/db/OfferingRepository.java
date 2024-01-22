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

import java.util.Set;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.Offering;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository interface for the Offering entity */
public interface OfferingRepository extends JpaRepository<Offering, String> {
  @EntityGraph(value = "graph.offering")
  @Query(value = "select o from Offering o where o.sku = :sku order by o.sku")
  Offering findOfferingBySku(@Param("sku") String sku);

  @Query(value = "select sku from Offering where :sku member of childSkus")
  Stream<String> findSkusForChildSku(@Param("sku") String sku);

  @Query(value = "select sku from Offering where derivedSku in :derivedSkus")
  Stream<String> findSkusForDerivedSkus(@Param("derivedSkus") Set<String> derivedSkus);

  @Query(value = "select distinct sku from Offering")
  Set<String> findAllDistinctSkus();
}
