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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.redhat.swatch.hbi.events.repository.HypervisorRelationship;
import com.redhat.swatch.hbi.events.repository.HypervisorRelationshipId;
import com.redhat.swatch.hbi.events.repository.HypervisorRelationshipRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HypervisorRelationshipServiceTest {

  @Mock private HypervisorRelationshipRepository repository;
  @Mock private ApplicationClock clock;

  HypervisorRelationshipService service;

  @BeforeEach
  void setUp() {
    service = new HypervisorRelationshipService(clock, repository);
  }

  @Test
  void testProcessGuest_PersistsRelationshipForUnmappedGuest() {
    String orgId = "orgId";
    String subscriptionManagerId = "abc";
    String hypervisorUuid = "123";

    String hbiHostFactJson = "{\"data\":\"Test host fact data\"}";
    service.processGuest(orgId, subscriptionManagerId, hypervisorUuid, hbiHostFactJson, true);

    ArgumentCaptor<HypervisorRelationship> captor =
        ArgumentCaptor.forClass(HypervisorRelationship.class);
    verify(repository, times(1)).persist(captor.capture());
    HypervisorRelationship hypervisorRelationship = captor.getValue();
    assertNotNull(hypervisorRelationship);
    assertEquals(orgId, hypervisorRelationship.getId().getOrgId());
    assertEquals(subscriptionManagerId, hypervisorRelationship.getId().getSubscriptionManagerId());
    assertEquals(hypervisorUuid, hypervisorRelationship.getHypervisorUuid());
    assertEquals(hbiHostFactJson, hypervisorRelationship.getFacts());
  }

  @Test
  void testProcessGuest_RemovesRelationshipForMappedGuest() {
    String orgId = "org1";
    String subscriptionManagerId = "abc";
    String hypervisorUuid = "123";

    HypervisorRelationshipId expectedId =
        new HypervisorRelationshipId(orgId, subscriptionManagerId);
    service.processGuest(orgId, subscriptionManagerId, hypervisorUuid, "", false);

    verify(repository, never()).persist(any(HypervisorRelationship.class));
    verify(repository, times(1)).deleteById(expectedId);
  }

  @Test
  void testMapHypervisor_createsNewRelationship() {
    String orgId = "org_1";
    String submanId = "123";
    String factData = "{\"data\":\"Test hypervisor fact data\"}";

    OffsetDateTime expectedCreateUpdateTime = OffsetDateTime.now();
    when(clock.now()).thenReturn(expectedCreateUpdateTime);

    HypervisorRelationship hypervisorRelationship = new HypervisorRelationship();
    hypervisorRelationship.setId(new HypervisorRelationshipId(orgId, submanId));
    hypervisorRelationship.setFacts(factData);
    hypervisorRelationship.setCreationDate(expectedCreateUpdateTime);
    hypervisorRelationship.setLastUpdated(expectedCreateUpdateTime);

    service.mapHypervisor(orgId, submanId, factData);
    verify(repository, times(1)).findByIdOptional(hypervisorRelationship.getId());
    verify(repository, times(1)).persist(hypervisorRelationship);
  }

  @Test
  void testMapHypervisor_updatesExistingRelationship() {
    String orgId = "org_1";
    String submanId = "123";

    HypervisorRelationshipId relationshipId = new HypervisorRelationshipId(orgId, submanId);

    OffsetDateTime initialCreateUpdateTime = OffsetDateTime.now();
    HypervisorRelationship existingHypervisorRelationship = new HypervisorRelationship();
    existingHypervisorRelationship.setId(relationshipId);
    existingHypervisorRelationship.setFacts("{\"data\":\"Initial Fact Data\"}");
    existingHypervisorRelationship.setHypervisorUuid("hypervisor_123");
    existingHypervisorRelationship.setCreationDate(initialCreateUpdateTime);
    existingHypervisorRelationship.setLastUpdated(initialCreateUpdateTime);

    when(repository.findByIdOptional(existingHypervisorRelationship.getId()))
        .thenReturn(Optional.of(existingHypervisorRelationship));

    OffsetDateTime expectedUpdateTime = initialCreateUpdateTime.plusMinutes(1);
    String updatedFactData = "{\"data\":\"Test hypervisor fact data\"}";

    when(clock.now()).thenReturn(expectedUpdateTime);

    service.mapHypervisor(orgId, submanId, updatedFactData);

    verify(repository, times(1)).findByIdOptional(existingHypervisorRelationship.getId());

    ArgumentCaptor<HypervisorRelationship> persistCaptor =
        ArgumentCaptor.forClass(HypervisorRelationship.class);
    verify(repository, times(1)).persist(persistCaptor.capture());

    HypervisorRelationship persistedHypervisorRelationship = persistCaptor.getValue();
    assertEquals(existingHypervisorRelationship.getId(), persistedHypervisorRelationship.getId());
    assertEquals(
        existingHypervisorRelationship.getHypervisorUuid(),
        persistedHypervisorRelationship.getHypervisorUuid());
    assertEquals(updatedFactData, persistedHypervisorRelationship.getFacts());
    assertEquals(initialCreateUpdateTime, persistedHypervisorRelationship.getCreationDate());
    assertEquals(expectedUpdateTime, persistedHypervisorRelationship.getLastUpdated());
  }

  @Test
  void testIsHypervisor() {
    String orgId = "org123";
    String hypervisorSubmanId = "hypervisorSubmanId1";
    String nonHypervisorSubmanId = "hypervisorSubmanId2";

    HypervisorRelationship guestRelationship = new HypervisorRelationship();
    guestRelationship.setId(new HypervisorRelationshipId(orgId, "abc"));

    when(repository.findByHypervisorUuid(orgId, hypervisorSubmanId))
        .thenReturn(List.of(guestRelationship));
    when(repository.findByHypervisorUuid(orgId, nonHypervisorSubmanId)).thenReturn(List.of());

    assertTrue(service.isHypervisor(orgId, hypervisorSubmanId));
    assertFalse(service.isHypervisor(orgId, nonHypervisorSubmanId));
  }

  @Test
  void testIsUnmappedGuest() {
    String orgId = "org123";
    String unmappedGuestHypervisorUuid = "123";
    String mappedGuestHypervisorUuid = "abc";

    // A record must exist for the hypervisor's subman ID.
    when(repository.findByIdOptional(
            new HypervisorRelationshipId(orgId, mappedGuestHypervisorUuid)))
        .thenReturn(Optional.of(new HypervisorRelationship()));
    when(repository.findByIdOptional(
            new HypervisorRelationshipId(orgId, unmappedGuestHypervisorUuid)))
        .thenReturn(Optional.empty());

    assertTrue(service.isUnmappedGuest(orgId, unmappedGuestHypervisorUuid));
    assertFalse(service.isUnmappedGuest(orgId, mappedGuestHypervisorUuid));
  }

  @Test
  void testGetUnmappedGuests() {
    String orgId = "org123";
    String hypervisorUuid = "hypervisorUuid";
    List<HypervisorRelationship> unmapped = List.of(new HypervisorRelationship());
    when(repository.findByHypervisorUuid(orgId, hypervisorUuid)).thenReturn(unmapped);
    assertEquals(unmapped, service.getUnmappedGuests(orgId, hypervisorUuid));
  }
}
