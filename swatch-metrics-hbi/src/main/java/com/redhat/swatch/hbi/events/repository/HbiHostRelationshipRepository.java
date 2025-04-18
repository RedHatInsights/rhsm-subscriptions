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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class HbiHostRelationshipRepository
    implements PanacheRepositoryBase<HbiHostRelationship, UUID> {

  public Optional<HbiHostRelationship> findByOrgIdAndInventoryId(String orgId, UUID inventoryId) {
    return find("orgId=?1 and inventoryId=?2", orgId, inventoryId).firstResultOptional();
  }

  public long guestCount(String orgId, String hypervisorUuid) {
    if (Objects.isNull(hypervisorUuid) || hypervisorUuid.isBlank()) {
      return 0;
    }
    return count("orgId=?1 and hypervisorUuid=?2", orgId, hypervisorUuid);
  }

  public List<HbiHostRelationship> findUnmappedGuests(String orgId, String hypervisorUuid) {
    if (Objects.isNull(hypervisorUuid) || hypervisorUuid.isBlank()) {
      return List.of();
    }
    return list("orgId=?1 and hypervisorUuid=?2 and isUnmappedGuest=true", orgId, hypervisorUuid);
  }

  public Optional<HbiHostRelationship> findByOrgIdAndSubscriptionManagerId(
      String orgId, String subscriptionManagerId) {
    if (Objects.isNull(subscriptionManagerId) || subscriptionManagerId.isBlank()) {
      return Optional.empty();
    }
    return find("orgId=?1 and subscriptionManagerId=?2", orgId, subscriptionManagerId)
        .firstResultOptional();
  }
}
