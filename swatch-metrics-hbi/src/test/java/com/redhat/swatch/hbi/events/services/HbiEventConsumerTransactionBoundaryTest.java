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

import static com.redhat.swatch.hbi.events.configuration.Channels.HBI_HOST_EVENTS_IN;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.repository.HbiEventOutbox;
import com.redhat.swatch.hbi.events.repository.HbiEventOutboxRepository;
import com.redhat.swatch.hbi.events.repository.HbiHostRelationship;
import com.redhat.swatch.hbi.events.repository.HbiHostRelationshipRepository;
import com.redhat.swatch.hbi.events.test.helpers.HbiEventTestData;
import com.redhat.swatch.hbi.events.test.helpers.HbiEventTestHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HbiEventConsumerTransactionBoundaryTest {
  @Inject @Any InMemoryConnector connector;
  @InjectSpy HbiHostRelationshipRepository repo;
  @InjectSpy HbiEventOutboxRepository outboxRepository;
  @Inject HbiEventTestHelper hbiEventTestHelper;
  @Inject TransactionSynchronizationRegistry tsr;
  private InMemorySource<HbiEvent> hbiEventsIn;

  private HbiHostCreateUpdateEvent hbiEvent;
  private HbiHostRelationship expectedRelationship;

  @Transactional
  @BeforeEach
  void setup() {
    hbiEventsIn = connector.source(HBI_HOST_EVENTS_IN);
    repo.deleteAll();
    outboxRepository.deleteAll();

    hbiEvent =
        hbiEventTestHelper.getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());
    expectedRelationship = hbiEventTestHelper.relationshipFromHbiEvent(hbiEvent);
  }

  @Test
  void testHbiHostRelationshipNotPersistedIfOutboxRecordFailsToPersist() {
    doThrow(
            new PersistenceException(
                "FORCED: Ensure that the database transaction is rolled back on failure."))
        .when(outboxRepository)
        .persist(any(HbiEventOutbox.class));

    hbiEventsIn.send(hbiEvent);

    // Wait for the event to be processed. The outbox repository persist call must happen.
    Awaitility.await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              verify(repo, times(1)).persist(any(HbiHostRelationship.class));
              verify(outboxRepository, times(1)).persist(any(HbiEventOutbox.class));
            });

    // If the outbox record could not be persisted, the relationship change should have
    // been rolled back.
    assertTrue(
        repo.findByOrgIdAndInventoryId(
                expectedRelationship.getOrgId(), expectedRelationship.getInventoryId())
            .isEmpty());
    assertTrue(outboxRepository.findByOrgId(expectedRelationship.getOrgId()).isEmpty());
  }

  @Test
  void testNothingPersistedIfDatabaseTransactionFails() {
    // After the real persist, schedule the transaction to roll back at commit time.
    doAnswer(
            inv -> {
              inv.callRealMethod();
              tsr.registerInterposedSynchronization(
                  new Synchronization() {
                    @Override
                    public void beforeCompletion() {
                      // Force the enclosing @Transactional method's commit to roll back
                      tsr.setRollbackOnly();
                    }

                    @Override
                    public void afterCompletion(int status) {}
                  });
              return null;
            })
        .when(outboxRepository)
        .persist(any(HbiEventOutbox.class));

    // Send the event and await persist invocation
    hbiEventsIn.send(hbiEvent);
    Awaitility.await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              verify(repo, atLeastOnce()).persist(any(HbiHostRelationship.class));
              verify(outboxRepository, atLeastOnce()).persist(any(HbiEventOutbox.class));
            });

    // Make sure that the relationship is not found (transaction rolled back on commit)
    assertTrue(
        repo.findByOrgIdAndInventoryId(
                expectedRelationship.getOrgId(), expectedRelationship.getInventoryId())
            .isEmpty());
    assertTrue(outboxRepository.findByOrgId(expectedRelationship.getOrgId()).isEmpty());
  }
}
