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
package org.candlepin.subscriptions.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.test.ExtendWithSwatchDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.test.context.ActiveProfiles;

/** Integration test that reproduces the intra-batch conflict resolution transaction issue. */
@SpringBootTest
@ActiveProfiles({"worker", "test-inventory"})
class EventControllerIT implements ExtendWithSwatchDatabase {

  @Autowired EventController eventController;

  @Autowired EventRecordRepository eventRecordRepository;

  @AfterEach
  void cleanup() {
    eventRecordRepository.deleteAll();
  }

  /**
   * Test that reproduces the intra-batch conflict resolution transaction issue.
   *
   * <p>This test simulates the scenario where multiple events with the same conflict key arrive in
   * the same Kafka batch, causing inappropriate deduction events due to transaction isolation
   * issues.
   */
  @Test
  void testIntraBatchConflictResolutionTransactionFix() {
    // Create batch of events with same conflict key but different values
    List<String> eventBatch = createIntraBatchConflictEvents();

    // Process the batch - this reproduces the transaction issue scenario
    eventController.persistServiceInstances(eventBatch);

    // Verify the fix: should have correct conflict resolution pattern
    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> nonAmendmentEvents =
        allEvents.stream().filter(e -> e.getEvent().getAmendmentType() == null).toList();
    List<EventRecord> amendmentEvents =
        allEvents.stream().filter(e -> e.getEvent().getAmendmentType() != null).toList();

    // Assert that the intra-batch conflict resolution is working correctly
    // Expected: Only 1 event (the final one) due to intra-batch deduplication
    assertEquals(1, nonAmendmentEvents.size());
    assertEquals(0, amendmentEvents.size());

    // Verify the final event has the latest measurement value
    EventRecord finalEvent = nonAmendmentEvents.get(0);
    assertEquals(3.0, finalEvent.getEvent().getMeasurements().get(0).getValue());
  }

  /**
   * Test that verifies why transactionHandler.runInNewTransaction is necessary.
   *
   * <p>This test demonstrates that when an event in a batch has SQL constraint violations or other
   * database errors, the runInNewTransaction mechanism ensures that: 1. Only the problematic event
   * fails 2. Other valid events in the batch are still processed successfully 3. The system
   * gracefully handles individual event failures without affecting the entire batch
   */
  @Test
  void testTransactionIsolationForFailedEvents() {
    // Processing batch with 2 events: 1 valid, 1 with SQL constraint violation
    List<String> events = createEventsWithInvalidSqlConstraints();

    // Process the mixed batch - this should handle the failed event gracefully
    assertThrows(
        BatchListenerFailedException.class, () -> eventController.persistServiceInstances(events));

    // Verify transaction isolation: valid events should be saved despite the failed one
    List<EventRecord> savedEvents = eventRecordRepository.findAll();

    // The key assertion: Due to transactionHandler.runInNewTransaction,
    // the 1 valid event should be saved even though 1 event failed
    assertEquals(1, savedEvents.size());

    // Verify the correct events were saved (the valid ones)
    List<String> savedInstanceIds =
        savedEvents.stream().map(EventRecord::getInstanceId).sorted().toList();

    assertEquals(List.of("valid_instance_123"), savedInstanceIds);

    // Verify the measurements are correct
    List<Double> savedValues =
        savedEvents.stream()
            .map(e -> e.getEvent().getMeasurements().get(0).getValue())
            .sorted()
            .toList();

    assertEquals(List.of(4.0), savedValues);
  }

  private List<String> createEventsWithInvalidSqlConstraints() {
    List<String> events = new ArrayList<>();

    // Event 1: Valid event that should succeed
    events.add(
        """
        {
           "sla": "Premium",
           "org_id": "org123",
           "timestamp": "2023-05-02T10:00:00Z",
           "event_type": "snapshot_rhel_cores",
           "expiration": "2023-05-02T11:00:00Z",
           "instance_id": "valid_instance_123",
           "display_name": "valid_server",
           "event_source": "prometheus",
           "measurements": [
             {
               "metric_id": "cores",
               "value": 4.0
             }
           ],
           "service_type": "RHEL System",
           "product_tag": ["rosa"]
         }
        """);

    // Event 2: Event with constraint violation (event_type too long - exceeds VARCHAR(60))
    // This will cause a SQL constraint violation
    String longEventType = "a".repeat(100); // 100 characters, exceeds VARCHAR(60) limit
    events.add(
        """
        {
           "sla": "Premium",
           "org_id": "org456",
           "timestamp": "2023-05-02T11:00:00Z",
           "event_type": "%s",
           "expiration": "2023-05-02T12:00:00Z",
           "instance_id": "invalid_instance_456",
           "display_name": "invalid_server",
           "event_source": "prometheus",
           "measurements": [
             {
               "metric_id": "cores",
               "value": 8.0
             }
           ],
           "service_type": "RHEL System",
           "product_tag": ["rosa"]
         }
        """
            .formatted(longEventType));

    return events;
  }

  private List<String> createIntraBatchConflictEvents() {
    List<String> events = new ArrayList<>();

    // Event 1: cores = 1.0
    events.add(
        """
        {
           "sla": "Premium",
           "org_id": "org123",
           "timestamp": "2023-05-02T10:00:00Z",
           "event_type": "snapshot_rhel_cores",
           "expiration": "2023-05-02T11:00:00Z",
           "instance_id": "test_instance_456",
           "display_name": "test_server",
           "event_source": "prometheus",
           "measurements": [
             {
               "metric_id": "cores",
               "value": 1.0
             }
           ],
           "service_type": "RHEL System",
           "product_tag": ["rosa"]
         }
        """);

    // Event 2: cores = 2.0 (same conflict key, different value)
    events.add(
        """
        {
           "sla": "Premium",
           "org_id": "org123",
           "timestamp": "2023-05-02T10:00:00Z",
           "event_type": "snapshot_rhel_cores",
           "expiration": "2023-05-02T11:00:00Z",
           "instance_id": "test_instance_456",
           "display_name": "test_server",
           "event_source": "prometheus",
           "measurements": [
             {
               "metric_id": "cores",
               "value": 2.0
             }
           ],
           "service_type": "RHEL System",
           "product_tag": ["rosa"]
         }
        """);

    // Event 3: cores = 3.0 (same conflict key, different value)
    events.add(
        """
        {
           "sla": "Premium",
           "org_id": "org123",
           "timestamp": "2023-05-02T10:00:00Z",
           "event_type": "snapshot_rhel_cores",
           "expiration": "2023-05-02T11:00:00Z",
           "instance_id": "test_instance_456",
           "display_name": "test_server",
           "event_source": "prometheus",
           "measurements": [
             {
               "metric_id": "cores",
               "value": 3.0
             }
           ],
           "service_type": "RHEL System",
           "product_tag": ["rosa"]
         }
        """);

    return events;
  }
}
