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

import com.redhat.swatch.hbi.domain.normalization.facts.NormalizedFacts;
import com.redhat.swatch.hbi.infra.PostgresResource;
import com.redhat.swatch.hbi.infra.TestUtil;
import com.redhat.swatch.hbi.persistence.entity.HbiHost;
import com.redhat.swatch.hbi.persistence.repository.HbiHostRepository;
import com.redhat.swatch.hbi.persistence.repository.HbiHypervisorGuestRelationshipRepository;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@Transactional
@QuarkusTestResource(PostgresResource.class)
public class HbiHostManagerIT {

  @Inject EntityManager em;
  @Inject HbiHostManager hostManager;
  @Inject HbiHostRepository hostRepo;
  @Inject HbiHypervisorGuestRelationshipRepository linkRepo;

  private final String ORG_ID = "org1";

  @BeforeEach
  void clearDb() {
    linkRepo.deleteAll();
    hostRepo.deleteAll();
  }

  @Test
  void testHostWithOnlySubmanId() {
    /*
     * Scenario: Host has a subscription_manager_id but no hypervisor_uuid.
     * Meaning: This COULD be a hypervisor. No dependency created yet.
     * Action: Store the host. Wait to see if any guests reference it later.
     */
    NormalizedFacts facts =
        NormalizedFacts.builder().orgId(ORG_ID).subscriptionManagerId("subman-1").build();

    hostManager.processHost(facts, "{}");

    Optional<HbiHost> host = hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, "subman-1");
    assertTrue(host.isPresent());
    assertTrue(host.get().getGuests().isEmpty());
    assertFalse(host.get().getHypervisor().isPresent());
  }

  @Test
  void testGuestWithKnownHypervisor() {
    /*
     * Scenario: Host has a subscription_manager_id but no hypervisor_uuid.
     * Meaning: This COULD be a hypervisor. No dependency created yet.
     * Action: Store the host. Wait to see if any guests reference it later.
     */

    // Create hypervisor first
    NormalizedFacts hypervisorFacts =
        NormalizedFacts.builder().orgId(ORG_ID).subscriptionManagerId("hypervisor").build();
    hostManager.processHost(hypervisorFacts, "{}");

    NormalizedFacts guestFacts =
        NormalizedFacts.builder()
            .orgId(ORG_ID)
            .subscriptionManagerId("guest")
            .hypervisorUuid("hypervisor")
            .build();

    hostManager.processHost(guestFacts, "{}");

    TestUtil.flushAndClear(em);

    Optional<HbiHost> guest = hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, "guest");
    assertTrue(guest.isPresent());
    assertTrue(guest.get().getHypervisor().isPresent());
  }

  @Test
  void testGuestWithUnknownHypervisor() {
    /*
     * Scenario: Host has both subscription_manager_id and hypervisor_uuid. Hypervisor does not exist
     * in DB yet.
     * Meaning: This is an unmapped guest.
     * Action: Store guest, set isUnmappedGuest=true, do NOT create dependency, emit limited measurements.
     */

    NormalizedFacts guestFacts =
        NormalizedFacts.builder()
            .orgId(ORG_ID)
            .subscriptionManagerId("guest-unmapped")
            .hypervisorUuid("unknown-hv")
            .build();

    hostManager.processHost(guestFacts, "{}");

    TestUtil.flushAndClear(em);

    Optional<HbiHost> guest =
        hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, "guest-unmapped");
    assertTrue(guest.isPresent());
    assertTrue(guest.get().isUnmappedGuest());
  }

  @Test
  void testReprocessUnmappedGuestAfterHypervisorArrives() {
    /*
     * Scenario: Guest was stored earlier as unmapped. Then its hypervisor shows up later.
     * Meaning: The guest can now be linked.
     * Action: Update guest's create relationship link.
     */
    NormalizedFacts guestFacts =
        NormalizedFacts.builder()
            .orgId(ORG_ID)
            .subscriptionManagerId("guest")
            .hypervisorUuid("late-hv")
            .build();

    hostManager.processHost(guestFacts, "{}");

    TestUtil.flushAndClear(em);

    Optional<HbiHost> guest = hostRepo.findByOrgIdAndSubscriptionManagerId(ORG_ID, "guest");
    assertTrue(guest.isPresent());
    assertTrue(guest.get().isUnmappedGuest());

    var guestId = guest.get().getId();

    // hypervisor arrives later
    NormalizedFacts hvFacts =
        NormalizedFacts.builder().orgId(ORG_ID).subscriptionManagerId("late-hv").build();

    hostManager.processHost(hvFacts, "{}");

    // Guest reprocessed to establish relationship
    hostManager.processHost(guestFacts, "{}");

    TestUtil.flushAndClear(em);

    guest = hostRepo.findById(guestId);

    assertFalse(guest.get().isUnmappedGuest(), "Guest should now be linked");
    assertTrue(
        guest.get().getHypervisor().isPresent(), "Guest should have a hypervisor relationship");
  }

  @Test
  void testGuestWithNoSubmanId() {
    /*
     * Scenario: Host has hypervisor_uuid, but no subscription_manager_id.
     * Meaning: Unmapped guest. No hypervisor exists yet.
     * Action: Store host without dependency.
     */

    NormalizedFacts guestFacts =
        NormalizedFacts.builder()
            .orgId(ORG_ID)
            .subscriptionManagerId(null)
            .hypervisorUuid("missing-subman")
            .build();

    hostManager.processHost(guestFacts, "{}");

    TestUtil.flushAndClear(em);

    List<HbiHost> hosts = hostRepo.findAllByOrgId(ORG_ID);
    assertEquals(1, hosts.size());
    assertNull(hosts.get(0).getSubscriptionManagerId());
  }

  @Test
  void testHostWithNoSubmanIdOrHypervisor() {
    /*
     * Scenario: Host has neither subscription_manager_id nor hypervisor_uuid.
     * Meaning: Standalone or unmanaged host.
     * Action: Store without relationship.
     */

    NormalizedFacts facts = NormalizedFacts.builder().orgId(ORG_ID).build();

    hostManager.processHost(facts, "{}");

    TestUtil.flushAndClear(em);

    List<HbiHost> hosts = hostRepo.findAllByOrgId(ORG_ID);
    assertEquals(1, hosts.size());
    assertNull(hosts.get(0).getSubscriptionManagerId());
    assertTrue(hosts.get(0).getGuests().isEmpty());
    assertFalse(hosts.get(0).getHypervisor().isPresent());
  }
}
