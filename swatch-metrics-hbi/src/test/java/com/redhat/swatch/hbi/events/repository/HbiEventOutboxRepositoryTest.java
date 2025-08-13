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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HbiEventOutboxRepositoryTest {

  @Inject HbiEventOutboxRepository repository;

  @BeforeEach
  @Transactional
  public void setUp() {
    repository.deleteAll();
  }

  @Test
  @Transactional
  void testFindByIdReturnsCorrectResult() {
    HbiEventOutbox existing = givenExistingHbiEventOutbox();
    Optional<HbiEventOutbox> found = repository.findByIdOptional(existing.getId());
    assertTrue(found.isPresent());
    assertHbiEventOutboxEquals(existing, found.get());
  }

  @Test
  @Transactional
  void testFindByOrgIdReturnsCorrectResults() {
    givenExistingHbiEventOutbox("org1");
    List<HbiEventOutbox> foundRecords = repository.findByOrgId("org1");
    assertEquals(1, foundRecords.size());

    givenExistingHbiEventOutbox("org2");
    List<HbiEventOutbox> foundRecordsForOrg2 = repository.findByOrgId("org2");
    assertEquals(1, foundRecordsForOrg2.size());
  }

  @Test
  @Transactional
  void testFindByOrgIdReturnsEmptyListForUnknownOrg() {
    List<HbiEventOutbox> foundRecords = repository.findByOrgId("unknown_org");
    assertTrue(foundRecords.isEmpty());
  }

  @Test
  @Transactional
  void testPersistAndRetrieve() {
    HbiEventOutbox existing = givenExistingHbiEventOutbox();

    Optional<HbiEventOutbox> retrieved = repository.findByIdOptional(existing.getId());
    assertTrue(retrieved.isPresent());
    assertHbiEventOutboxEquals(existing, retrieved.get());
  }

  @Test
  @Transactional
  void testDeleteByOrgId() {
    HbiEventOutbox existing = givenExistingHbiEventOutbox();
    long deletedCount = repository.deleteByOrgId(existing.getOrgId());
    assertEquals(1L, deletedCount);
    repository.getEntityManager().detach(existing);
    assertEquals(0, repository.count());
  }

  @Test
  @Transactional
  void testUpdateRecord() {
    HbiEventOutbox existing = givenExistingHbiEventOutbox();

    existing.getSwatchEventJson().setEventType("updated");
    repository.persistAndFlush(existing);

    Optional<HbiEventOutbox> updated = repository.findByIdOptional(existing.getId());
    assertTrue(updated.isPresent());
    assertEquals("updated", updated.get().getSwatchEventJson().getEventType());
  }

  private HbiEventOutbox givenExistingHbiEventOutbox() {
    return givenExistingHbiEventOutbox("org1");
  }

  private HbiEventOutbox givenExistingHbiEventOutbox(String orgId) {
    HbiEventOutbox entity = new HbiEventOutbox();
    entity.setOrgId(orgId);
    Event event = new Event();
    event.setOrgId(orgId);
    event.setEventSource("HBI_HOST");
    event.setEventType("test");
    event.setServiceType("RHEL System");
    event.setInstanceId(UUID.randomUUID().toString());
    event.setTimestamp(OffsetDateTime.now());
    entity.setSwatchEventJson(event);
    repository.persistAndFlush(entity);
    return entity;
  }

  private void assertHbiEventOutboxEquals(HbiEventOutbox expected, HbiEventOutbox actual) {
    assertEquals(expected.getId(), actual.getId());
    assertEquals(expected.getOrgId(), actual.getOrgId());
    assertEquals(expected.getCreatedOn(), actual.getCreatedOn());
    assertEquals(expected.getSwatchEventJson(), actual.getSwatchEventJson());
  }
}
