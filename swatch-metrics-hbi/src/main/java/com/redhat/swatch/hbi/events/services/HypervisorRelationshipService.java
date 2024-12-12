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

import com.redhat.swatch.hbi.events.repository.HypervisorRelationship;
import com.redhat.swatch.hbi.events.repository.HypervisorRelationshipId;
import com.redhat.swatch.hbi.events.repository.HypervisorRelationshipRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.ZonedDateTime;
import java.util.List;

@ApplicationScoped
public class HypervisorRelationshipService {

  @Inject HypervisorRelationshipRepository repository;

  public void processGuest(String subscriptionManagerId, String hypervisorUuid) {
    boolean isHypervisor = !repository.findByHypervisorUuid(hypervisorUuid).isEmpty();
    boolean isUnmapped = repository.findBySubscriptionManagerId(subscriptionManagerId).isEmpty();

    if (!isHypervisor && isUnmapped) {
      // Derive measurements and persist a new relationship
      String derivedMeasurements = deriveMeasurements(4, 2);
      HypervisorRelationshipId id = new HypervisorRelationshipId("org123", subscriptionManagerId);
      HypervisorRelationship relationship = new HypervisorRelationship();
      relationship.setId(id);
      relationship.setHypervisorUuid(hypervisorUuid);
      relationship.setCreationDate(ZonedDateTime.now());
      relationship.setLastUpdated(ZonedDateTime.now());
      relationship.setFacts("{\"cores\":4,\"sockets\":2}");
      relationship.setMeasurements(derivedMeasurements);

      repository.persist(relationship);
      emitSwatchEvent(subscriptionManagerId, hypervisorUuid, derivedMeasurements);
    }
  }

  /** Translate an unmapped guest to a mapped hypervisor. */
  public void mapHypervisor(String hypervisorUuid) {
    List<HypervisorRelationship> unmappedGuests = repository.findByHypervisorUuid(null);
    for (HypervisorRelationship guest : unmappedGuests) {
      guest.setHypervisorUuid(hypervisorUuid);
      guest.setLastUpdated(ZonedDateTime.now());
      repository.persist(guest);

      String derivedMeasurements = deriveMeasurements(1, 1);
      emitSwatchEvent(
          guest.getId().getSubscriptionManagerId(), hypervisorUuid, derivedMeasurements);
    }
  }

  /** Delete a stale hypervisor. */
  public void deleteStaleHypervisor(String hypervisorUuid) {
    List<HypervisorRelationship> staleHypervisors = repository.findByHypervisorUuid(hypervisorUuid);
    for (HypervisorRelationship hypervisor : staleHypervisors) {
      repository.delete(hypervisor);
      emitDeleteEvent(hypervisorUuid);
    }
  }

  /** Re-add a guest with a known but deleted hypervisor. */
  public void reAddGuest(
      String subscriptionManagerId, String hypervisorUuid, String orgId, String rawFacts) {
    HypervisorRelationshipId id = new HypervisorRelationshipId(orgId, subscriptionManagerId);
    HypervisorRelationship relationship = new HypervisorRelationship();
    relationship.setId(id);
    relationship.setHypervisorUuid(hypervisorUuid);
    relationship.setCreationDate(ZonedDateTime.now());
    relationship.setLastUpdated(ZonedDateTime.now());
    relationship.setFacts(rawFacts);
    relationship.setMeasurements(deriveMeasurements(5, 3));

    repository.persist(relationship);
    emitSwatchEvent(subscriptionManagerId, hypervisorUuid, relationship.getMeasurements());
  }

  // TODO placeholder
  private String deriveMeasurements(int cores, int sockets) {
    return String.format("{\"derivedCores\":%d,\"derivedSockets\":%d}", cores, sockets);
  }

  // TODO placeholder
  private void emitSwatchEvent(
      String subscriptionManagerId, String hypervisorUuid, String measurements) {

    System.out.printf(
        "emit swatch event message: subscriptionManagerId=%s, hypervisorUuid=%s, measurements=%s%n",
        subscriptionManagerId, hypervisorUuid, measurements);
  }

  // TODO placeholder
  private void emitDeleteEvent(String hypervisorUuid) {

    System.out.printf("emit swatch delete event message: hypervisorUuid=%s%n", hypervisorUuid);
  }

  public boolean isHypervisor(String subscriptionManagerId) {
    return false;
  }

  public boolean isUnmappedGuest(String hypervisorUUID) {
    return false;
  }
}
