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
class HypervisorRelationshipRepositoryTest {

  @Inject HypervisorRelationshipRepository repository;

  @BeforeEach
  @Transactional
  public void setUp() {
    HypervisorRelationshipId id1 = new HypervisorRelationshipId("org1", "subman1");
    HypervisorRelationship relationship1 = new HypervisorRelationship();
    relationship1.setId(id1);
    relationship1.setHypervisorUuid("uuid1");
    relationship1.setCreationDate(OffsetDateTime.now());
    relationship1.setLastUpdated(OffsetDateTime.now());
    relationship1.setFacts("{\"cores\":4,\"sockets\":2}");
    relationship1.setMeasurements("{\"derivedCores\":4,\"derivedSockets\":2}");

    HypervisorRelationshipId id2 = new HypervisorRelationshipId("org1", "subman2");
    HypervisorRelationship relationship2 = new HypervisorRelationship();
    relationship2.setId(id2);
    relationship2.setHypervisorUuid("uuid2");
    relationship2.setCreationDate(OffsetDateTime.now());
    relationship2.setLastUpdated(OffsetDateTime.now());
    relationship2.setFacts("{\"cores\":8,\"sockets\":4}");
    relationship2.setMeasurements("{\"derivedCores\":8,\"derivedSockets\":4}");

    repository.persist(relationship1);
    repository.persist(relationship2);
  }

  @Transactional
  @AfterEach
  public void tearDown() {
    repository.deleteAll();
  }

  @Test
  @Transactional
  void testFindByOrgId_ReturnsCorrectNumberOfResults() {
    List<HypervisorRelationship> results = repository.findByOrgId("org1");
    assertEquals(2, results.size(), "Expected 2 results for org1");
  }

  @Test
  @Transactional
  void testFindByOrgId_FirstResultHasCorrectOrgId() {
    List<HypervisorRelationship> results = repository.findByOrgId("org1");
    assertEquals(
        "org1", results.get(0).getId().getOrgId(), "First result should have orgId 'org1'");
  }

  @Test
  @Transactional
  void testFindByOrgId_SecondResultHasCorrectOrgId() {
    List<HypervisorRelationship> results = repository.findByOrgId("org1");
    assertEquals(
        "org1", results.get(1).getId().getOrgId(), "Second result should have orgId 'org1'");
  }

  @Test
  @Transactional
  void testFindById_ReturnsCorrectResult() {
    HypervisorRelationshipId id = new HypervisorRelationshipId("org123", "subman3");
    HypervisorRelationship relationship = new HypervisorRelationship();
    relationship.setId(id);
    relationship.setHypervisorUuid("uuid3");
    relationship.setCreationDate(OffsetDateTime.now());
    relationship.setLastUpdated(OffsetDateTime.now());
    relationship.setFacts("{\"cores\":16,\"sockets\":8}");
    relationship.setMeasurements("{\"derivedCores\":16,\"derivedSockets\":8}");
    repository.persist(relationship);

    assertTrue(
        repository.findByIdOptional(new HypervisorRelationshipId("org123", "subman3")).isPresent());
  }

  @Test
  @Transactional
  void testFindByHypervisorUUID() {
    assertFalse(repository.findByHypervisorUuid("org1", "uuid1").isEmpty());
    assertTrue(repository.findByHypervisorUuid("org2", "uuid1").isEmpty());
  }
}
