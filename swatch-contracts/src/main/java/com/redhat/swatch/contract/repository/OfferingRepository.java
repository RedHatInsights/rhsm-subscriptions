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

import com.redhat.swatch.contract.model.SlaUsageDefaults;
import com.redhat.swatch.panache.PanacheSpecificationSupport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class OfferingRepository implements PanacheSpecificationSupport<OfferingEntity, String> {
  private static final int BACKFILL_BATCH_SIZE = 1_000;

  /** Backfill null values for the supplied SKUs and count fields updated. */
  @Transactional
  public int applySlaUsageDefaults(List<String> skus) {
    int updatedFields = 0;
    Instant lastModified = Instant.now();
    for (int start = 0; start < skus.size(); start += BACKFILL_BATCH_SIZE) {
      int end = Math.min(start + BACKFILL_BATCH_SIZE, skus.size());
      var batch = skus.subList(start, end);
      updatedFields +=
          update(
              """
              set serviceLevel = :sla, lastModified = :lastModified
              where serviceLevel is null and sku in :skus
              """,
              Map.of(
                  "sla", SlaUsageDefaults.SERVICE_LEVEL,
                  "lastModified", lastModified,
                  "skus", batch));
      updatedFields +=
          update(
              """
              set usage = :usage, lastModified = :lastModified
              where usage is null and sku in :skus
              """,
              Map.of(
                  "usage", SlaUsageDefaults.USAGE,
                  "lastModified", lastModified,
                  "skus", batch));
    }
    return updatedFields;
  }

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
}
