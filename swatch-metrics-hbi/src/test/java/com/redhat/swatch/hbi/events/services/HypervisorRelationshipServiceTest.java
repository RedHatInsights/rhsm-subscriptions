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

import static org.mockito.Mockito.*;

import com.redhat.swatch.hbi.events.repository.HypervisorRelationship;
import com.redhat.swatch.hbi.events.repository.HypervisorRelationshipId;
import com.redhat.swatch.hbi.events.repository.HypervisorRelationshipRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class HypervisorRelationshipServiceTest {

  @Mock private HypervisorRelationshipRepository repository;

  @InjectMocks private HypervisorRelationshipService service;

  private AutoCloseable mocks;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  void testProcessGuest() {
    String subscriptionManagerId = "abc";
    String hypervisorUuid = "123";

    when(repository.findByHypervisorUuid(hypervisorUuid)).thenReturn(List.of());
    when(repository.findBySubscriptionManagerId(subscriptionManagerId)).thenReturn(List.of());

    service.processGuest(subscriptionManagerId, hypervisorUuid);

    verify(repository, times(1)).persist(any(HypervisorRelationship.class));
  }

  @Test
  void testMapHypervisor() {
    String hypervisorUuid = "123";

    HypervisorRelationship guest = new HypervisorRelationship();
    guest.setId(new HypervisorRelationshipId("org123", "abc"));

    when(repository.findByHypervisorUuid(null)).thenReturn(List.of(guest));

    service.mapHypervisor(hypervisorUuid);

    verify(repository, times(1)).persist(guest);
  }

  @Test
  void testDeleteStaleHypervisor() {
    String hypervisorUuid = "123";

    HypervisorRelationship hypervisor = new HypervisorRelationship();
    hypervisor.setId(new HypervisorRelationshipId("org123", "abc"));
    hypervisor.setHypervisorUuid(hypervisorUuid);

    when(repository.findByHypervisorUuid(hypervisorUuid)).thenReturn(List.of(hypervisor));

    service.deleteStaleHypervisor(hypervisorUuid);

    verify(repository, times(1)).delete(hypervisor);
  }

  @Test
  void testReAddGuest() {
    String subscriptionManagerId = "abc";
    String hypervisorUuid = "123";
    String rawFacts = "{\"cores\":5,\"sockets\":3}";

    service.reAddGuest(subscriptionManagerId, hypervisorUuid, "org123", rawFacts);

    verify(repository, times(1)).persist(any(HypervisorRelationship.class));
  }

  @ParameterizedTest
  @CsvSource({"abc,123,guestName", ",HBI_HOST", "'',HBI_HOST", "' ',HBI_HOST"})
  void testHypervisorStatus(String subscriptionManagerId, String hypervisorUuid) {}
}
