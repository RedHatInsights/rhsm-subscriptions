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
package com.redhat.swatch.hbi.events.services;

import com.redhat.swatch.hbi.events.repository.HbiHostRelationship;
import com.redhat.swatch.hbi.events.repository.HbiHostRelationshipRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;

@Slf4j
@ApplicationScoped
public class HbiHostRelationshipService {

  private final ApplicationClock clock;
  private final HbiHostRelationshipRepository repository;

  public HbiHostRelationshipService(
      ApplicationClock clock, HbiHostRelationshipRepository repository) {
    this.clock = clock;
    this.repository = repository;
  }

  /** Adds or updates a host relationship. */
  @Transactional
  public void processHost(
      String orgId,
      UUID inventoryId,
      String subscriptionManagerId,
      String hypervisorUuid,
      boolean isUnmapped,
      String hbiHostFactJson) {
    HbiHostRelationship relationship = findOrDefault(orgId, inventoryId);
    repository.persist(
        updateRelationship(
            relationship, subscriptionManagerId, hypervisorUuid, isUnmapped, hbiHostFactJson));
  }

  @Transactional
  public List<HbiHostRelationship> getUnmappedGuests(String orgId, String hypervisorUuid) {
    return repository.findUnmappedGuests(orgId, hypervisorUuid);
  }

  @Transactional
  public boolean isHypervisor(String orgId, String subscriptionManagerId) {
    return repository.guestCount(orgId, subscriptionManagerId) > 0;
  }

  private HbiHostRelationship updateRelationship(
      HbiHostRelationship toUpdate,
      String subscriptionManagerId,
      String hypervisorUuid,
      boolean isUnmapped,
      String hbiEventJson) {
    OffsetDateTime now = clock.now();
    if (Objects.isNull(toUpdate.getCreationDate())) {
      toUpdate.setCreationDate(now);
    }
    toUpdate.setSubscriptionManagerId(subscriptionManagerId);
    toUpdate.setHypervisorUuid(hypervisorUuid);
    toUpdate.setUnmappedGuest(isUnmapped);
    toUpdate.setLastUpdated(now);
    toUpdate.setLatestHbiEventData(hbiEventJson);
    return toUpdate;
  }

  private HbiHostRelationship findOrDefault(String orgId, UUID inventoryId) {
    return repository
        .findByOrgIdAndInventoryId(orgId, inventoryId)
        .orElseGet(
            () -> {
              log.info("Creating new HbiHostRelationship for {}:{}", orgId, inventoryId);
              HbiHostRelationship newRelationship = new HbiHostRelationship();
              newRelationship.setOrgId(orgId);
              newRelationship.setInventoryId(inventoryId);
              return newRelationship;
            });
  }

  @Transactional
  public Optional<HbiHostRelationship> findHypervisor(String orgId, String hypervisorId) {
    return repository.findByOrgIdAndSubscriptionManagerId(orgId, hypervisorId);
  }
}
