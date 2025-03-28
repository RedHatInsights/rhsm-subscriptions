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
import com.redhat.swatch.hbi.events.repository.HbiHostRelationshipId;
import com.redhat.swatch.hbi.events.repository.HbiHostRelationshipRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.candlepin.clock.ApplicationClock;

@ApplicationScoped
public class HbiHostRelationshipService {

  private ApplicationClock clock;
  private HbiHostRelationshipRepository repository;

  public HbiHostRelationshipService(
      ApplicationClock clock, HbiHostRelationshipRepository repository) {
    this.clock = clock;
    this.repository = repository;
  }

  /** Adds or updates a host relationship. */
  @Transactional
  public void processHost(
      String orgId,
      String subscriptionManagerId,
      String hypervisorUuid,
      boolean isUnmapped,
      String hbiHostFactJson) {
    HbiHostRelationshipId id = new HbiHostRelationshipId(orgId, subscriptionManagerId);
    repository.persist(createOrUpdate(id, hypervisorUuid, isUnmapped, hbiHostFactJson));
  }

  @Transactional
  public List<HbiHostRelationship> getUnmappedGuests(String orgId, String hypervisorUuid) {
    return repository.findUnmappedGuests(orgId, hypervisorUuid);
  }

  @Transactional
  public boolean isHypervisor(String orgId, String subscriptionManagerId) {
    return repository.guestCount(orgId, subscriptionManagerId) > 0;
  }

  @Transactional
  public boolean isKnownHost(String orgId, String subscriptionManagerId) {
    return repository
        .findByIdOptional(new HbiHostRelationshipId(orgId, subscriptionManagerId))
        .isPresent();
  }

  private HbiHostRelationship createOrUpdate(
      HbiHostRelationshipId id, String hypervisorUuid, boolean isUnmapped, String hbiHostFactJson) {
    HbiHostRelationship relationship = findOrDefault(id);
    OffsetDateTime now = clock.now();
    if (Objects.isNull(relationship.getCreationDate())) {
      relationship.setCreationDate(now);
    }
    relationship.setHypervisorUuid(hypervisorUuid);
    relationship.setUnmappedGuest(isUnmapped);
    relationship.setLastUpdated(now);
    relationship.setFacts(hbiHostFactJson);
    return relationship;
  }

  private HbiHostRelationship findOrDefault(HbiHostRelationshipId id) {
    return repository
        .findByIdOptional(id)
        .orElseGet(
            () -> {
              HbiHostRelationship newRelationship = new HbiHostRelationship();
              newRelationship.setId(id);
              return newRelationship;
            });
  }

  @Transactional
  public Optional<HbiHostRelationship> getRelationship(String orgId, String subscriptionManagerId) {
    return repository.findByIdOptional(new HbiHostRelationshipId(orgId, subscriptionManagerId));
  }
}
