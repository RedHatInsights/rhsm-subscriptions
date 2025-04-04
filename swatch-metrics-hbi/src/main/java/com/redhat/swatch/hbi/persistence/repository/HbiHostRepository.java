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
package com.redhat.swatch.hbi.persistence.repository;

import com.redhat.swatch.hbi.persistence.entity.HbiHost;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional
public class HbiHostRepository implements PanacheRepository<HbiHost> {

  public Optional<HbiHost> findByOrgIdAndSubscriptionManagerId(String orgId, String subMgrId) {
    return find("orgId = ?1 and subscriptionManagerId = ?2", orgId, subMgrId).firstResultOptional();
  }

  public List<HbiHost> findAllByOrgId(String orgId) {
    return list("orgId", orgId);
  }

  public Optional<HbiHost> findById(UUID id) {
    return find("id", id).firstResultOptional();
  }
}
