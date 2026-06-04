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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.db.model.config.OrgConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Defines all operations for storing organization config entries. */
public interface OrgConfigRepository extends JpaRepository<OrgConfig, String> {

  @Query("select distinct c.orgId from OrgConfig c")
  Stream<String> findSyncEnabledOrgs();

  default Optional<OrgConfig> createOrUpdateOrgConfig(
      String orgId, OffsetDateTime current, OptInType optInType) {
    Optional<OrgConfig> found = findById(orgId);
    OrgConfig orgConfig = found.orElse(new OrgConfig(orgId));
    if (!found.isPresent()) {
      orgConfig.setOptInType(optInType);
      orgConfig.setCreated(current);
    }
    orgConfig.setUpdated(current);
    return Optional.of(save(orgConfig));
  }

  @Query
  boolean existsByOrgId(String orgId);
}
