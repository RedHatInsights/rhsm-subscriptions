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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.hbi.events.test.helpers.HbiEventOutboxTestHelper;
import com.redhat.swatch.hbi.events.test.resources.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.UserTransaction;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
class HbiEventOutboxRepositoryTest {

  @Inject HbiEventOutboxTestHelper outboxHelper;
  @Inject HbiEventOutboxRepository repository;
  @Inject UserTransaction userTransaction;
  @Inject EntityManager entityManager;

  @BeforeEach
  @Transactional
  void setUp() {
    repository.deleteAll();
  }

  @Test
  @Transactional
  void testFindByIdReturnsCorrectResult() {
    HbiEventOutbox existing = givenExistingHbiEventOutbox();
    Optional<HbiEventOutbox> found = repository.findByIdOptional(existing.getId());
    assertTrue(found.isPresent());
    outboxHelper.assertHbiEventOutboxEquals(existing, found.get());
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
    outboxHelper.assertHbiEventOutboxEquals(existing, retrieved.get());
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

  @Test
  @Transactional
  void testGetWithLock() {
    givenExistingHbiEventOutbox();
    givenExistingHbiEventOutbox();
    givenExistingHbiEventOutbox("org456");

    List<HbiEventOutbox> all = repository.findAllWithLock(10);
    assertEquals(3, all.size());
  }

  @Test
  @Transactional
  void testGetWithLockBatchSize() {
    givenExistingHbiEventOutbox();
    givenExistingHbiEventOutbox();

    List<HbiEventOutbox> all = repository.findAllWithLock(1);
    assertEquals(1, all.size());
  }

  @Test
  void testFindAllWithLockSkipsLockedRows() throws Exception {
    // Persist the record in its own transaction
    userTransaction.begin();
    HbiEventOutbox existing = givenExistingHbiEventOutbox();
    userTransaction.commit();

    CountDownLatch firstTxHasLock = new CountDownLatch(1);
    CountDownLatch releaseFirstTx = new CountDownLatch(1);
    AtomicReference<List<HbiEventOutbox>> secondTxResult = new AtomicReference<>();

    Thread t1 =
        new Thread(
            () -> {
              try {
                userTransaction.begin();
                List<HbiEventOutbox> lockedRows = repository.findAllWithLock(10);
                assertEquals(1, lockedRows.size());
                firstTxHasLock.countDown();
                // Hold the transaction open until the second transaction runs its query
                releaseFirstTx.await(2, TimeUnit.SECONDS);
                userTransaction.commit();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    Thread t2 =
        new Thread(
            () -> {
              try {
                // Wait until the first transaction has acquired the lock
                firstTxHasLock.await(2, TimeUnit.SECONDS);
                userTransaction.begin();
                List<HbiEventOutbox> rows = repository.findAllWithLock(10);
                secondTxResult.set(rows);
                userTransaction.commit();
              } catch (Exception e) {
                throw new RuntimeException(e);
              } finally {
                // Allow first transaction to complete
                releaseFirstTx.countDown();
              }
            });

    t1.start();
    t2.start();
    t1.join(2000);
    t2.join(2000);

    assertNotNull(secondTxResult.get());
    // Because the row is locked by the first transaction and the query uses SKIP LOCKED,
    // the second transaction should not see the locked row.
    assertEquals(0, secondTxResult.get().size());
  }

  @Test
  void testEstimatedOutboxRecords() throws InterruptedException {
    long count = givenManyOutboxRecords();
    // The 'retuples' column needs to be updated
    // for the estimate to be accurate.
    forceVacuum();
    assertEquals(count, repository.estimatedCount());
  }

  @Transactional
  long givenManyOutboxRecords() {
    long count = 1000;
    LongStream.range(0, count)
        .forEach(
            i -> {
              givenExistingHbiEventOutbox();
            });
    return count;
  }

  void forceVacuum() {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (var statement = connection.prepareStatement("VACUUM hbi_event_outbox")) {
                statement.executeUpdate();
              }
            });
  }

  private HbiEventOutbox givenExistingHbiEventOutbox() {
    return givenExistingHbiEventOutbox("org1");
  }

  private HbiEventOutbox givenExistingHbiEventOutbox(String orgId) {
    HbiEventOutbox entity = outboxHelper.createHbiEventOutbox(orgId);
    repository.persistAndFlush(entity);
    return entity;
  }
}
