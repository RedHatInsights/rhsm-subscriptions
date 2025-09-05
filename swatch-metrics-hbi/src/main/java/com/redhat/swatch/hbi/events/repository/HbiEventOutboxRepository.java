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
package com.redhat.swatch.hbi.events.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class HbiEventOutboxRepository implements PanacheRepositoryBase<HbiEventOutbox, UUID> {

  // Native query required to use Postgres' FOR UPDATE SKIP LOCKED functionality.
  private static final String FIND_BY_ORG_ID_WITH_LOCK_QUERY =
      "SELECT * FROM hbi_event_outbox ORDER BY created_on ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED";

  public List<HbiEventOutbox> findByOrgId(String orgId) {
    return list("orgId", orgId);
  }

  public long deleteByOrgId(String orgId) {
    return delete("orgId", orgId);
  }

  /**
   * Find all outbox records in batches. Postgresql's FOR UPDATE SKIP LOCKED is used to ensure that
   * a record cannot be processed at the same time.
   *
   * @param batchSize the max number of records to return.
   * @return the list of {@link HbiEventOutbox} records.
   */
  @SuppressWarnings("unchecked")
  public List<HbiEventOutbox> findAllWithLock(int batchSize) {
    return getEntityManager()
        .createNativeQuery(FIND_BY_ORG_ID_WITH_LOCK_QUERY, HbiEventOutbox.class)
        .setParameter("batchSize", batchSize)
        .getResultList();
  }
}
