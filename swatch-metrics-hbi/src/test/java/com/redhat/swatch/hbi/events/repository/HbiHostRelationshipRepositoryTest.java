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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.hbi.events.test.resources.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
class HbiHostRelationshipRepositoryTest {

  @Inject HbiHostRelationshipRepository repository;

  @BeforeEach
  @Transactional
  public void setUp() {
    HbiHostRelationshipId id1 = new HbiHostRelationshipId("org1", "subman1");
    HbiHostRelationship relationship1 = new HbiHostRelationship();
    relationship1.setId(id1);
    relationship1.setHypervisorUuid("uuid1");
    relationship1.setCreationDate(OffsetDateTime.now());
    relationship1.setLastUpdated(OffsetDateTime.now());
    relationship1.setUnmappedGuest(true);
    relationship1.setFacts("{\"cores\":4,\"sockets\":2}");

    HbiHostRelationshipId id2 = new HbiHostRelationshipId("org1", "subman2");
    HbiHostRelationship relationship2 = new HbiHostRelationship();
    relationship2.setId(id2);
    relationship2.setCreationDate(OffsetDateTime.now());
    relationship2.setLastUpdated(OffsetDateTime.now());
    relationship2.setFacts("{\"cores\":8,\"sockets\":4}");

    HbiHostRelationshipId id3 = new HbiHostRelationshipId("org1", "subman3");
    HbiHostRelationship relationship3 = new HbiHostRelationship();
    relationship3.setId(id3);
    relationship3.setHypervisorUuid("subman2");
    relationship3.setCreationDate(OffsetDateTime.now());
    relationship3.setLastUpdated(OffsetDateTime.now());
    relationship3.setUnmappedGuest(false);
    relationship3.setFacts("{\"cores\":8,\"sockets\":4}");

    HbiHostRelationshipId id4 = new HbiHostRelationshipId("org3", "subman444");
    HbiHostRelationship relationship4 = new HbiHostRelationship();
    relationship4.setId(id4);
    relationship4.setCreationDate(OffsetDateTime.now());
    relationship4.setLastUpdated(OffsetDateTime.now());
    relationship4.setUnmappedGuest(false);
    relationship4.setFacts("{}");

    repository.persist(relationship1);
    repository.persist(relationship2);
    repository.persist(relationship3);
    repository.persist(relationship4);
  }

  @Transactional
  @AfterEach
  public void tearDown() {
    repository.deleteAll();
  }

  @Test
  @Transactional
  void testFindByOrgIdReturnsCorrectNumberOfResults() {
    assertEquals(0, repository.findByOrgId("org2").size(), "Expected 0 results for org2");
    assertEquals(3, repository.findByOrgId("org1").size(), "Expected 3 results for org1");
  }

  @Test
  @Transactional
  void testFindByOrgIdHasCorrectOrgId() {
    List<HbiHostRelationship> results = repository.findByOrgId("org3");
    assertEquals(1, results.size(), "Expected 1 result for org3");
    assertEquals(
        "org3", results.get(0).getId().getOrgId(), "First result should have orgId 'org3'");
  }

  @Test
  @Transactional
  void testFindByIdReturnsCorrectResult() {
    HbiHostRelationshipId id = new HbiHostRelationshipId("org123", "subman3");
    HbiHostRelationship relationship = new HbiHostRelationship();
    relationship.setId(id);
    relationship.setHypervisorUuid("uuid3");
    relationship.setCreationDate(OffsetDateTime.now());
    relationship.setLastUpdated(OffsetDateTime.now());
    relationship.setFacts("{\"cores\":16,\"sockets\":8}");
    repository.persist(relationship);

    assertTrue(
        repository.findByIdOptional(new HbiHostRelationshipId("org123", "subman3")).isPresent());
  }

  @Test
  @Transactional
  void testFindByHypervisorUUID() {
    assertFalse(repository.findByHypervisorUuid("org1", "uuid1").isEmpty());
    assertTrue(repository.findByHypervisorUuid("org2", "uuid1").isEmpty());
  }

  @Test
  @Transactional
  void testGuestCount() {
    assertEquals(0, repository.guestCount("org1", "unknown"));
    assertEquals(0, repository.guestCount("org1", null));
    assertEquals(0, repository.guestCount("org1", ""));
    assertEquals(0, repository.guestCount("org1", "subman1"));
    assertEquals(1, repository.guestCount("org1", "subman2"));
  }

  @Test
  @Transactional
  void testFindUnmappedGuests() {
    assertEquals(0, repository.findUnmappedGuests("org1", "").size());
    assertEquals(0, repository.findUnmappedGuests("org1", null).size());
    assertEquals(0, repository.findUnmappedGuests("org1", "subman2").size());

    List<HbiHostRelationship> unmapped = repository.findUnmappedGuests("org1", "uuid1");
    assertEquals(1, unmapped.size());
    assertEquals("subman1", unmapped.get(0).getId().getSubscriptionManagerId());
  }
}
