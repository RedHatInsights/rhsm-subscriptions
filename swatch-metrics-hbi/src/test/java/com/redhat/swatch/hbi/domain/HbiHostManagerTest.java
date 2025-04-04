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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.redhat.swatch.hbi.domain.normalization.facts.NormalizedFacts;
import com.redhat.swatch.hbi.persistence.entity.HbiHost;
import com.redhat.swatch.hbi.persistence.entity.HbiHypervisorGuestRelationship;
import com.redhat.swatch.hbi.persistence.repository.HbiHostRepository;
import com.redhat.swatch.hbi.persistence.repository.HbiHypervisorGuestRelationshipRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class HbiHostManagerTest {

  public static final String ORG_ID = "org";
  public static final String HYPERVISOR_SUBMAN_ID = "hypervisor-id";
  public static final String GUEST_SUBMAN_ID = "guest-id";
  public static final String HBI_MESSAGE_JSON = "{}";
  public static final String SUB_MGR_ID = "subMgrId";
  private ApplicationClock clock;
  private HbiHostRepository hostRepo;
  private HbiHypervisorGuestRelationshipRepository linkRepo;
  private HbiHostManager manager;

  @BeforeEach
  void setUp() {
    clock = mock(ApplicationClock.class);
    hostRepo = mock(HbiHostRepository.class);
    linkRepo = mock(HbiHypervisorGuestRelationshipRepository.class);
    manager = new HbiHostManager(clock, hostRepo, linkRepo);
  }

  @Test
  void shouldUpsertNewHost() {
    OffsetDateTime now = OffsetDateTime.now();
    when(clock.now()).thenReturn(now);
    when(hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, SUB_MGR_ID))
        .thenReturn(Optional.empty());

    NormalizedFacts normalizedFacts =
        NormalizedFacts.builder().orgId(ORG_ID).subscriptionManagerId(SUB_MGR_ID).build();

    assertDoesNotThrow(() -> manager.processHost(normalizedFacts, HBI_MESSAGE_JSON));
    verify(hostRepo).persist(any(HbiHost.class));
  }

  @Test
  void shouldUpdateExistingHost() {
    OffsetDateTime now = OffsetDateTime.now();
    when(clock.now()).thenReturn(now);

    HbiHost existing = HbiHost.builder().orgId(ORG_ID).subscriptionManagerId(SUB_MGR_ID).build();
    when(hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, SUB_MGR_ID))
        .thenReturn(Optional.of(existing));

    NormalizedFacts facts =
        NormalizedFacts.builder().orgId(ORG_ID).subscriptionManagerId(SUB_MGR_ID).build();

    assertDoesNotThrow(() -> manager.processHost(facts, HBI_MESSAGE_JSON));
    verify(hostRepo).persist(existing);
  }

  @Test
  void shouldLinkGuestToNewHypervisor() {
    OffsetDateTime now = OffsetDateTime.now();
    when(clock.now()).thenReturn(now);

    HbiHost guest =
        spy(HbiHost.builder().orgId(ORG_ID).subscriptionManagerId(GUEST_SUBMAN_ID).build());

    HbiHost hypervisor =
        HbiHost.builder().orgId(ORG_ID).subscriptionManagerId(HYPERVISOR_SUBMAN_ID).build();

    // Guest should appear mapped to trigger linking logic
    doReturn(false).when(guest).isUnmappedGuest();

    when(hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, GUEST_SUBMAN_ID))
        .thenReturn(Optional.of(guest));
    when(hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, HYPERVISOR_SUBMAN_ID))
        .thenReturn(Optional.of(hypervisor));
    when(linkRepo.findByGuest(guest)).thenReturn(Optional.empty());

    NormalizedFacts facts =
        NormalizedFacts.builder()
            .orgId(ORG_ID)
            .subscriptionManagerId(GUEST_SUBMAN_ID)
            .hypervisorUuid(HYPERVISOR_SUBMAN_ID)
            .build();

    assertDoesNotThrow(() -> manager.processHost(facts, HBI_MESSAGE_JSON));

    verify(linkRepo).persist(any(HbiHypervisorGuestRelationship.class));
  }

  @Test
  void shouldNotLinkIfHypervisorNotFound() {
    OffsetDateTime now = OffsetDateTime.now();
    when(clock.now()).thenReturn(now);

    HbiHost guest = HbiHost.builder().orgId(ORG_ID).subscriptionManagerId(GUEST_SUBMAN_ID).build();
    when(hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, GUEST_SUBMAN_ID))
        .thenReturn(Optional.of(guest));
    when(hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, "unknown-hv"))
        .thenReturn(Optional.empty());
    when(linkRepo.findByGuest(guest)).thenReturn(Optional.empty());

    NormalizedFacts facts =
        NormalizedFacts.builder()
            .orgId(ORG_ID)
            .subscriptionManagerId(GUEST_SUBMAN_ID)
            .hypervisorUuid("unknown-hv")
            .build();

    assertDoesNotThrow(() -> manager.processHost(facts, HBI_MESSAGE_JSON));
    verify(linkRepo, never()).persist(any(HbiHypervisorGuestRelationship.class));
  }

  @Test
  void shouldHandleNullSubscriptionManagerId() {
    OffsetDateTime now = OffsetDateTime.now();
    when(clock.now()).thenReturn(now);

    when(hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, null)).thenReturn(Optional.empty());

    NormalizedFacts normalizedFacts =
        NormalizedFacts.builder().orgId(ORG_ID).subscriptionManagerId(null).build();

    assertDoesNotThrow(() -> manager.processHost(normalizedFacts, HBI_MESSAGE_JSON));
    verify(hostRepo).persist(any(HbiHost.class));
  }

  @Test
  void shouldReturnTrueIfHostExists() {
    when(hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, SUB_MGR_ID))
        .thenReturn(
            Optional.of(HbiHost.builder().orgId(ORG_ID).subscriptionManagerId(SUB_MGR_ID).build()));

    boolean result = manager.isKnownHost(ORG_ID, SUB_MGR_ID);

    assertTrue(result);
    verify(hostRepo).findByOrgIdAndSubscriptionManagerId(ORG_ID, SUB_MGR_ID);
  }

  @Test
  void shouldReturnFalseIfHostDoesNotExist() {
    when(hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, SUB_MGR_ID))
        .thenReturn(Optional.empty());

    boolean result = manager.isKnownHost(ORG_ID, SUB_MGR_ID);

    assertFalse(result);
  }

  @Test
  void shouldReturnHypervisorIfItExists() {
    HbiHost hypervisor = HbiHost.builder().orgId(ORG_ID).subscriptionManagerId(SUB_MGR_ID).build();
    when(hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, SUB_MGR_ID))
        .thenReturn(Optional.of(hypervisor));

    Optional<HbiHost> result = manager.findHypervisorForGuest(ORG_ID, SUB_MGR_ID);

    assertTrue(result.isPresent());
    assertEquals(hypervisor, result.get());
    verify(hostRepo).findByOrgIdAndSubscriptionManagerId(ORG_ID, SUB_MGR_ID);
  }

  @Test
  void shouldReturnEmptyIfHypervisorDoesNotExist() {
    when(hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, SUB_MGR_ID))
        .thenReturn(Optional.empty());

    Optional<HbiHost> result = manager.findHypervisorForGuest(ORG_ID, SUB_MGR_ID);

    assertFalse(result.isPresent());
    verify(hostRepo).findByOrgIdAndSubscriptionManagerId(ORG_ID, SUB_MGR_ID);
  }

  @Disabled
  @Test
  void shouldFindUnmappedGuests() {
    HbiHost hypervisor = HbiHost.builder().orgId(ORG_ID).subscriptionManagerId(SUB_MGR_ID).build();
    HbiHost unmappedGuest = HbiHost.builder().orgId(ORG_ID).subscriptionManagerId("guest1").build();
    HbiHost mappedGuest = HbiHost.builder().orgId(ORG_ID).subscriptionManagerId("guest2").build();

    // Set up guests
    hypervisor.setGuestLinks(
        List.of(
            HbiHypervisorGuestRelationship.builder().guest(unmappedGuest).build(),
            HbiHypervisorGuestRelationship.builder().guest(mappedGuest).build()));

    when(hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, SUB_MGR_ID))
        .thenReturn(Optional.of(hypervisor));
    when(unmappedGuest.isUnmappedGuest()).thenReturn(true);
    when(mappedGuest.isUnmappedGuest()).thenReturn(false);

    List<HbiHost> result = manager.findUnmappedGuests(ORG_ID, SUB_MGR_ID);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertTrue(result.contains(unmappedGuest));
    assertFalse(result.contains(mappedGuest));
  }

  @Test
  void shouldReturnEmptyForUnmappedGuestsIfHypervisorNotFound() {
    when(hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, "unknown-hv"))
        .thenReturn(Optional.empty());

    List<HbiHost> result = manager.findUnmappedGuests(ORG_ID, "unknown-hv");

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void shouldReturnEmptyForUnmappedGuestsIfNoGuestsExist() {
    HbiHost hypervisor = HbiHost.builder().orgId(ORG_ID).subscriptionManagerId(SUB_MGR_ID).build();
    hypervisor.setGuestLinks(List.of());

    when(hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, SUB_MGR_ID))
        .thenReturn(Optional.of(hypervisor));

    List<HbiHost> result = manager.findUnmappedGuests(ORG_ID, SUB_MGR_ID);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }
}
