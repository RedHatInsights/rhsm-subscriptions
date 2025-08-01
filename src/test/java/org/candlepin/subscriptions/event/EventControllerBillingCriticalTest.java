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

import java.util.ArrayList;
import java.util.List;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.test.ExtendWithSwatchDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Critical billing tests to ensure no usage double-counting occurs in real-world scenarios that
 * could impact customer billing.
 */
@SpringBootTest
@ActiveProfiles({"worker", "test-inventory"})
class EventControllerBillingCriticalTest implements ExtendWithSwatchDatabase {

  @Autowired EventController eventController;
  @Autowired EventRecordRepository eventRecordRepository;

  @AfterEach
  void cleanup() {
    eventRecordRepository.deleteAll();
  }

  @Test
  void testNoDuplicateBillingFromRedeliveredMessages() {
    // Simulates Kafka message redelivery causing duplicate usage events
    // Critical: Must not result in double billing
    List<String> originalBatch = List.of(createEventJsonWithMessageId(100.0, "unique-msg-id-1"));
    eventController.persistServiceInstances(originalBatch);

    // Simulate redelivered message (same content, different Kafka offset)
    List<String> redeliveredBatch = List.of(createEventJsonWithMessageId(100.0, "unique-msg-id-1"));
    eventController.persistServiceInstances(redeliveredBatch);

    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> nonAmendmentEvents = getNonAmendmentEvents(allEvents);
    List<EventRecord> amendmentEvents = getAmendmentEvents(allEvents);

    // Critical: Should only have 1 billable event total, not 2
    assertEquals(1, nonAmendmentEvents.size());
    assertEquals(0, amendmentEvents.size());
    assertEquals(100.0, nonAmendmentEvents.get(0).getEvent().getMeasurements().get(0).getValue());
  }

  @Test
  void testHighFrequencyUpdatesFromSameSource() {
    // Simulates rapid-fire updates from same monitoring source
    // Each update should replace previous, not accumulate
    List<String> rapidUpdates =
        List.of(
            createEventJson(50.0, "2023-05-02T10:00:00Z"),
            createEventJson(75.0, "2023-05-02T10:00:01Z"), // 1 second later
            createEventJson(100.0, "2023-05-02T10:00:02Z"), // 2 seconds later
            createEventJson(80.0, "2023-05-02T10:00:03Z") // 3 seconds later
            );

    eventController.persistServiceInstances(rapidUpdates);

    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> nonAmendmentEvents = getNonAmendmentEvents(allEvents);
    List<EventRecord> amendmentEvents = getAmendmentEvents(allEvents);

    // Should have only final value, with appropriate deductions
    assertEquals(1, nonAmendmentEvents.size());
    assertEquals(3, amendmentEvents.size()); // 3 deductions for overridden values
    assertEquals(80.0, nonAmendmentEvents.get(0).getEvent().getMeasurements().get(0).getValue());
  }

  @Test
  void testCrossSourceConflictResolution() {
    // Different sources reporting usage for same instance/time
    // Last received should win, previous should be deducted
    List<String> sourceAEvents = List.of(createEventJsonWithSource(150.0, "prometheus"));
    eventController.persistServiceInstances(sourceAEvents);

    List<String> sourceBEvents = List.of(createEventJsonWithSource(120.0, "insights"));
    eventController.persistServiceInstances(sourceBEvents);

    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> nonAmendmentEvents = getNonAmendmentEvents(allEvents);
    List<EventRecord> amendmentEvents = getAmendmentEvents(allEvents);

    // Critical: Should have exactly 120.0 billable usage, not 270.0
    assertEquals(2, nonAmendmentEvents.size()); // Original + replacement
    assertEquals(1, amendmentEvents.size()); // Deduction for original

    // Verify total net usage is 120.0
    double totalUsage =
        allEvents.stream().mapToDouble(e -> e.getEvent().getMeasurements().get(0).getValue()).sum();
    assertEquals(120.0, totalUsage, 0.001);
  }

