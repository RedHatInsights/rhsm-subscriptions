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
package com.redhat.swatch.hbi.events.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.hbi.events.test.resources.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
class HbiHostRelationshipRepositoryTest {

  @Inject HbiHostRelationshipRepository repository;
  private HbiHostRelationship hypervisor;
  private HbiHostRelationship mappedGuest;
  private HbiHostRelationship unmappedGuest;

  @BeforeEach
  @Transactional
  public void setUp() {
    // Match the MICROS of the DB, otherwise it would be rounded.
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    hypervisor = new HbiHostRelationship();
    hypervisor.setOrgId("org1");
    hypervisor.setInventoryId(UUID.randomUUID());
    hypervisor.setSubscriptionManagerId(UUID.randomUUID().toString());
    hypervisor.setCreationDate(now);
    hypervisor.setLastUpdated(now);
    hypervisor.setLatestHbiEventData("{\"cores\": 8, \"sockets\": 4}");

    mappedGuest = new HbiHostRelationship();
    mappedGuest.setOrgId("org1");
    mappedGuest.setInventoryId(UUID.randomUUID());
    mappedGuest.setSubscriptionManagerId(UUID.randomUUID().toString());
    mappedGuest.setHypervisorUuid(hypervisor.getSubscriptionManagerId());
    mappedGuest.setCreationDate(now);
    mappedGuest.setLastUpdated(now);
    mappedGuest.setUnmappedGuest(false);
    mappedGuest.setLatestHbiEventData("{\"cores\": 8, \"sockets\": 4}");

    unmappedGuest = new HbiHostRelationship();
    unmappedGuest.setOrgId("org1");
    unmappedGuest.setInventoryId(UUID.randomUUID());
    unmappedGuest.setSubscriptionManagerId(UUID.randomUUID().toString());
    unmappedGuest.setHypervisorUuid(UUID.randomUUID().toString());
    unmappedGuest.setCreationDate(now);
    unmappedGuest.setLastUpdated(now);
    unmappedGuest.setUnmappedGuest(true);
    unmappedGuest.setLatestHbiEventData("{\"cores\": 4, \"sockets\": 2}");

    HbiHostRelationship org3Host = new HbiHostRelationship();
    org3Host.setOrgId("org3");
    org3Host.setInventoryId(UUID.randomUUID());
    org3Host.setSubscriptionManagerId(UUID.randomUUID().toString());
    org3Host.setCreationDate(now);
    org3Host.setLastUpdated(now);
    org3Host.setUnmappedGuest(false);
    org3Host.setLatestHbiEventData("{}");

    repository.persist(unmappedGuest);
    repository.persist(hypervisor);
    repository.persist(mappedGuest);
    repository.persist(org3Host);
  }

  @Transactional
  @AfterEach
  public void tearDown() {
    repository.deleteAll();
  }

  @Test
  @Transactional
  void testFindByIdReturnsCorrectResult() {
    Optional<HbiHostRelationship> found = repository.findByIdOptional(mappedGuest.getId());
    assertTrue(found.isPresent());
    assertRelationship(mappedGuest, found.get());
  }

  @Test
  @Transactional
  void testFindByOrgIdAndInventoryIdReturnsCorrectResult() {
    Optional<HbiHostRelationship> found =
        repository.findByOrgIdAndInventoryId(
            unmappedGuest.getOrgId(), unmappedGuest.getInventoryId());
    assertTrue(found.isPresent());
    assertRelationship(unmappedGuest, found.get());
  }

  @Test
  @Transactional
  void testGuestCount() {
    assertEquals(0, repository.guestCount("org1", "unknown"));
    assertEquals(0, repository.guestCount("org1", null));
    assertEquals(0, repository.guestCount("org1", ""));
    // NOTE: Even though the guest is unmapped, there it still counts as a guest
    // based on the incoming hypervisorUuid match on the guest.
    assertEquals(1, repository.guestCount("org1", unmappedGuest.getHypervisorUuid()));
    assertEquals(1, repository.guestCount("org1", mappedGuest.getHypervisorUuid()));
  }

