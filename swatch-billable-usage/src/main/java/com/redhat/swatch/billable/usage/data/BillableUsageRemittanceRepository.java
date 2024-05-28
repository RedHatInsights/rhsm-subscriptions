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
package com.redhat.swatch.billable.usage.data;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@ApplicationScoped
public class BillableUsageRemittanceRepository
    implements PanacheRepositoryBase<BillableUsageRemittanceEntity, UUID> {

  public List<BillableUsageRemittanceEntity> findByRetryAfterLessThan(OffsetDateTime asOf) {
    return find("retryAfter < ?1", asOf).list();
  }

  public void deleteAllByOrgIdAndRemittancePendingDateBefore(
      String orgId, OffsetDateTime cutoffDate) {
    delete("orgId = ?1 AND remittancePendingDate < ?2", orgId, cutoffDate);
  }

  @Transactional
  public void deleteByOrgId(String orgId) {
    delete("orgId = ?1", orgId);
  }

  @Transactional
  public Stream<BillableUsageRemittanceEntity> findByIdIn(List<String> uuids) {
    return find("uuid in (?1)", uuids).stream();
  }
}
