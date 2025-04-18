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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.hbi.events.repository.HbiHostRelationship;
import com.redhat.swatch.hbi.events.repository.HbiHostRelationshipRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HbiHostRelationshipServiceTest {

  @Mock private HbiHostRelationshipRepository repository;
  @Mock private ApplicationClock clock;

  HbiHostRelationshipService service;

  @BeforeEach
  void setUp() {
    service = new HbiHostRelationshipService(clock, repository);
  }

  @Test
  void testProcessHostPersistsRelationship() {
    String orgId = "orgId";
    UUID inventoryId = UUID.randomUUID();
    String subscriptionManagerId = "abc";
    String hypervisorUuid = "123";
    String hbiHostFactJson = "{\"data\":\"Test host fact data\"}";
    when(clock.now()).thenReturn(OffsetDateTime.now());

    service.processHost(
        orgId, inventoryId, subscriptionManagerId, hypervisorUuid, true, hbiHostFactJson);

    ArgumentCaptor<HbiHostRelationship> captor = ArgumentCaptor.forClass(HbiHostRelationship.class);
    verify(repository, times(1)).persist(captor.capture());

    HbiHostRelationship hbiHostRelationship = captor.getValue();
    assertNotNull(hbiHostRelationship);
    assertEquals(orgId, hbiHostRelationship.getOrgId());
    assertEquals(inventoryId, hbiHostRelationship.getInventoryId());
    assertEquals(subscriptionManagerId, hbiHostRelationship.getSubscriptionManagerId());
    assertEquals(hypervisorUuid, hbiHostRelationship.getHypervisorUuid());
    assertEquals(hbiHostFactJson, hbiHostRelationship.getLatestHbiEventData());
    assertTrue(hbiHostRelationship.isUnmappedGuest());
    assertNotNull(hbiHostRelationship.getCreationDate());
    assertNotNull(hbiHostRelationship.getLastUpdated());
    // Should be the same when initially created.
    assertEquals(hbiHostRelationship.getCreationDate(), hbiHostRelationship.getLastUpdated());
  }

  @Test
  void testMapHypervisor_createsNewRelationship() {
    String orgId = "org_1";
    UUID inventoryId = UUID.randomUUID();
    String submanId = "123";
    String factData = "{\"data\":\"Test hypervisor fact data\"}";

    OffsetDateTime expectedCreateUpdateTime = OffsetDateTime.now();
    when(clock.now()).thenReturn(expectedCreateUpdateTime);

    HbiHostRelationship hbiHostRelationship = new HbiHostRelationship();
    hbiHostRelationship.setOrgId(orgId);
    hbiHostRelationship.setInventoryId(inventoryId);
    hbiHostRelationship.setSubscriptionManagerId(submanId);
    hbiHostRelationship.setLatestHbiEventData(factData);
    hbiHostRelationship.setCreationDate(expectedCreateUpdateTime);
    hbiHostRelationship.setLastUpdated(expectedCreateUpdateTime);

    service.processHost(orgId, inventoryId, submanId, null, false, factData);
    verify(repository, times(1))
        .findByOrgIdAndInventoryId(
            hbiHostRelationship.getOrgId(), hbiHostRelationship.getInventoryId());
    verify(repository, times(1)).persist(any(HbiHostRelationship.class));
  }

  @Test
  void testMapHypervisor_updatesExistingRelationship() {
    String orgId = "org_1";
    UUID inventoryId = UUID.randomUUID();
    String submanId = "123";

    OffsetDateTime initialCreateUpdateTime = OffsetDateTime.now();
    HbiHostRelationship existingHbiHostRelationship = new HbiHostRelationship();
    existingHbiHostRelationship.setId(UUID.randomUUID());
    existingHbiHostRelationship.setOrgId(orgId);
    existingHbiHostRelationship.setInventoryId(inventoryId);
    existingHbiHostRelationship.setSubscriptionManagerId(submanId);
    existingHbiHostRelationship.setLatestHbiEventData("{\"data\":\"Initial Fact Data\"}");
    existingHbiHostRelationship.setHypervisorUuid("hypervisor_123");
    existingHbiHostRelationship.setCreationDate(initialCreateUpdateTime);
    existingHbiHostRelationship.setLastUpdated(initialCreateUpdateTime);

    when(repository.findByOrgIdAndInventoryId(
            existingHbiHostRelationship.getOrgId(), existingHbiHostRelationship.getInventoryId()))
        .thenReturn(Optional.of(existingHbiHostRelationship));

    OffsetDateTime expectedUpdateTime = initialCreateUpdateTime.plusMinutes(1);
    String updatedFactData = "{\"data\":\"Test hypervisor fact data\"}";

    when(clock.now()).thenReturn(expectedUpdateTime);

    service.processHost(orgId, inventoryId, submanId, null, false, updatedFactData);

    verify(repository, times(1))
        .findByOrgIdAndInventoryId(
            existingHbiHostRelationship.getOrgId(), existingHbiHostRelationship.getInventoryId());

    ArgumentCaptor<HbiHostRelationship> persistCaptor =
        ArgumentCaptor.forClass(HbiHostRelationship.class);
    verify(repository, times(1)).persist(persistCaptor.capture());

    HbiHostRelationship persistedHbiHostRelationship = persistCaptor.getValue();
    assertEquals(existingHbiHostRelationship.getId(), persistedHbiHostRelationship.getId());
    assertEquals(existingHbiHostRelationship.getOrgId(), persistedHbiHostRelationship.getOrgId());
    assertEquals(
        existingHbiHostRelationship.getInventoryId(),
        persistedHbiHostRelationship.getInventoryId());
    assertEquals(
        existingHbiHostRelationship.getSubscriptionManagerId(),
        persistedHbiHostRelationship.getSubscriptionManagerId());
    assertEquals(
        existingHbiHostRelationship.getHypervisorUuid(),
        persistedHbiHostRelationship.getHypervisorUuid());
    assertEquals(updatedFactData, persistedHbiHostRelationship.getLatestHbiEventData());
    assertEquals(initialCreateUpdateTime, persistedHbiHostRelationship.getCreationDate());
    assertEquals(expectedUpdateTime, persistedHbiHostRelationship.getLastUpdated());
  }

  @Test
  void testIsHypervisor() {
    String orgId = "org123";
    String hypervisorSubmanId = "hypervisorSubmanId1";
    String nonHypervisorSubmanId = "hypervisorSubmanId2";

    when(repository.guestCount(orgId, hypervisorSubmanId)).thenReturn(1L);
    when(repository.guestCount(orgId, nonHypervisorSubmanId)).thenReturn(0L);

    assertTrue(service.isHypervisor(orgId, hypervisorSubmanId));
    assertFalse(service.isHypervisor(orgId, nonHypervisorSubmanId));
  }

  @Test
  void testGetUnmappedGuests() {
    String orgId = "org123";
    String hypervisorUuid = "hypervisorUuid";
    List<HbiHostRelationship> unmapped = List.of(new HbiHostRelationship());
    when(repository.findUnmappedGuests(orgId, hypervisorUuid)).thenReturn(unmapped);
    assertEquals(unmapped, service.getUnmappedGuests(orgId, hypervisorUuid));
  }
}