  @Test
  void testBatchProcessingMaintainsConsistency() {
    // Large batch with conflicts - ensure atomicity of conflict resolution
    List<String> largeBatch = new ArrayList<>();

    // Add 10 events for same instance with increasing values
    for (int i = 1; i <= 10; i++) {
      largeBatch.add(createEventJson(i * 10.0, "2023-05-02T10:00:00Z"));
    }

    eventController.persistServiceInstances(largeBatch);

    List<EventRecord> allEvents = eventRecordRepository.findAll();
    List<EventRecord> nonAmendmentEvents = getNonAmendmentEvents(allEvents);
    List<EventRecord> amendmentEvents = getAmendmentEvents(allEvents);

    // Should only have final value (100.0) as billable
    assertEquals(1, nonAmendmentEvents.size());
    assertEquals(9, amendmentEvents.size()); // 9 deductions
    assertEquals(100.0, nonAmendmentEvents.get(0).getEvent().getMeasurements().get(0).getValue());

    // Verify total net usage equals final value
    double totalUsage =
        allEvents.stream().mapToDouble(e -> e.getEvent().getMeasurements().get(0).getValue()).sum();
    assertEquals(100.0, totalUsage, 0.001);
  }

  @Test
  void testZeroUsageEventsPreventPhantomBilling() {
    // Instance shuts down, sends zero usage - should clear previous usage
    List<String> initialUsage = List.of(createEventJson(200.0, "2023-05-02T10:00:00Z"));
    eventController.persistServiceInstances(initialUsage);

    List<String> shutdownEvent = List.of(createEventJson(0.0, "2023-05-02T11:00:00Z"));
    eventController.persistServiceInstances(shutdownEvent);

    List<EventRecord> allEvents = eventRecordRepository.findAll();

    // Should have zero net billable usage
    double totalUsage =
        allEvents.stream().mapToDouble(e -> e.getEvent().getMeasurements().get(0).getValue()).sum();
    assertEquals(0.0, totalUsage, 0.001);
  }

  private List<EventRecord> getNonAmendmentEvents(List<EventRecord> events) {
    return events.stream().filter(e -> e.getEvent().getAmendmentType() == null).toList();
  }

  private List<EventRecord> getAmendmentEvents(List<EventRecord> events) {
    return events.stream().filter(e -> e.getEvent().getAmendmentType() != null).toList();
  }

  private String createEventJson(double value, String timestamp) {
    return String.format(
        """
        {
           "sla": "Premium",
           "org_id": "org123",
           "timestamp": "%s",
           "event_type": "snapshot_rhel_cores",
           "expiration": "2023-05-02T11:00:00Z",
           "instance_id": "test_instance_456",
           "event_source": "prometheus",
           "service_type": "RHEL",
           "measurements": [
             {
               "value": %.1f,
               "metric_id": "cores"
             }
           ],
           "hardware_type": "Physical",
           "product_tag": ["RHEL"],
           "conversion": false
        }""",
        timestamp, value);
  }

  private String createEventJsonWithMessageId(double value, String messageId) {
    return String.format(
        """
        {
           "sla": "Premium",
           "org_id": "org123",
           "timestamp": "2023-05-02T10:00:00Z",
           "event_type": "snapshot_rhel_cores",
           "expiration": "2023-05-02T11:00:00Z",
           "instance_id": "test_instance_456",
           "event_source": "prometheus",
           "service_type": "RHEL",
           "measurements": [
             {
               "value": %.1f,
               "metric_id": "cores"
             }
           ],
           "hardware_type": "Physical",
           "product_tag": ["RHEL"],
           "conversion": false,
           "message_id": "%s"
        }""",
        value, messageId);
  }

  private String createEventJsonWithSource(double value, String eventSource) {
    return String.format(
        """
        {
           "sla": "Premium",
           "org_id": "org123",
           "timestamp": "2023-05-02T10:00:00Z",
           "event_type": "snapshot_rhel_cores",
           "expiration": "2023-05-02T11:00:00Z",
           "instance_id": "test_instance_456",
           "event_source": "%s",
           "service_type": "RHEL",
           "measurements": [
             {
               "value": %.1f,
               "metric_id": "cores"
             }
           ],
           "hardware_type": "Physical",
           "product_tag": ["RHEL"],
           "conversion": false
        }""",
        eventSource, value);
  }
}