  @Test
  @Transactional
  void testFindUnmappedGuests() {
    assertEquals(0, repository.findUnmappedGuests("org1", "").size());
    assertEquals(0, repository.findUnmappedGuests("org1", null).size());
    assertEquals(0, repository.findUnmappedGuests("org1", mappedGuest.getHypervisorUuid()).size());

    List<HbiHostRelationship> unmapped =
        repository.findUnmappedGuests("org1", unmappedGuest.getHypervisorUuid());
    assertEquals(1, unmapped.size());
    assertRelationship(unmappedGuest, unmapped.get(0));
  }

  @Test
  @Transactional
  void testPersistHypervisorWithMultipleGuestRelationship() {
    // Add a couple guests
    repository.persist(createGuestRelationship("org1", hypervisor.getSubscriptionManagerId()));
    repository.persist(createGuestRelationship("org1", hypervisor.getSubscriptionManagerId()));

    assertEquals(3, repository.guestCount("org1", hypervisor.getSubscriptionManagerId()));
  }

  @Test
  @Transactional
  void testFindByOrgIdAndSubscriptionManagerIdReturnsCorrectResult() {
    assertFalse(
        repository
            .findByOrgIdAndSubscriptionManagerId(
                "unknownOrg", unmappedGuest.getSubscriptionManagerId())
            .isPresent());
    assertFalse(
        repository
            .findByOrgIdAndSubscriptionManagerId(
                unmappedGuest.getOrgId(), UUID.randomUUID().toString())
            .isPresent());

    Optional<HbiHostRelationship> existing =
        repository.findByOrgIdAndSubscriptionManagerId(
            unmappedGuest.getOrgId(), unmappedGuest.getSubscriptionManagerId());
    assertTrue(existing.isPresent());
    assertRelationship(unmappedGuest, existing.get());
  }

  @Test
  @Transactional
  void testInventoryIdCanNotExistBetweenTwoOrgs() {
    assertThrows(
        ConstraintViolationException.class,
        () -> {
          HbiHostRelationship rel = createRelationship("org2", hypervisor.getInventoryId());
          repository.persist(rel);
          // Because the test function declares the transaction, the flush call will
          // trigger the exception instead of the persist call.
          repository.flush();
        });
  }

  @Test
  @Transactional
  void testDeleteByInventoryId() {
    UUID inventoryId = UUID.randomUUID();
    repository.persist(createRelationship("org1", inventoryId));
    repository.flush();
    assertEquals(1L, repository.deleteByInventoryId(inventoryId));
  }

  private HbiHostRelationship createRelationship(String orgId, UUID inventoryId) {
    HbiHostRelationship relationship = new HbiHostRelationship();
    relationship.setOrgId(orgId);
    relationship.setInventoryId(inventoryId);
    relationship.setSubscriptionManagerId(UUID.randomUUID().toString());
    relationship.setCreationDate(OffsetDateTime.now());
    relationship.setLastUpdated(OffsetDateTime.now());
    relationship.setUnmappedGuest(false);
    relationship.setLatestHbiEventData("{}");
    return relationship;
  }

  private HbiHostRelationship createGuestRelationship(String orgId, String hypervisorUuid) {
    HbiHostRelationship relationship = new HbiHostRelationship();
    relationship.setOrgId(orgId);
    relationship.setInventoryId(UUID.randomUUID());
    relationship.setSubscriptionManagerId(UUID.randomUUID().toString());
    relationship.setHypervisorUuid(hypervisorUuid);
    relationship.setCreationDate(OffsetDateTime.now());
    relationship.setLastUpdated(OffsetDateTime.now());
    relationship.setUnmappedGuest(false);
    relationship.setLatestHbiEventData("{\"cores\": 4, \"sockets\": 2}");
    return relationship;
  }

  private void assertRelationship(HbiHostRelationship expected, HbiHostRelationship actual) {
    assertEquals(expected.getOrgId(), actual.getOrgId());
    assertEquals(expected.getSubscriptionManagerId(), actual.getSubscriptionManagerId());
    assertEquals(expected.getHypervisorUuid(), actual.getHypervisorUuid());
    assertEquals(expected.getLatestHbiEventData(), actual.getLatestHbiEventData());
    assertEquals(expected.isUnmappedGuest(), actual.isUnmappedGuest());
    assertEquals(expected.getCreationDate(), actual.getCreationDate());
    assertEquals(expected.getLastUpdated(), actual.getLastUpdated());
  }
}
