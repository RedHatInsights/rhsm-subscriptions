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
package com.redhat.swatch.hbi.domain;

import com.redhat.swatch.hbi.domain.normalization.facts.NormalizedFacts;
import com.redhat.swatch.hbi.persistence.entity.HbiHost;
import com.redhat.swatch.hbi.persistence.entity.HbiHypervisorGuestRelationship;
import com.redhat.swatch.hbi.persistence.repository.HbiHostRepository;
import com.redhat.swatch.hbi.persistence.repository.HbiHypervisorGuestRelationshipRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.candlepin.clock.ApplicationClock;

@ApplicationScoped
@Transactional
public class HbiHostManager {

  private final ApplicationClock clock;
  private final HbiHostRepository hostRepo;
  private final HbiHypervisorGuestRelationshipRepository linkRepo;

  @Inject
  public HbiHostManager(
      ApplicationClock clock,
      HbiHostRepository hostRepo,
      HbiHypervisorGuestRelationshipRepository linkRepo) {
    this.clock = clock;
    this.hostRepo = hostRepo;
    this.linkRepo = linkRepo;
  }

  public void processHost(NormalizedFacts normalizedFacts, String hbiEventMessageJson) {
    HbiHost savedEntity = upsertHostEntity(normalizedFacts, hbiEventMessageJson);

    var hypervisorUuid = normalizedFacts.getHypervisorUuid();

    if (hypervisorUuid != null) {
      linkGuestToHypervisor(savedEntity, hypervisorUuid);
    }
  }

  public boolean isHypervisor(String orgId, String subscriptionManagerId) {
    return hostRepo
            .findByOrgIdAndSubscriptionManagerId(orgId, subscriptionManagerId)
            .map(linkRepo::countGuestsForHypervisor)
            .orElse(0L)
        > 0;
  }

  public boolean isKnownHost(String orgId, String subscriptionManagerId) {
    return hostRepo.findByOrgIdAndSubscriptionManagerId(orgId, subscriptionManagerId).isPresent();
  }

  public Optional<HbiHost> findHypervisorForGuest(String orgId, String hypervisorSubMgrId) {
    return hostRepo.findByOrgIdAndSubscriptionManagerId(orgId, hypervisorSubMgrId);
  }

  public List<HbiHost> findUnmappedGuests(String orgId, String hypervisorSubMgrId) {
    return hostRepo
        .findByOrgIdAndSubscriptionManagerId(orgId, hypervisorSubMgrId)
        .map(HbiHost::getGuests)
        .orElse(List.of())
        .stream()
        .filter(HbiHost::isUnmappedGuest)
        .toList();
  }

  private HbiHost upsertHostEntity(NormalizedFacts normalizedFacts, String hbiJson) {
    var orgId = normalizedFacts.getOrgId();
    var subscriptionManagerId = normalizedFacts.getSubscriptionManagerId();
    var inventoryId = normalizedFacts.getInventoryId();

    OffsetDateTime now = clock.now();

    HbiHost host =
        hostRepo
            .findByOrgIdAndSubscriptionManagerId(orgId, subscriptionManagerId)
            .orElseGet(
                () ->
                    HbiHost.builder()
                        .orgId(orgId)
                        .subscriptionManagerId(subscriptionManagerId)
                        .inventoryId(inventoryId)
                        .creationDate(now)
                        .build());

    host.setLastUpdated(now);
    host.setHbiMessage(hbiJson);

    hostRepo.persist(host);

    return host;
  }

  private void linkGuestToHypervisor(HbiHost guest, String hypervisorSubMgrId) {
    Optional<HbiHost> maybeHypervisor =
        hostRepo.findByOrgIdAndSubscriptionManagerId(guest.getOrgId(), hypervisorSubMgrId);

    if (maybeHypervisor.isPresent()) {
      HbiHost hypervisor = maybeHypervisor.get();

      Optional<HbiHypervisorGuestRelationship> existingLink = linkRepo.findByGuest(guest);

      if (existingLink.isPresent()) {
        HbiHypervisorGuestRelationship link = existingLink.get();
        if (!link.getHypervisor().equals(hypervisor)) {
          link.setHypervisor(hypervisor);
          linkRepo.persist(link);
        }
      } else {
        HbiHypervisorGuestRelationship newLink =
            HbiHypervisorGuestRelationship.builder()
                .id(UUID.randomUUID())
                .hypervisor(hypervisor)
                .guest(guest)
                .build();
        linkRepo.persist(newLink);
      }
    }
  }
}
