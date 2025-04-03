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

import static org.junit.jupiter.api.Assertions.*;

import com.redhat.swatch.hbi.events.TestUtil;
import com.redhat.swatch.hbi.events.repository.HbiHost;
import com.redhat.swatch.hbi.events.repository.HbiHostRepository;
import com.redhat.swatch.hbi.events.repository.HbiHypervisorGuestRelationshipRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HbiHostDbIntegrationTest {

  @Inject EntityManager em;

  private static final String PLACEHOLDER_FACTS =
      "{\"number_of_cpus\": 16, \"cores_per_socket\": 8}";

  @Inject HbiHostRelationshipService service;
  @Inject HbiHostRepository hbiHostRepository;
  @Inject HbiHypervisorGuestRelationshipRepository hbiHostRelationshipRepository;

  private final String orgId = "org_1";

  @BeforeEach
  @Transactional
  void clearDb() {
    hbiHostRelationshipRepository.deleteAll();
    hbiHostRepository.deleteAll();
  }

  @Test
  @DisplayName("Host with only subscription_manager_id is stored without relationships")
  @Transactional
  void testHostWithOnlySubmanId() {
    /*
     * Scenario: Host has a subscription_manager_id but no hypervisor_uuid.
     * Meaning: This COULD be a hypervisor. No dependency created yet.
     * Action: Store the host. Wait to see if any guests reference it later.
     */

    var subscriptionManagerId = UUID.randomUUID().toString();

    service.processHost(orgId, subscriptionManagerId, null, false, PLACEHOLDER_FACTS);
    UUID id = uuidFrom(orgId, subscriptionManagerId);

    Optional<HbiHost> hostOpt = hbiHostRepository.findById(id);
    assertTrue(hostOpt.isPresent(), "Expected host to be persisted");

    HbiHost host = hostOpt.get();

    assertAll(
        () -> assertEquals(id, host.getId(), "UUID should match"),
        () -> assertEquals(orgId, host.getOrgId(), "orgId should match"),
        () -> assertEquals(PLACEHOLDER_FACTS, host.getHbiMessage(), "Facts should match"),
        () -> assertTrue(host.getGuests().isEmpty(), "Should have no guests"),
        () -> assertTrue(host.getHypervisor().isEmpty(), "Should not be linked to a hypervisor"));
  }

  @Test
  @DisplayName("Create a host then create its guest")
  @Transactional
  void testGuestWithKnownHypervisor() {
    /*
     * Scenario: Host has a subscription_manager_id but no hypervisor_uuid.
     * Meaning: This COULD be a hypervisor. No dependency created yet.
     * Action: Store the host. Wait to see if any guests reference it later.
     */

    // Step 1: Create hypervisor
    var hyperSubmanId = UUID.randomUUID().toString();
    service.processHost(orgId, hyperSubmanId, null, false, PLACEHOLDER_FACTS);
    UUID hypervisorId = uuidFrom(orgId, hyperSubmanId);

    ;

    HbiHost hypervisor =
        hbiHostRepository
            .findById(hypervisorId)
            .orElseThrow(() -> new AssertionError("Hypervisor must be persisted"));

    // Step 2: Guest that references existing hypervisor
    var guestSubmanId = UUID.randomUUID().toString();
    service.processHost(orgId, guestSubmanId, hyperSubmanId, false, PLACEHOLDER_FACTS);

    TestUtil.flushAndClear(em);

    UUID guestId = uuidFrom(orgId, guestSubmanId);

    HbiHost guest =
        hbiHostRepository
            .findById(guestId)
            .orElseThrow(() -> new AssertionError("Guest must be persisted"));

    assertAll(
        () -> assertFalse(guest.isUnmappedGuest(), "Guest should be mapped"),
        () -> assertTrue(guest.getHypervisor().isPresent(), "Guest should have hypervisor link"),
        () ->
            assertEquals(
                hypervisor.getId(),
                guest.getHypervisor().get().getId(),
                "Should be linked to correct hypervisor"));
  }

  @Test
  @DisplayName("Guest references unknown hypervisor")
  @Transactional
  void testGuestWithUnknownHypervisor() {
    /*
     * Scenario: Host has both subscription_manager_id and hypervisor_uuid. Hypervisor does not exist
     * in DB yet.
     *
     * Meaning: This is an unmapped guest.
     *
     * Action: Store guest, set isUnmappedGuest=true, do NOT create dependency, emit limited
     * measurements.
     */

    String guestSubmanId = UUID.randomUUID().toString();
    String unknownHypervisorSubmanId = UUID.randomUUID().toString(); // Hypervisor does not exist

    // Process the guest before the hypervisor exists
    service.processHost(orgId, guestSubmanId, unknownHypervisorSubmanId, true, PLACEHOLDER_FACTS);
    UUID guestId = uuidFrom(orgId, guestSubmanId);

    HbiHost guest =
        hbiHostRepository
            .findById(guestId)
            .orElseThrow(() -> new AssertionError("Guest should have been persisted"));

    assertAll(
        () -> assertEquals(orgId, guest.getOrgId(), "Org ID should match"),
        () -> assertEquals(PLACEHOLDER_FACTS, guest.getHbiMessage(), "Facts should match"),
        () -> assertTrue(guest.isUnmappedGuest(), "Guest should be marked as unmapped"),
        () ->
            assertTrue(guest.getGuests().isEmpty(), "Guest should not have any downstream guests"),
        () ->
            assertTrue(guest.getHypervisor().isEmpty(), "No hypervisor relationship should exist"));
  }

  @Test
  @DisplayName("Unmapped guest becomes mapped after hypervisor arrives and guest is reprocessed")
  @Transactional
  void testReprocessUnmappedGuestAfterHypervisorArrives() {
    /*
     * Scenario: Guest was stored earlier as unmapped. Then its hypervisor shows up in a later
     * message.
     *
     * Meaning: The guest can now be linked.
     *
     * Action: Update guest's create relationship link.
     */

    String guestSubmanId = UUID.randomUUID().toString();
    String hypervisorSubmanId = UUID.randomUUID().toString();

    UUID guestId = uuidFrom(orgId, guestSubmanId);
    UUID hypervisorId = uuidFrom(orgId, hypervisorSubmanId);

    // Step 1: Guest arrives referencing a nonexistent hypervisor
    service.processHost(orgId, guestSubmanId, hypervisorSubmanId, true, PLACEHOLDER_FACTS);

    HbiHost guest =
        hbiHostRepository
            .findById(guestId)
            .orElseThrow(() -> new AssertionError("Guest should have been persisted"));
    assertTrue(guest.isUnmappedGuest(), "Guest should initially be unmapped");
    assertTrue(guest.getHypervisor().isEmpty(), "No hypervisor relationship should exist yet");

    // Step 2: Hypervisor arrives later
    service.processHost(orgId, hypervisorSubmanId, null, false, PLACEHOLDER_FACTS);

    TestUtil.flushAndClear(em);

    HbiHost hypervisor =
        hbiHostRepository
            .findById(hypervisorId)
            .orElseThrow(() -> new AssertionError("Hypervisor should have been persisted"));

    // Step 3: Guest is reprocessed after hypervisor exists
    service.processHost(orgId, guestSubmanId, hypervisorSubmanId, false, PLACEHOLDER_FACTS);

    TestUtil.flushAndClear(em);

    guest =
        hbiHostRepository
            .findById(guestId)
            .orElseThrow(() -> new AssertionError("Guest should still exist"));
    hypervisor =
        hbiHostRepository
            .findById(hypervisorId)
            .orElseThrow(() -> new AssertionError("Hypervisor should still exist"));

    assertFalse(guest.isUnmappedGuest(), "Guest should now be mapped");
    assertTrue(guest.getHypervisor().isPresent(), "Guest should now have a hypervisor link");
    assertEquals(
        hypervisor.getId(), guest.getHypervisor().get().getId(), "Mapped to correct hypervisor");
    assertTrue(
        hypervisor.getGuests().stream().anyMatch(g -> g.getId().equals(guestId)),
        "Hypervisor should have guest");
  }

  @Disabled
  @Test
  @DisplayName("Guest with hypervisor_uuid but no subman_id is stored without relationships")
  @Transactional
  void testGuestWithNoSubmanId() {
    /*
     * Scenario: Host has hypervisor_uuid, but no subscription_manager_id. It's also an unmapped
     * guest (i.e. there's no hypervisor on file to map it to)
     *
     * Meaning: It's a guest, want to make sure no issues with null subman ids.
     *
     * Action: Store host, warn/log missing subman ID, do not create dependency.
     */

    String guestSubmanId = null;
    String hypervisorSubmanId = UUID.randomUUID().toString(); // guest references a hypervisor
    UUID guestId = uuidFrom(orgId, hypervisorSubmanId + "-null");

    service.processHost(orgId, guestSubmanId, hypervisorSubmanId, true, PLACEHOLDER_FACTS);

    HbiHost guest =
        hbiHostRepository
            .findById(guestId)
            .orElseThrow(() -> new AssertionError("Guest should have been stored"));

    assertNull(guest.getSubscriptionManagerId(), "Subman ID should remain null");
    assertTrue(guest.getHypervisor().isEmpty(), "Guest should have no linked hypervisor");
  }

  @Disabled
  @Test
  @DisplayName(
      "Standalone host with no subman_id or hypervisor_uuid is stored without relationships")
  @Transactional
  void testHostWithNoSubmanIdOrHypervisor() {
    /*
     * Scenario: Host has neither subscription_manager_id nor hypervisor_uuid.
     *
     * Meaning: Standalone or unmanaged host. It can't be a hypervisor.
     *
     * Action: Store it without a relationship
     */

    String submanId = null;
    String hypervisorUuid = null;

    UUID id = uuidFrom(orgId, "null-null");

    service.processHost(orgId, submanId, hypervisorUuid, false, PLACEHOLDER_FACTS);

    HbiHost host =
        hbiHostRepository
            .findById(id)
            .orElseThrow(() -> new AssertionError("Host should be persisted"));

    assertNull(host.getSubscriptionManagerId(), "No subman_id should be stored");
    assertTrue(host.getGuests().isEmpty(), "Standalone host should have no guests");
    assertTrue(host.getHypervisor().isEmpty(), "Standalone host should not link to a hypervisor");
  }

  private static UUID uuidFrom(String orgId, String submanId) {
    return UUID.nameUUIDFromBytes((orgId + submanId).getBytes());
  }
}
